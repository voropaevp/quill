package io.getquill

import com.datastax.driver.core._
import io.getquill.context.cassandra.CqlIdiom
import io.getquill.util.{ ContextLogger, LoadConfig }
import cats.effect._
import cats._
import io.getquill.util.GuavaCeUtils._
import cats.implicits._
import com.typesafe.config.Config
import io.getquill.context.ce.CeContext
import fs2.Stream

import scala.jdk.CollectionConverters._
import scala.language.higherKinds

class CassandraCeContext[N <: NamingStrategy, F[_]: FlatMap](
  naming:                     N,
  cluster:                    Cluster,
  keyspace:                   String,
  preparedStatementCacheSize: Long
)(implicit val af: Async[F])
  extends CassandraClusterSessionContext[N](naming, cluster, keyspace, preparedStatementCacheSize)
  with CeContext[CqlIdiom, N, F] {

  private val logger = ContextLogger(classOf[CassandraCeContext[_, F]])

  private def prepareRowAndLog(cql: String, prepare: Prepare = identityPrepare): F[PrepareRow] = for {
    ec <- Async[F].executionContext
    futureStatement = Sync[F].delay(super.prepareAsync(cql)(ec))
    prepStatement <- Async[F].fromFuture(futureStatement)
    (params, bs) = prepare(prepStatement)
    _ <- Sync[F].delay(logger.logQuery(cql, params))
  } yield bs

  protected def page(rs: ResultSet): F[Iterable[Row]] = for {
    available <- af.delay(rs.getAvailableWithoutFetching)
    page_isFullyFetched <- af.delay {
      (rs.asScala.take(available), rs.isFullyFetched)
    }
    (page, isFullyFetched) = page_isFullyFetched
    it <- if (isFullyFetched)
      af.delay {
        logger.underlying.info("paging")
        page
      }
    else {
      rs.fetchMoreResults().toAsync.map(_ => {
        logger.underlying.info("fetching")
        val p = page.toList
        logger.underlying.info(p.toString())
        p
      })
    }
  } yield it

  def streamQuery[T](cql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): Stream[F, T] = {
    Stream
      .eval(prepareRowAndLog(cql, prepare))
      .evalMap(p => session.executeAsync(p).toAsync)
      .flatMap(rs => Stream.repeatEval(page(rs)))
      .takeWhile(_.nonEmpty)
      .flatMap(Stream.iterable)
      .map(extractor)
  }

  def executeQuery[T](cql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): Result[RunQueryResult[T]] = {
    streamQuery[T](cql, prepare, extractor)
      .fold(List[T]())({ case (l, r) => r +: l })
      .map(_.reverse)
      .covary[F]
      .compile
      .toList.map(_.head)
  }

  def executeQuerySingle[T](cql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): Result[RunQuerySingleResult[T]] =
    Functor[F].map(executeQuery(cql, prepare, extractor))(handleSingleResult)

  def executeAction(cql: String, prepare: Prepare = identityPrepare): Result[Unit] = {
    prepareRowAndLog(cql, prepare)
      .flatMap(r => session.executeAsync(r).toAsync)
      .map(_ => ())
  }

  def executeBatchAction(groups: List[BatchGroup]): Result[Unit] =
    Stream.iterable(groups)
      .flatMap {
        case BatchGroup(cql, prepare) =>
          Stream.iterable(prepare)
            .flatMap(prep => Stream.eval(executeAction(cql, prep)))
            .map(_ => ())
      }.compile.drain

}

object CassandraCeContext {

  def apply[N <: NamingStrategy, F[_]: Async: FlatMap](naming: N, config: CassandraContextConfig): CassandraCeContext[N, F] =
    new CassandraCeContext(naming, config.cluster, config.keyspace, config.preparedStatementCacheSize)

  def apply[N <: NamingStrategy, F[_]: Async: FlatMap](naming: N, config: Config): CassandraCeContext[N, F] =
    CassandraCeContext(naming, CassandraContextConfig(config))

  def apply[N <: NamingStrategy, F[_]: Async: FlatMap](naming: N, configPrefix: String): CassandraCeContext[N, F] =
    CassandraCeContext(naming, LoadConfig(configPrefix))

}
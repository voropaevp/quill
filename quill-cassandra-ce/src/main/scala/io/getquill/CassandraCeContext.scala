package io.getquill

import com.datastax.driver.core._
import io.getquill.context.StandardContext
import io.getquill.context.cassandra.{CassandraBaseContext, CqlIdiom}
import io.getquill.util.Messages.fail
import io.getquill.util.ContextLogger
import cats.effect._
import cats.effect.implicits._
import cats._
import io.getquill.util.GuavaCeUtils._
import cats.implicits._
import io.getquill.context.ce.CeContext
import fs2.Stream
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}
import scala.language.higherKinds

class CassandraCeContext[N <: NamingStrategy, F[_]](naming: N,
                                                    cluster: Cluster,
                                                    keyspace: String,
                                                    preparedStatementCacheSize: Long)
                                                   (implicit val af: Async[F])
  extends CassandraClusterSessionContext[N](naming, cluster, keyspace, preparedStatementCacheSize)
    with CeContext[CqlIdiom, N, F] {

  private val logger = ContextLogger(classOf[CassandraCeContext[_, F]])

  override type RunActionResult = Unit

  override type RunQueryResult[T] = List[T]
  override type RunQuerySingleResult[T] = T
  override type RunBatchActionResult = Unit

  override type PrepareRow = BoundStatement
  override type ResultRow = Row

  private def prepareRowAndLog(cql: String, prepare: Prepare = identityPrepare): F[PrepareRow] = for {
    ec <- Async[F].executionContext
    futureStatement = Sync[F].delay(super.prepareAsync(cql)(ec))
    prepStatement <- Async[F].fromFuture(futureStatement)
    (params, bs) = prepare(prepStatement)
    _ <- Sync[F].delay(logger.logQuery(cql , params))
  } yield bs

  protected def page(rs: ResultSet): F[Iterable[Row]] = for {
    available <- af.delay(rs.getAvailableWithoutFetching)
    page_isFullyFetched <- af.delay {
      (rs.asScala.take(available), rs.isFullyFetched)
    }
    (page, isFullyFetched) = page_isFullyFetched
    it <- if (isFullyFetched) {
      af.delay(page)
    } else {
      rs.fetchMoreResults().toAsync
    }
  } yield it

  def streamQuery[T](fetchSize: Option[Int], cql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): StreamResult[T] = {
    Stream
      .eval(prepareRowAndLog(cql, prepare))
      .evalMap(p => session.executeAsync(p).toAsync)
      .flatMap(Observable.fromAsyncStateAction((rs: ResultSet) => page(rs).map((_, rs)))(_))
      .takeWhile(_.nonEmpty)
      .flatMap(Observable.fromIterable)
      .map(extractor)
  }

  def executeQuery[T](cql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): Task[List[T]] = {
    streamQuery[T](None, cql, prepare, extractor)
      .fo

    //  protected def page(rs: ResultSet): CIO[Chunk[Row]] = ZIO.succeed { // TODO Is this right? Was Task.defer in monix
    //    val available = rs.getAvailableWithoutFetching
    //    val builder = ChunkBuilder.make[Row]()
    //    builder.sizeHint(available)
    //    while (rs.getAvailableWithoutFetching() > 0) {
    //      builder += rs.one()
    //    }
    //    builder.result()
    //  }
    //
    //  private[getquill] def execute(cql: String, prepare: Prepare, csession: CassandraZioSession, fetchSize: Option[Int]) =
    //    blocking {
    //      prepareRowAndLog(cql, prepare)
    //        .mapEffect { p =>
    //          // Set the fetch size of the result set if it exists
    //          fetchSize match {
    //            case Some(value) => p.setFetchSize(value)
    //            case None =>
    //          }
    //          p
    //        }
    //        .flatMap(p => {
    //          csession.session.executeAsync(p).asZio
    //        })
    //    }
    //
    //  val streamBlocker: ZStream[Blocking, Nothing, Any] =
    //    ZStream.managed(zio.blocking.blockingExecutor.toManaged_.flatMap { executor =>
    //      ZManaged.lock(executor)
    //    })
    //
    //  def streamQuery[T](fetchSize: Option[Int], cql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor) = {
    //    val stream =
    //      for {
    //        env <- ZStream.environment[Has[CassandraZioSession]]
    //        csession = env.get[CassandraZioSession]
    //        rs <- ZStream.fromEffect(execute(cql, prepare, csession, fetchSize))
    //        row <- ZStream.unfoldChunkM(rs) { rs =>
    //          // keep taking pages while chunk sizes are non-zero
    //          val nextPage = page(rs)
    //          nextPage.flatMap { chunk =>
    //            if (chunk.length > 0) {
    //              rs.fetchMoreResults().asZio.map(rs => Some((chunk, rs)))
    //            } else
    //              ZIO.succeed(None)
    //          }
    //        }
    //      } yield extractor(row)
    //
    //    // Run the entire chunking flow on the blocking executor
    //    streamBlocker *> stream
    //  }
    //
    //  def executeQuery[T](cql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): CIO[List[T]] = blocking {
    //    for {
    //      env <- ZIO.environment[Has[CassandraZioSession] with Blocking]
    //      csession = env.get[CassandraZioSession]
    //      rs <- execute(cql, prepare, csession, None)
    //      rows <- ZIO.effect(rs.all())
    //    } yield (rows.asScala.map(extractor).toList)
    //  }
    //
    //  def executeQuerySingle[T](cql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): CIO[T] = blocking {
    //    executeQuery(cql, prepare, extractor).map(handleSingleResult(_))
    //    for {
    //      env <- ZIO.environment[Has[CassandraZioSession] with Blocking]
    //      csession = env.get[CassandraZioSession]
    //      rs <- execute(cql, prepare, csession, None)
    //      rows <- ZIO.effect(rs.all())
    //      singleRow <- ZIO.effect(handleSingleResult(rows.asScala.map(extractor).toList))
    //    } yield singleRow
    //  }
    //
    //  def executeAction[T](cql: String, prepare: Prepare = identityPrepare): CIO[Unit] = blocking {
    //    for {
    //      env <- ZIO.environment[BlockingSession]
    //      r <- prepareRowAndLog(cql, prepare).provide(env)
    //      csession = env.get[CassandraZioSession]
    //      result <- csession.session.executeAsync(r).asZio
    //    } yield ()
    //  }
    //
    //  def executeBatchAction(groups: List[BatchGroup]): CIO[Unit] = blocking {
    //    for {
    //      env <- ZIO.environment[Has[CassandraZioSession] with Blocking]
    //      result <- {
    //        val batchGroups =
    //          groups.flatMap {
    //            case BatchGroup(cql, prepare) =>
    //              prepare
    //                .map(prep => executeAction(cql, prep).provide(env))
    //          }
    //        ZIO.collectAll(batchGroups)
    //      }
    //    } yield ()
    //  }
    //
    //  private[getquill] def prepareRowAndLog(cql: String, prepare: Prepare = identityPrepare): CIO[PrepareRow] =
    //    for {
    //      env <- ZIO.environment[Has[CassandraZioSession] with Blocking]
    //      csession = env.get[CassandraZioSession]
    //      boundStatement <- {
    //        ZIO.fromFuture { implicit ec => csession.prepareAsync(cql) }
    //          .mapEffect(prepare)
    //          .map(p => p._2)
    //      }
    //    } yield boundStatement
    //
    //  def probingSession: Option[CassandraZioSession] = None
    //
    //  def probe(statement: String): scala.util.Try[_] = {
    //    probingSession match {
    //      case Some(csession) =>
    //        Try(csession.prepare(statement))
    //      case None =>
    //        Try(())
    //    }
    //  }
    //
    //  def close(): Unit = fail("Zio Cassandra Session does not need to be closed because it does not keep internal state.")
  }

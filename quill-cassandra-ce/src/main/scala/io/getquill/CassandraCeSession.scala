package io.getquill

import com.datastax.driver.core.{ BoundStatement, Cluster, PreparedStatement }
import com.typesafe.config.Config
import io.getquill.context.cassandra.PrepareStatementCache
import io.getquill.context.{ AsyncFutureCache, CassandraSession, SyncCache }
import io.getquill.util.LoadConfig
import io.getquill.util.ZioConversions._
import cats.effect._
import cats.effect.implicits._
import cats._
import cats.implicits._
import zio.blocking.Blocking

case class CassandraCeSession(
  override val cluster:                    Cluster,
  override val keyspace:                   String,
  override val preparedStatementCacheSize: Long
) extends CassandraSession with SyncCache with AsyncFutureCache with AutoCloseable

object CassandraCeSession {
  def fromContextConfig[F[_]: Sync](config: CassandraContextConfig)(implicit val sf: Sync) = Resource.fromAutoCloseable(
    sf.defer(CassandraZioSession(config.cluster, config.keyspace, config.preparedStatementCacheSize))
  )

  def fromConfig[F[_]: Sync](config: Config) = fromContextConfig(CassandraContextConfig(config))

  def fromPrefix[F[_]: Sync](configPrefix: String) = fromContextConfig(CassandraContextConfig(LoadConfig(configPrefix)))

}
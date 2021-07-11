package io.getquill.context.cassandra

import io.getquill.util.LoadConfig
import io.getquill.{ CassandraCeContext, CassandraContextConfig, Literal }
import cats.effect.{ Async, IO }
import io.getquill.context.cassandra.encoding.{ Decoders, Encoders }

package object catsEffect {

  lazy val testCeDB: CassandraCeContext[Literal.type, IO] = {
    implicit val af = Async[IO]
    val c = CassandraContextConfig(LoadConfig("testStreamDB"))
    new CassandraCeContext(Literal, c.cluster, c.keyspace, c.preparedStatementCacheSize) with CassandraTestEntities with Encoders with Decoders
  }

}

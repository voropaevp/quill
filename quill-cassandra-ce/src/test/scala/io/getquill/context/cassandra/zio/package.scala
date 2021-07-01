package io.getquill.context.cassandra
import io.getquill.{ CassandraCeContext, Literal }

package object zio {
  lazy val testZioDB = new CassandraCeContext(Literal) with CassandraTestEntities
}


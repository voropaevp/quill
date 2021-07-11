package io.getquill.context.cassandra.catEffect

import cats.effect.unsafe.implicits.global
import io.getquill.context.cassandra.EncodingSpecHelper
import io.getquill.context.cassandra.QueryResultTypeCassandraSpec

class QueryResultTypeCassandraCeSpec extends EncodingSpecHelper with QueryResultTypeCassandraSpec {

  import io.getquill.context.cassandra.catsEffect.testCeDB._
  import io.getquill.context.cassandra.catsEffect.testCeDB
  import cats.effect.IO

  def result[A](fa: IO[A]): A = fa.unsafeRunSync()

  override def beforeAll: Unit = {
    result(testCeDB.run(deleteAll))
    result(testCeDB.run(liftQuery(entries).foreach(e => insert(e))))
    ()
  }

  "query" in {
    result(testCeDB.run(selectAll)) mustEqual entries
  }

  //  "stream" in {
  //    result(testCeDB.stream(selectAll)) mustEqual entries
  //  }

  "querySingle" - {
    "size" in {
      result(testCeDB.run(entitySize)) mustEqual 3
    }

    "parametrized size" in {
      result(testCeDB.run(parametrizedSize(lift(10000)))) mustEqual 0
    }
  }
}

package io.getquill.context.cassandra.zio.examples

import io.getquill.{ CassandraCeContext, _ }
import zio.Runtime
import zio.console.putStrLn

object PlainApp {

  object MyZioPostgresContext extends CassandraCeContext(Literal)
  import MyZioPostgresContext._

  case class Person(name: String, age: Int)

  val zioSession =
    CassandraZioSession.fromPrefix("testStreamDB")

  def main(args: Array[String]): Unit = {
    val people = quote {
      query[Person]
    }
    val czio =
      MyZioPostgresContext.run(people)
        .tap(result => putStrLn(result.toString))
        .provideCustomLayer(zioSession)

    Runtime.default.unsafeRun(czio)
    ()
  }
}

package io.getquill.util

import com.google.common.util.concurrent.{ FutureCallback, Futures, ListenableFuture }

import scala.concurrent.ExecutionContext
import cats.effect.Async

import java.util.concurrent.Executor
import scala.language.higherKinds

implicit class RichListenableFuture[T](lf: ListenableFuture[T])(implicit ec: ExecutionContext) {

  case object ecExecutor extends Executor {
    def execute(command: Runnable): Unit = ec.execute(command)
  }

  def toAsync[F[_]: Async]: F[T] = {
    Async[F].async { cb =>
      Futures.addCallback(lf, new FutureCallback[T] {
        override def onFailure(t: Throwable): Unit = cb(Left(t))

        override def onSuccess(result: T): Unit = cb(Right(result))
      }, ecExecutor)
    }
  }
}


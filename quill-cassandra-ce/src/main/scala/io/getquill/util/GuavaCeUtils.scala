package io.getquill.util

import com.google.common.util.concurrent.{ FutureCallback, Futures, ListenableFuture }

import cats.effect.Async
import scala.concurrent.ExecutionContext.global

import java.util.concurrent.Executor
import scala.language.higherKinds

object GuavaCeUtils {
  implicit class RichListenableFuture[T](lf: ListenableFuture[T]) {

    case object ecExecutor extends Executor {
      def execute(command: Runnable): Unit = global.execute(command)
    }

    def toAsync[F[_]: Async]: F[T] = {
      Async[F].async_ { cb =>
        Futures.addCallback(lf, new FutureCallback[T] {
          override def onFailure(t: Throwable): Unit = cb(Left(t))

          override def onSuccess(result: T): Unit = cb(Right(result))
        }, ecExecutor)
      }
    }
  }
}
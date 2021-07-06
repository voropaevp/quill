package io.getquill.context.ce

import io.getquill.NamingStrategy
import io.getquill.context.{ Context, StreamingContext }
import fs2._
import scala.language.higherKinds

trait CeContext[Idiom <: io.getquill.idiom.Idiom, Naming <: NamingStrategy, F[_]] extends Context[Idiom, Naming]
  with StreamingContext[Idiom, Naming] {

  type Error
  type Environment

  override type StreamResult[T] = Stream[F, T]
  override type Result[T] = F[T]
  override type RunQueryResult[T] = F[List[T]]
  override type RunQuerySingleResult[T] = F[T]
  override type RunBatchActionResult = F[Unit]
  override type RunActionResult = F[Unit]

  // Need explicit return-type annotations due to scala/bug#8356. Otherwise macro system will not understand Result[Long]=Task[Long] etc...
  def executeQuery[T](sql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): F[List[T]]

  def executeQuerySingle[T](sql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): F[T]
}

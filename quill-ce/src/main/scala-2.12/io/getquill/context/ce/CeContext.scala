package io.getquill.context.ce

import io.getquill.NamingStrategy
import io.getquill.context.{ Context, StreamingContext }
import scala.language.higherKinds

trait CeContext[Idiom <: io.getquill.idiom.Idiom, Naming <: NamingStrategy, F[_]]
  extends Context[Idiom, Naming]
  with StreamingContext[Idiom, Naming] {

  type Error
  type Environment

  override type Result[T] = F[T]
  override type RunActionResult = Unit
  override type RunBatchActionResult = Unit
  override type RunQueryResult[T] = List[T]
  override type RunQuerySingleResult[T] = T

  // Need explicit return-type annotations due to scala/bug#8356. Otherwise macro system will not understand Result[Long]=Task[Long] etc...
  def executeQuery[T](sql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): Result[RunQueryResult[T]]

  def executeQuerySingle[T](sql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): Result[RunQuerySingleResult[T]]
}

package io.getquill.context.ce

import io.getquill.NamingStrategy
import io.getquill.context.{ Context, StreamingContext }
import scala.language.higherKinds

trait CeContext[Idiom <: io.getquill.idiom.Idiom, Naming <: NamingStrategy, F[_]]
  extends Context[Idiom, Naming]
  with StreamingContext[Idiom, Naming] {

  type Error
  type Environment

  //  override type StreamResult[T] = Stream[F, T]
  override type Result[T] = F[T]
  override type RunActionResult = Unit
  override type RunBatchActionResult = Unit
  override type RunQueryResult[T] = List[T]
  override type RunQuerySingleResult[T] = T
  //  override type RunQuerySingleResult[T] = F[T]

  //  override type RunActionResult = F[Unit]
  //
  //  override def run[T](quoted: Quoted[T]): Result[RunQuerySingleResult[T]] = macro QueryMacro.runQuerySingle[T]
  //  override def run[T](quoted: Quoted[Query[T]]): Result[RunQueryResult[T]] = macro QueryMacro.runQuery[T]
  //
  //  override def run(quoted: Quoted[Action[_]]): Result[RunActionResult] = macro ActionMacro.runAction
  //  override def run[T](quoted: Quoted[ActionReturning[_, T]]): Result[RunActionReturningResult[T]] = macro ActionMacro.runActionReturning[T]
  //  override def run(quoted: Quoted[BatchAction[Action[_]]]): Result[RunBatchActionResult] = macro ActionMacro.runBatchAction
  //  override def run[T](quoted: Quoted[BatchAction[ActionReturning[_, T]]]): Result[RunBatchActionReturningResult[T]] = macro ActionMacro.runBatchActionReturning[T]

  // Need explicit return-type annotations due to scala/bug#8356. Otherwise macro system will not understand Result[Long]=Task[Long] etc...
  def executeQuery[T](sql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): Result[RunQueryResult[T]]

  def executeQuerySingle[T](sql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): Result[RunQuerySingleResult[T]]
}

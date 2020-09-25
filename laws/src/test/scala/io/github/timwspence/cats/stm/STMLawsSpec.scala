package io.github.timwspence.cats.stm

import cats.Eq

import org.scalacheck._, Prop.forAll

import cats.laws._
import org.typelevel.discipline._

trait STMTests extends Laws {

  val laws: STMLaws

  def stm[A: Arbitrary : Cogen : Eq](
    implicit ArbSTM: Arbitrary[STM[A]],
    ArbTVar: Arbitrary[TVar[A]],
    ArbThrowable: Arbitrary[Throwable],
    STMToProp: IsEq[STM[A]] => Prop,
    STMUnitToPro: IsEq[STM[Unit]] => Prop
  ): RuleSet =
    new DefaultRuleSet(
      name = "stm",
      parent = None,
      "set then get is set then pure" -> forAll(laws.setThenGet[A] _),
      "set then set is set" -> forAll(laws.setThenSet[A] _),
      "retry orElse stm is stm" -> forAll(laws.retryOrElse[A] _),
      "retry orElse retry is retry" -> laws.retryOrElseRetry[A],
      "abort orElse stm is abort" -> forAll(
        laws.abortOrElse[A] _)
    )

}

object STMTests {
  def apply: STMTests = new STMTests {
    override val laws: STMLaws = new STMLaws {}
  }
}

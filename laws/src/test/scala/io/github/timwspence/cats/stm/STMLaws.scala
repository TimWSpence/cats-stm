package io.github.timwspence.cats.stm

import cats.Eq
import cats.implicits._
import cats.laws._

import org.scalacheck._, Prop.forAll

import org.typelevel.discipline._

import io.github.timwspence.cats.stm._

trait STMLaws {

  def setThenGet[A](a: A, tvar: TVar[A]) =
    (tvar.set(a) >> tvar.get) <-> (tvar.set(a) >> STM.pure(a))

  def setThenSet[A](a: A, b: A, tvar: TVar[A]) =
    (tvar.set(a) >> tvar.set(b)) <-> tvar.set(a)

  def retryOrElse[A](stm: STM[A]) =
    (STM.retry[A] orElse stm) <-> stm

  def abortOrElse[A](error: Throwable, stm: STM[A]) =
    (STM.abort[A](error) orElse stm) <-> STM.abort[A](error)

  def retryOrElseRetry[A] =
    (STM.retry[A] orElse STM.retry[A]) <-> STM.retry[A]

}

trait STMTests extends Laws {

  val laws: STMLaws

  def stm[A: Arbitrary: Cogen: Eq](implicit
    ArbSTM: Arbitrary[STM[A]],
    ArbTVar: Arbitrary[TVar[A]],
    ArbThrowable: Arbitrary[Throwable],
    STMToProp: IsEq[STM[A]] => Prop,
    STMUnitToPro: IsEq[STM[Unit]] => Prop
  ): RuleSet =
    new DefaultRuleSet(
      name = "stm",
      parent = None,
      "set then get is set then pure" -> forAll(laws.setThenGet[A] _),
      "set then set is set"           -> forAll(laws.setThenSet[A] _),
      "retry orElse stm is stm"       -> forAll(laws.retryOrElse[A] _),
      "retry orElse retry is retry"   -> laws.retryOrElseRetry[A],
      "abort orElse stm is abort"     -> forAll(laws.abortOrElse[A] _)
    )

}

object STMTests {
  def apply: STMTests =
    new STMTests {
      override val laws: STMLaws = new STMLaws {}
    }
}

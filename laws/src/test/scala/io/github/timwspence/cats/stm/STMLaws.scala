package io.github.timwspence.cats.stm

import cats.Eq
import cats.implicits._
import cats.laws._

import org.scalacheck._, Prop.forAll

import org.typelevel.discipline._

import io.github.timwspence.cats.stm._

trait STMLaws {

  def getThenGet[A](tvar: TVar[A]) =
    (tvar.get, tvar.get).tupled <-> tvar.get.map(a => a -> a)

  def setThenGet[A](a: A, tvar: TVar[A]) =
    (tvar.set(a) >> tvar.get) <-> (tvar.set(a) >> STM.pure(a))

  def setThenSet[A](a: A, b: A, tvar: TVar[A]) =
    (tvar.set(a) >> tvar.set(b)) <-> tvar.set(a)

  def setThenRetry[A](a: A, tvar: TVar[A]) =
    (tvar.set(a) >> STM.retry[A]) <-> STM.retry[A]

  def setThenAbort[A](a: A, error: Throwable, tvar: TVar[A]) =
    (tvar.set(a) >> STM.abort[A](error)) <-> STM.abort[A](error)

  def retryOrElse[A](stm: STM[A]) =
    (STM.retry[A] orElse stm) <-> stm

  def orElseRetry[A](stm: STM[A]) =
    (stm orElse STM.retry[A]) <-> stm

  def abortOrElse[A](error: Throwable, stm: STM[A]) =
    (STM.abort[A](error) orElse stm) <-> STM.abort[A](error)

  def bindDistributesOverOrElse[A](lhs: STM[A], rhs: STM[A], f: A => STM[A]) =
    ((lhs orElse rhs) >>= f) <-> ((lhs >>= f) orElse (rhs >>= f))

}

trait STMTests extends Laws {

  val laws: STMLaws

  def stm[A: Arbitrary: Cogen: Eq](implicit
    ArbSTM: Arbitrary[STM[A]],
    ArbTVar: Arbitrary[TVar[A]],
    ArbThrowable: Arbitrary[Throwable],
    STMToProp: IsEq[STM[A]] => Prop,
    STMPairToProp: IsEq[STM[(A, A)]] => Prop,
    STMUnitToPro: IsEq[STM[Unit]] => Prop
  ): RuleSet =
    new DefaultRuleSet(
      name = "stm",
      parent = None,
      "get then get is get"           -> forAll(laws.getThenGet[A] _),
      "set then get is set then pure" -> forAll(laws.setThenGet[A] _),
      "set then set is set"           -> forAll(laws.setThenSet[A] _),
      "set then retry is retry"       -> forAll(laws.setThenRetry[A] _),
      "set then abort is abort"       -> forAll(laws.setThenAbort[A] _),
      "retry orElse stm is stm"       -> forAll(laws.retryOrElse[A] _),
      "stm orElse retry is stm"       -> forAll(laws.orElseRetry[A] _),
      "abort orElse stm is abort"     -> forAll(laws.abortOrElse[A] _),
      "bind distributes over orElse"  -> forAll(laws.bindDistributesOverOrElse[A] _)
    )

}

object STMTests {
  def apply: STMTests =
    new STMTests {
      override val laws: STMLaws = new STMLaws {}
    }
}

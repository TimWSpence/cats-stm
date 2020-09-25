package io.github.timwspence.cats.stm

import cats._
import cats.implicits._

import io.github.timwspence.cats.stm.STM.internal._

import org.scalacheck.{Arbitrary, Cogen, Gen, Prop}, Arbitrary.{arbitrary => arb}
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import cats.kernel.laws.IsEq

trait Instances {
  implicit def eqTResult[A: Eq]: Eq[TResult[A]] =
    new Eq[TResult[A]] {

      override def eqv(x: TResult[A], y: TResult[A]): Boolean =
        (x, y) match {
          case (TSuccess(a1), TSuccess(a2)) => a1 == a2
          case (TRetry, TRetry)             => true
          case (TFailure(_), TFailure(_))   => true // This is a bit dubious but we don't have an
          // Eq instance for Throwable so ¯\_(ツ)_/¯
          case _ => false
        }

    }

  //TODO check log as well
  implicit def eqSTM[A](implicit A: Eq[A]): Eq[STM[A]] =
    Eq.instance((stm1, stm2) => eval(stm1)._1 === eval(stm2)._1)

  implicit def arbSTM[A: Arbitrary: Cogen]: Arbitrary[STM[A]] =
    Arbitrary(
      Gen.delay(genSTM[A])
    )

  implicit def isEqSTMToProp[A: Eq](isEq: IsEq[STM[A]]): Prop =
    Prop(
      isEq.lhs === isEq.rhs
    )

  implicit def arbTVar[A: Arbitrary]: Arbitrary[TVar[A]] =
    Arbitrary(
      arb[A].map(a => new TVar(IdGen.incrementAndGet(), a, new AtomicReference(Map())))
    )

  def genSTM[A: Arbitrary: Cogen]: Gen[STM[A]] =
    Gen.oneOf(
      genPure[A],
      genBind[A],
      genOrElse[A],
      genGet[A],
      genModify[A],
      genAbort[A],
      genRetry[A]
    )

  def genPure[A: Arbitrary]: Gen[STM[A]] = arb[A].map(STM.pure(_))

  def genBind[A: Arbitrary: Cogen]: Gen[STM[A]] =
    for {
      stm <- arb[STM[A]]
      f   <- arb[A => STM[A]]
    } yield stm.flatMap(f)

  def genOrElse[A](implicit A: Arbitrary[STM[A]]): Gen[STM[A]] =
    for {
      attempt  <- arb[STM[A]]
      fallback <- arb[STM[A]]
    } yield STM.orElse(attempt, fallback)

  def genGet[A: Arbitrary]: Gen[STM[A]] =
    for {
      tvar <- genTVar[A]
    } yield tvar.flatMap(_.get)

  def genModify[A: Arbitrary: Cogen]: Gen[STM[A]] =
    for {
      tvar <- genTVar[A]
      f    <- arb[A => A]
    } yield for {
      tv  <- tvar
      _   <- tv.modify(f)
      res <- tv.get
    } yield res

  def genAbort[A]: Gen[STM[A]] = arb[Throwable].map(STM.abort[A](_))

  def genRetry[A]: Gen[STM[A]] = Gen.const(STM.retry[A])

  def genTVar[A: Arbitrary]: Gen[STM[TVar[A]]] = arb[A].map(TVar.of(_))

  implicit val genInt: Gen[Int]       = Gen.posNum[Int]
  implicit val genString: Gen[String] = Gen.alphaNumStr

  private val IdGen: AtomicLong = new AtomicLong()

}

package io.github.timwspence.cats.stm

import cats._
import cats.implicits._
import cats.effect._
import cats.kernel.laws.discipline._
import cats.laws.discipline._
import cats.laws.discipline.eq._
import cats.laws.discipline.arbitrary._

import io.github.timwspence.cats.stm.STM.internal.{TLog, TFailure, TResult, TRetry, TSuccess}

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.Configuration

import org.typelevel.discipline.scalatest.FunSuiteDiscipline

import Implicits._

object Implicits {
  implicit def eqTResult[A](implicit A: Eq[A]): Eq[TResult[A]] = new Eq[TResult[A]] {

    override def eqv(x: TResult[A], y: TResult[A]): Boolean = (x, y) match {
      case (TSuccess(a1), TSuccess(a2)) => A.eqv(a1, a2)
      case (TRetry, TRetry)             => true
      case (TFailure(_), TFailure(_))   => true // This is a bit dubious but we don't have an
      // Eq instance for Throwable so ¯\_(ツ)_/¯
      case _ => false
    }

  }

  implicit def eqSTM[A](implicit A: Eq[A]): Eq[STM[A]] =
    Eq.instance((stm1, stm2) => stm1.run(TLog.empty) === stm2.run(TLog.empty))

  implicit def genSTM[A](implicit A: Gen[A]): Gen[STM[A]] = Gen.oneOf(
    A.map(a => Function.const(TSuccess(a)) _)
  , Gen.const(Function.const(TRetry) _)
  , Gen.const(Function.const(TFailure(new RuntimeException("Txn failed"))) _)
  ).map(STM.apply _)

  implicit def arbSTM[A](implicit Gen: Arbitrary[A]): Arbitrary[STM[A]] = Arbitrary(genSTM(Gen.arbitrary))

  implicit val genInt: Gen[Int]       = Gen.posNum[Int]
  implicit val genString: Gen[String] = Gen.alphaNumStr

}

class STMLaws extends AnyFunSuite with FunSuiteDiscipline with Configuration {
  checkAll("STM[Int]", SemigroupTests[STM[Int]].semigroup)

  checkAll("STM[Int]", MonoidTests[STM[Int]].monoid)

  checkAll("STM[Int]", FunctorTests[STM].functor[Int, Int, Int])

  checkAll("STM[Int]", ApplicativeTests[STM].applicative[Int, Int, Int])

  checkAll("STM[Int]", MonadTests[STM].monad[Int, Int, Int])

  checkAll("STM[Int]", SemigroupKTests[STM].semigroupK[Int])

  checkAll("STM[Int]", MonoidKTests[STM].monoidK[Int])
}

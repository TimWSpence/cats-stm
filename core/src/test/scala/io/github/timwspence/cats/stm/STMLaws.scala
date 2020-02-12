package io.github.timwspence.cats.stm

import cats._
import cats.implicits._
import cats.derived._

import org.scalatest.funsuite.AnyFunSuite

import org.typelevel.discipline.scalatest.Discipline
import org.scalatest.prop.Configuration
import cats.laws.discipline._
import cats.kernel.laws.discipline._
import cats.laws.discipline.eq._
import cats.laws.discipline.arbitrary._

import Helpers._
import org.scalacheck.Arbitrary
import io.github.timwspence.cats.stm.STM.internal.TLog
import io.github.timwspence.cats.stm.STM.internal.TResult

object Helpers {
  implicit def eqTResult[A: Eq : shapeless.Typeable]: Eq[TResult[A]] = {
    // import auto.eq._
    semi.eq[TResult[A]]
  }

  implicit  def eqSTM[A: Eq](implicit Log: ExhaustiveCheck[TLog]): Eq[STM[A]] = Eq.instance((stm1, stm2) => Log.allValues.forall(a => Eq[TResult[A]].eqv(stm1.run(a), stm1.run(a))))

  implicit val arbitraryTLog: Arbitrary[TLog] = ???

  implicit def arbitrarySTM[A: Arbitrary]: Arbitrary[STM[A]] = Arbitrary.arbitrary[Function1[TLog, A]].map(r => STM(r))
}

class STMLaws extends AnyFunSuite with Discipline with Configuration {
  checkAll("STM[String]", MonoidTests[STM[String]].monoid)
}

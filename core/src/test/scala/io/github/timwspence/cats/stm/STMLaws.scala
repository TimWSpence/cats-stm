package io.github.timwspence.cats.stm

import cats._
import cats.implicits._
import cats.derived._
import cats.effect._

import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable.{Map => MMap}

import org.typelevel.discipline.scalatest.Discipline
import org.scalatest.prop.Configuration
import cats.laws.discipline._
import cats.kernel.laws.discipline._
import cats.laws.discipline.eq._
import cats.laws.discipline.arbitrary._

import shapeless._

import Helpers._
import org.scalacheck.{Arbitrary, Gen}
import io.github.timwspence.cats.stm.STM.internal.TLog
import io.github.timwspence.cats.stm.STM.internal.TResult
import io.github.timwspence.cats.stm.STM.internal.TSuccess
import io.github.timwspence.cats.stm.STM.internal.TRetry
import io.github.timwspence.cats.stm.STM.internal.TFailure

object Helpers {
  implicit def eqTResult[A](implicit A: Eq[A]): Eq[TResult[A]] = new Eq[TResult[A]] {

    override def eqv(x: TResult[A], y: TResult[A]): Boolean = (x,y) match {
      case (TSuccess(a1), TSuccess(a2)) => A.eqv(a1,a2)
      case (TRetry, TRetry) => true
      case (TFailure(_), TFailure(_)) => true // This is a bit dubious but we don't have an
                                              // Eq instance for Throwable so ¯\_(ツ)_/¯
      case _ => false
    }

  }

  implicit  def eqSTM[A: Eq](implicit Log: ExhaustiveCheck[TLog]): Eq[STM[A]] = Eq.instance((stm1, stm2) => Log.allValues.forall(a => Eq[TResult[A]].eqv(stm1.run(a), stm1.run(a))))


  //Possibly the best way to build a stm is to generate list of tvars and then from that generate list of stm actions from that
  //and fold with bind

  implicit def genTVar[A](implicit A: Gen[A]): Gen[TVar[A]] = A.map(a => TVar.of(a).commit[IO].unsafeRunSync)

  //This looks a bit weird but STM.run is always invoked with an empty TLog so it's ok
  implicit val exhaustiveCheckTlog: ExhaustiveCheck[TLog] = ExhaustiveCheck.instance(List(TLog.empty))

  implicit def genSTM[A: Gen]: Gen[STM[A]] = ???
}

class STMLaws extends AnyFunSuite with Discipline with Configuration {
  // checkAll("STM[String]", MonoidTests[STM[String]].monoid)
}

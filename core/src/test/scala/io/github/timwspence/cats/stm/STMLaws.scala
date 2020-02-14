package io.github.timwspence.cats.stm

import cats._
import cats.implicits._
import cats.effect._
import cats.kernel.laws.discipline._
import cats.laws.AlternativeLaws
import cats.laws.discipline._
import cats.laws.discipline.eq._
import cats.laws.discipline.arbitrary._

import io.github.timwspence.cats.stm.STM.internal.{TLog, TFailure, TResult, TRetry, TSuccess}

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.Configuration

import org.typelevel.discipline.scalatest.FunSuiteDiscipline

import Implicits._

object Implicits extends LowPriorityImplicits {
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

  // Why doesn't this work? Just use the simple eqSTM above for now. It achieves what is intended anyway

  // implicit  def eqSTM[A: Eq](implicit Log: ExhaustiveCheck[TLog]): Eq[STM[A]] = Eq.instance((stm1, stm2) => Log.allValues.forall(a => Eq[TResult[A]].eqv(stm1.run(a), stm1.run(a))))

  //This looks a bit weird but STM.run is always invoked with an empty TLog so it's ok
  // implicit val exhaustiveCheckTlog: ExhaustiveCheck[TLog] = ExhaustiveCheck.instance(List(TLog.empty))

  implicit def genSTM[A](implicit A: Gen[A]): Gen[STM[A]] = Gen.oneOf(
    A.map(a => Function.const(TSuccess(a)) _)
  , Gen.const(Function.const(TRetry) _)
  , Gen.const(Function.const(TFailure(new RuntimeException("Txn failed"))) _)
  ).map(STM.apply _)

  // implicit def genTVar[A](implicit A: Gen[A]): Gen[TVar[A]] = A.map(a => TVar.of(a).commit[IO].unsafeRunSync)

  // implicit def oldGenSTM[A: Gen: Order](implicit TVar: Gen[TVar[A]], M: Monoid[A]): Gen[STM[A]] = {
  //   def genGetSTM[A](tv: TVar[A]): Gen[STM[Unit]]                     = Gen.const(tv.get.void)
  //   def genSetSTM[A](tv: TVar[A])(implicit A: Gen[A]): Gen[STM[Unit]] = A.map(tv.set)
  //   def genModifySTM[A](tv: TVar[A])(implicit A: Gen[A], M: Monoid[A]): Gen[STM[Unit]] =
  //     A.map(a1 => tv.modify(a2 => M.combine(a1, a2)))
  //   def genCheckSTM[A](tv: TVar[A])(implicit A: Gen[A], Ord: Order[A]): Gen[STM[Unit]] =
  //     A.map(a1 => tv.get >>= (a2 => STM.check(Ord.compare(a1, a2) > 0)))
  //   def genSingleSTM[A: Gen: Monoid: Order](tv: TVar[A]): Gen[STM[Unit]] =
  //     Gen.oneOf(genGetSTM(tv), genSetSTM(tv), genModifySTM(tv), genCheckSTM(tv))
  //   def sumAll[A](tvs: List[TVar[A]])(implicit M: Monoid[A]): STM[A] = tvs.traverse(_.get).map(M.combineAll)

  //   for {
  //     tvars <- Gen.listOfN(5, TVar)
  //     stms <- Gen.nonEmptyListOf(for {
  //       idx <- Gen.choose(0, tvars.length - 1)
  //       tv = tvars(idx)
  //       stm <- genSingleSTM(tv)
  //     } yield stm)
  //   } yield stms.reduce(_ >> _) >> sumAll(tvars)
  // }

  implicit def arbSTM[A: Monoid: Gen: Order](implicit Gen: Gen[STM[A]]): Arbitrary[STM[A]] = Arbitrary(Gen)

  implicit val genInt: Gen[Int]       = Gen.posNum[Int]
  implicit val genString: Gen[String] = Gen.alphaNumStr

}

trait LowPriorityImplicits {
  // Generates non-interesting arbitrary STMs using pure
  // Necessary as the Monad laws require Arbitrary[A => B]
  // And Function does not satisfy the (Monoid, Ord) constraints
  // we used above to generate more complex STM  values
  implicit def arbSTMNoConstraints[A](implicit A: Arbitrary[A]): Arbitrary[STM[A]] = Arbitrary(A.arbitrary.map(STM.pure))
}

class STMLaws extends AnyFunSuite with FunSuiteDiscipline with Configuration {
  checkAll("STM[Int]", SemigroupTests[STM[Int]].semigroup)

  checkAll("STM[Int]", MonoidTests[STM[Int]].monoid)

  checkAll("STM[Int]", FunctorTests[STM].functor[Int, Int, Int])

  checkAll("STM[Int]", MonadTests[STM].monad[Int, Int, Int])

  checkAll("STM[Int]", AlternativeTests[STM].alternative[Int, Int, Int])
}

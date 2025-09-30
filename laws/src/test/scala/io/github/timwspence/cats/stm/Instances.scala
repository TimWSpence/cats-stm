/*
 * Copyright 2017 TimWSpence
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.timwspence.cats.stm

import java.util.concurrent.atomic.AtomicLong

import munit.CatsEffectSuite

import cats.effect.IO
import cats._
import cats.implicits._
import cats.kernel.laws.IsEq
import org.scalacheck.{Arbitrary, Cogen, Gen, Prop}, Arbitrary.arbitrary

import cats.effect.{Deferred, Ref}
import cats.effect.std.Semaphore

trait Instances extends CatsEffectSuite with HasSTM {
  import stm._

  implicit def eqTResult[A: Eq]: Eq[TResult[A]] =
    new Eq[TResult[A]] {

      override def eqv(x: TResult[A], y: TResult[A]): Boolean =
        (x, y) match {
          case (TSuccess(a1), TSuccess(a2)) => Eq[A].eqv(a1, a2)
          case (TRetry, TRetry)             => true
          case (TFailure(e1), TFailure(e2)) => e1 === e2
          case _                            => false
        }

    }

  implicit def eqTLogEntry: Eq[TLogEntry] =
    Eq.instance { (tlog1, tlog2) =>
      (tlog1.tvar.id === tlog2.tvar.id) &&
      (tlog1.initial == tlog2.initial) &&
      (tlog1.current == tlog2.current)
    }

  // A log is in the same state if all dirty entries are in the same state
  implicit def eqTLog: Eq[TLog] = {
    def run(tlog: TLog): List[(Long, Boolean)] =
      tlog.values.toList
        .sortBy(_.tvar.id)
        .traverse(e => e.isDirty.map(d => e.tvar.id -> d))
        .unsafeRunSync()
        .filter(_._2)

    Eq.instance { (tlog1, tlog2) =>
      run(tlog1) === run(tlog2)
    }
  }

  implicit def eqTxn[A](implicit A: Eq[A]): Eq[Txn[A]] =
    Eq.instance { (txn1, txn2) =>
      val (res1, log1) =
        (for {
          idGen <- Ref.of[IO, Long](0)
          res   <- eval(idGen, txn1)
        } yield res).unsafeRunSync()

      val (res2, log2) =
        (for {
          idGen <- Ref.of[IO, Long](0)
          res   <- eval(idGen, txn2)
        } yield res).unsafeRunSync()

      res1 === res2 && log1 === log2
    }

  implicit def arbitraryTxn[A: Arbitrary: Cogen]: Arbitrary[Txn[A]] =
    Arbitrary(
      Gen.delay(genTxn[A])
    )

  implicit def isEqSTMToProp[A: Eq](isEq: IsEq[Txn[A]]): Prop =
    Prop(
      isEq.lhs === isEq.rhs
    )

  implicit def arbitraryTVar[A: Arbitrary]: Arbitrary[TVar[A]] =
    Arbitrary(
      arbitrary[A].map(a =>
        new TVar(
          IdGen.incrementAndGet(),
          Ref.unsafe[IO, A](a),
          Semaphore[IO](1).unsafeRunSync(),
          Ref.of[IO, List[Deferred[IO, Unit]]](Nil).unsafeRunSync()
        )
      )
    )

  def genTxn[A: Arbitrary: Cogen]: Gen[Txn[A]] =
    Gen.sized { n =>
      if (n == 0)
        Gen.oneOf(
          genPure[A],
          genAbort[A],
          genRetry[A]
        )
      else
        Gen.resize(
          n / 2,
          // Encourage it to generate non-trivial transactions
          Gen.frequency(
            1 -> genPure[A],
            1 -> genAbort[A],
            1 -> genRetry[A],
            3 -> genGet[A],
            3 -> genModify[A],
            5 -> genBind[A],
            2 -> genOrElse[A],
            1 -> genHandleError[A]
          )
        )
    }

  def genPure[A: Arbitrary]: Gen[Txn[A]] = arbitrary[A].map(Txn.pure(_))

  def genBind[A: Arbitrary: Cogen]: Gen[Txn[A]] =
    for {
      txn <- arbitrary[Txn[A]]
      f   <- arbitrary[A => Txn[A]]
    } yield txn.flatMap(f)

  def genHandleError[A: Arbitrary: Cogen]: Gen[Txn[A]] =
    for {
      txn <- arbitrary[Txn[A]]
      f   <- arbitrary[Throwable => Txn[A]]
    } yield txn.handleErrorWith(f)

  def genOrElse[A](implicit A: Arbitrary[Txn[A]]): Gen[Txn[A]] =
    for {
      attempt  <- arbitrary[Txn[A]]
      fallback <- arbitrary[Txn[A]]
    } yield attempt.orElse(fallback)

  def genGet[A: Arbitrary]: Gen[Txn[A]] =
    for {
      tvar <- genTVar[A]
    } yield tvar.flatMap(_.get)

  def genModify[A: Arbitrary: Cogen]: Gen[Txn[A]] =
    for {
      tvar <- genTVar[A]
      f    <- arbitrary[A => A]
    } yield for {
      tv  <- tvar
      _   <- tv.modify(f)
      res <- tv.get
    } yield res

  def genAbort[A]: Gen[Txn[A]] = genError.map(stm.abort[A](_))

  def genRetry[A]: Gen[Txn[A]] = Gen.const(stm.retry[A])

  def genTVar[A: Arbitrary]: Gen[Txn[TVar[A]]] = arbitrary[A].map(TVar.of(_))

  implicit val genInt: Gen[Int]       = Gen.posNum[Int]
  implicit val genString: Gen[String] = Gen.alphaNumStr

  private val IdGen: AtomicLong = new AtomicLong()

  case class TestException(i: Int) extends RuntimeException

  val genError: Gen[Throwable] = arbitrary[Int].map(TestException(_))

  implicit val arbitraryitraryForThrowable: Arbitrary[Throwable] =
    Arbitrary(
      genError
    )

  implicit val eqForThrowable: Eq[Throwable] = Eq.fromUniversalEquals[Throwable]

}

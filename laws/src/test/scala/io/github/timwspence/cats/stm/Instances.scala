/*
 * Copyright 2020 TimWSpence
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
import org.scalacheck.{Arbitrary, Cogen, Gen, Prop}

import Arbitrary.{arbitrary => arb}
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.concurrent.Semaphore

trait Instances extends CatsEffectSuite with HasSTM {
  import stm._
  import stm.Internals._

  implicit def eqTResult[A: Eq]: Eq[TResult[A]] =
    new Eq[TResult[A]] {

      override def eqv(x: TResult[A], y: TResult[A]): Boolean =
        (x, y) match {
          case (TSuccess(a1), TSuccess(a2)) => a1 === a2
          case (TRetry, TRetry)             => true
          case (TFailure(_), TFailure(_))   => true // This is a bit dubious but we don't have an
          // Eq instance for Throwable so ¯\_(ツ)_/¯
          case _ => false
        }

    }

  implicit def eqTLogEntry: Eq[TLogEntry] =
    Eq.instance { (tlog1, tlog2) =>
      (tlog1.tvar.id === tlog2.tvar.id) &&
      (tlog1.initial == tlog2.initial) &&
      (tlog1.current == tlog2.current)
    }

  //A log is in the same state if all dirty entries are in the same state
  implicit def eqTLog: Eq[TLog] =
    Eq.instance { (tlog1, tlog2) =>
      tlog1.values.filter(_.isDirty).toList.sortBy(_.tvar.id) ===
        tlog2.values.filter(_.isDirty).toList.sortBy(_.tvar.id)
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

  implicit def arbTxn[A: Arbitrary: Cogen]: Arbitrary[Txn[A]] =
    Arbitrary(
      Gen.delay(genTxn[A])
    )

  implicit def isEqSTMToProp[A: Eq](isEq: IsEq[Txn[A]]): Prop =
    Prop(
      isEq.lhs === isEq.rhs
    )

  implicit def arbTVar[A: Arbitrary]: Arbitrary[TVar[A]] =
    Arbitrary(
      arb[A].map(a =>
        new TVar(
          IdGen.incrementAndGet(),
          a,
          Semaphore[IO](1).unsafeRunSync(),
          Ref.of[IO, List[Deferred[IO, Unit]]](Nil).unsafeRunSync()
        )
      )
    )

  def genTxn[A: Arbitrary: Cogen]: Gen[Txn[A]] =
    Gen.oneOf(
      genPure[A],
      genBind[A],
      genOrElse[A],
      genGet[A],
      genModify[A],
      genAbort[A],
      genRetry[A]
    )

  def genPure[A: Arbitrary]: Gen[Txn[A]] = arb[A].map(Txn.pure(_))

  def genBind[A: Arbitrary: Cogen]: Gen[Txn[A]] =
    for {
      stm <- arb[Txn[A]]
      f   <- arb[A => Txn[A]]
    } yield stm.flatMap(f)

  def genOrElse[A](implicit A: Arbitrary[Txn[A]]): Gen[Txn[A]] =
    for {
      attempt  <- arb[Txn[A]]
      fallback <- arb[Txn[A]]
    } yield attempt.orElse(fallback)

  def genGet[A: Arbitrary]: Gen[Txn[A]] =
    for {
      tvar <- genTVar[A]
    } yield tvar.flatMap(_.get)

  def genModify[A: Arbitrary: Cogen]: Gen[Txn[A]] =
    for {
      tvar <- genTVar[A]
      f    <- arb[A => A]
    } yield for {
      tv  <- tvar
      _   <- tv.modify(f)
      res <- tv.get
    } yield res

  def genAbort[A]: Gen[Txn[A]] = arb[Throwable].map(stm.abort[A](_))

  def genRetry[A]: Gen[Txn[A]] = Gen.const(stm.retry[A])

  def genTVar[A: Arbitrary]: Gen[Txn[TVar[A]]] = arb[A].map(TVar.of(_))

  implicit val genInt: Gen[Int]       = Gen.posNum[Int]
  implicit val genString: Gen[String] = Gen.alphaNumStr

  private val IdGen: AtomicLong = new AtomicLong()

}

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

import cats.implicits._
import cats.laws._
import org.scalacheck._
import org.typelevel.discipline._

import Prop.forAll

trait STMLaws extends HasSTM {
  import stm._

  def getThenGet[A](tvar: TVar[A]) =
    (tvar.get, tvar.get).tupled <-> tvar.get.map(a => a -> a)

  def setThenGet[A](a: A, tvar: TVar[A]) =
    (tvar.set(a) >> tvar.get) <-> (tvar.set(a) >> stm.pure(a))

  def setThenSet[A](a: A, b: A, tvar: TVar[A]) =
    (tvar.set(a) >> tvar.set(b)) <-> tvar.set(b)

  def setThenRetry[A](a: A, tvar: TVar[A]) =
    (tvar.set(a) >> stm.retry[A]) <-> stm.retry[A]

  def setThenAbort[A](a: A, error: Throwable, tvar: TVar[A]) =
    (tvar.set(a) >> stm.abort[A](error)) <-> stm.abort[A](error)

  def retryOrElse[A](txn: Txn[A]) =
    (stm.retry[A] orElse txn) <-> txn

  def orElseRetry[A](txn: Txn[A]) =
    (txn orElse stm.retry[A]) <-> txn

  def abortOrElse[A](error: Throwable, txn: Txn[A]) =
    (stm.abort[A](error) orElse txn) <-> stm.abort[A](error)

}

trait STMTests extends Laws with STMLaws {
  import stm._

  def stmLaws[A: Arbitrary](implicit
    ArbTxn: Arbitrary[Txn[A]],
    ArbTVar: Arbitrary[TVar[A]],
    ArbThrowable: Arbitrary[Throwable],
    TxnToProp: IsEq[Txn[A]] => Prop,
    TxnPairToProp: IsEq[Txn[(A, A)]] => Prop,
    TxnUnitToProp: IsEq[Txn[Unit]] => Prop
  ): RuleSet =
    new DefaultRuleSet(
      name = "stm",
      parent = None,
      "get then get is get"           -> forAll(getThenGet[A](_)),
      "set then get is set then pure" -> forAll(setThenGet[A](_, _)),
      "set then set is set"           -> forAll(setThenSet[A](_, _, _)),
      "set then retry is retry"       -> forAll(setThenRetry[A](_, _)),
      "set then abort is abort"       -> forAll(setThenAbort[A](_, _, _)),
      "retry orElse stm is stm"       -> forAll(retryOrElse[A](_)),
      "stm orElse retry is stm"       -> forAll(orElseRetry[A](_)),
      "abort orElse stm is abort"     -> forAll(abortOrElse[A](_, _))
    )

}

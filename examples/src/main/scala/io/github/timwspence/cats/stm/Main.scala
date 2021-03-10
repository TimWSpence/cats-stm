/*
 * Copyright 2017-2021 TimWSpence
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

import scala.concurrent.duration._

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple {

  val stm = STM.runtime[IO].unsafeRunSync()
  import stm._

  override def run: IO[Unit] =
    for {
      accountForTim   <- stm.commit(TVar.of[Long](100))
      accountForSteve <- stm.commit(TVar.of[Long](0))
      _               <- printBalances(accountForTim, accountForSteve)
      _               <- giveTimMoreMoney(accountForTim).start
      _               <- transfer(accountForTim, accountForSteve)
      _               <- printBalances(accountForTim, accountForSteve)
    } yield ()

  private def transfer(accountForTim: TVar[Long], accountForSteve: TVar[Long]): IO[Unit] =
    stm.commit {
      for {
        balance <- accountForTim.get
        _       <- stm.check(balance > 100)
        _       <- accountForTim.modify(_ - 100)
        _       <- accountForSteve.modify(_ + 100)
      } yield ()
    }

  private def giveTimMoreMoney(accountForTim: TVar[Long]): IO[Unit] =
    for {
      _ <- IO.sleep(5000.millis)
      _ <- stm.commit(accountForTim.modify(_ + 1))
    } yield ()

  private def printBalances(accountForTim: TVar[Long], accountForSteve: TVar[Long]): IO[Unit] =
    for {
      t <- stm.commit(for {
        t <- accountForTim.get
        s <- accountForSteve.get
      } yield (t, s))
      (amountForTim, amountForSteve) = t
      _ <- IO(println(s"Tim: $amountForTim"))
      _ <- IO(println(s"Steve: $amountForSteve"))
    } yield ()

}

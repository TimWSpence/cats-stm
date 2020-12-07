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
// import io.github.timwspence.cats.stm._
import scala.concurrent.duration._

import cats.effect._
import cats.effect.unsafe.implicits.global
import cats.implicits._

object SantaClausProblem extends IOApp.Simple {

  val stm = STM[IO].unsafeRunSync()
  import stm._

  override def run: IO[Unit] =
    mainProblem.timeout(5.seconds)

  def mainProblem: IO[Unit] =
    for {
      in  <- stm.commit(TVar.of(0))
      elf = (for {
          _ <- stm.commit(
            for {
              cur <- in.get
              _   <- stm.check(cur == 1)
              _   <- in.set(0)
            } yield ()
          )
          // _ <- IO.println("elf stuff")
        } yield ()).foreverM
      santa = (for {
          _ <- stm.commit(
            for {
              cur <- in.get
              _   <- stm.check(cur == 0)
              _   <- in.set(1)
            } yield ()
          )
          // _ <- IO.println("santa stuff")
        } yield ()).foreverM
      e <- elf.start
      s <- santa.start
      _ <- e.join
      _ <- s.join
      //TODO why always signalling 0 signals?
    } yield ()

}

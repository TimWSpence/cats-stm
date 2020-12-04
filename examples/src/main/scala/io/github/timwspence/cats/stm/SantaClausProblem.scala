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

  def meetInStudy(id: Int): IO[Unit] = IO.println(show"Elf $id meeting in the study")

  sealed abstract case class Gate(capacity: Int, tv: TVar[Int]) {
    def pass: IO[Unit]    = Gate.pass(this)
    def operate: IO[Unit] = Gate.operate(this)
  }
  object Gate {
    def of(capacity: Int) =
      TVar.of(0).map(new Gate(capacity, _) {})

    def pass(g: Gate): IO[Unit] =
      stm.commit {
        for {
          nLeft <- g.tv.get
          _     <- stm.check({println(s"nLeft in pass is $nLeft"); nLeft > 0})
          _     <- g.tv.modify(_ - 1)
        } yield ()
      }

    def operate(g: Gate): IO[Unit] =
      for {
        _ <- stm.commit(g.tv.set(g.capacity))
        _ <- stm.commit {
          for {
            nLeft <- g.tv.get
            _     <- stm.check(nLeft === 0)
          } yield ()
        }
      } yield ()
  }

  sealed abstract case class Group(
    n: Int,
    tv: TVar[(Int, Gate, Gate)]
  ) {
    def join: IO[(Gate, Gate)]   = Group.join(this)
    def await: Txn[(Gate, Gate)] = Group.await(this)
  }
  object Group {
    def of(n: Int): IO[Group] =
      stm.commit {
        for {
          g1 <- Gate.of(n)
          g2 <- Gate.of(n)
          tv <- TVar.of((n, g1, g2))
        } yield new Group(n, tv) {}
      }

    def join(g: Group): IO[(Gate, Gate)] =
      stm.commit {
        for {
          (nLeft, g1, g2) <- g.tv.get
          _               <- stm.check(nLeft > 0)
          _               <- g.tv.set((nLeft - 1, g1, g2))
        } yield (g1, g2)
      }
    def await(g: Group): Txn[(Gate, Gate)] =
      for {
        (nLeft, g1, g2) <- g.tv.get
        _               <- {println(s"nLeft is $nLeft"); stm.check(nLeft === 0)}
        newG1           <- Gate.of(g.n)
        newG2           <- Gate.of(g.n)
        _               <- g.tv.set((g.n, newG1, newG2))
      } yield (g1, g2)
  }

  def helper1(group: Group, doTask: IO[Unit]): IO[Unit] =
    (for {
      _ <- IO.println("Joining gate")
      (inGate, outGate) <- group.join
      _                 <- inGate.pass
      _                 <- doTask
      _                 <- outGate.pass
     } yield ()).handleErrorWith {
      case e => IO(e.printStackTrace())
    }

  def elf(g: Group, i: Int) =
    helper1(g, meetInStudy(i)).foreverM.start

  def santa(elfGroup: Group): IO[Unit] = {
    def run(task: String, gates: (Gate, Gate)): IO[Unit] =
      for {
        _ <- IO.println(show"Ho! Ho! Ho! let’s $task")
        _ <- gates._1.operate
        _ <- IO.println("operated gate 1") //Seems santa doesn't get this far?
        _ <- gates._2.operate
        _ <- IO.println("operated gates") //Seems santa doesn't get this far?
      } yield ()

    (for {
      _ <- IO.println("----------")
      _ <- stm.commit(elfGroup.await).flatMap {
        g: (Gate, Gate) => run("meet in study", g)
      }
     } yield ()).handleErrorWith {
      case e => IO(e.printStackTrace())
    }
  }

  def mainProblem: IO[Unit] =
    for {
      elfGroup <- Group.of(1)
      _         <- List(1).traverse_(n => elf(elfGroup, n))
      _         <- santa(elfGroup).foreverM.void
    } yield ()

}

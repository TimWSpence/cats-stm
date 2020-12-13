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

import cats.data._
import cats.effect._
import cats.effect.unsafe.implicits.global
import cats.implicits._

object SantaClausProblem extends IOApp.Simple {

  val stm = STM.runtime[IO].unsafeRunSync()
  import stm._

  override def run: IO[Unit] =
    mainProblem.timeout(5.seconds)

  def meetInStudy(id: Int): IO[Unit] = IO(println(show"Elf $id meeting in the study"))

  def deliverToys(id: Int): IO[Unit] = IO(println(show"Reindeer $id delivering toys"))

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
          _     <- stm.check(nLeft > 0)
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
          t <- g.tv.get
          (nLeft, g1, g2) = t
          _ <- stm.check(nLeft > 0)
          _ <- g.tv.set((nLeft - 1, g1, g2))
        } yield (g1, g2)
      }
    def await(g: Group): Txn[(Gate, Gate)] =
      for {
        t <- g.tv.get
        (nLeft, g1, g2) = t
        _     <- stm.check(nLeft === 0)
        newG1 <- Gate.of(g.n)
        newG2 <- Gate.of(g.n)
        _     <- g.tv.set((g.n, newG1, newG2))
      } yield (g1, g2)
  }

  def helper1(group: Group, doTask: IO[Unit]): IO[Unit] =
    for {
      t <- group.join
      (inGate, outGate) = t
      _ <- inGate.pass
      _ <- doTask
      _ <- outGate.pass
    } yield ()

  def elf2(group: Group, id: Int): IO[Unit] =
    helper1(group, meetInStudy(id))

  def reindeer2(group: Group, id: Int): IO[Unit] =
    helper1(group, deliverToys(id))

  def randomDelay: IO[Unit] = IO(scala.util.Random.nextInt(10000)).flatMap(n => IO.sleep(n.micros))

  def elf(g: Group, i: Int) =
    (elf2(g, i) >> randomDelay).foreverM.start

  def reindeer(g: Group, i: Int) =
    (reindeer2(g, i) >> randomDelay).foreverM.start

  def choose[A](choices: NonEmptyList[(Txn[A], A => IO[Unit])]): IO[Unit] = {
    def actions: NonEmptyList[Txn[IO[Unit]]] =
      choices.map {
        case (guard, rhs) =>
          for {
            value <- guard
          } yield rhs(value)
      }
    for {
      act <- stm.commit(actions.reduceLeft(_.orElse(_)))
      _   <- act
    } yield ()
  }

  def santa(elfGroup: Group, reinGroup: Group): IO[Unit] = {
    def run(task: String, gates: (Gate, Gate)): IO[Unit] =
      for {
        _ <- IO(println(show"Ho! Ho! Ho! let’s $task"))
        _ <- gates._1.operate
        _ <- gates._2.operate
      } yield ()

    for {
      _ <- IO(println("----------"))
      _ <- choose[(Gate, Gate)](
        NonEmptyList.of(
          (reinGroup.await, { (g: (Gate, Gate)) => run("deliver toys", g) }),
          (elfGroup.await, { (g: (Gate, Gate)) => run("meet in study", g) })
        )
      )
    } yield ()
  }

  def mainProblem: IO[Unit] =
    for {
      elfGroup  <- Group.of(3)
      _         <- List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).traverse_(n => elf(elfGroup, n))
      reinGroup <- Group.of(9)
      _         <- List(1, 2, 3, 4, 5, 6, 7, 8, 9).traverse_(n => reindeer(reinGroup, n))
      _         <- santa(elfGroup, reinGroup).foreverM.void
    } yield ()

}

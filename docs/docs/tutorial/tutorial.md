---
layout: docs
title:  "Tutorial"
position: 10
---

<nav role="navigation" id="toc"></nav>

## The Santa Claus Problem

> Santa repeatedly sleeps until wakened by either all of his nine rein-deer, back from their holidays, or by a group of three of his ten elves. If awakened by the reindeer, he harnesses each of them to his sleigh, delivers toys with them and finally unharnesses them (allowing them to go off on holiday). If awakened by a group of elves, he shows each of the group into his study, consults with them on toy R&D and finally shows them each out (allowing them to go back to work). Santa should give priority to the reindeer in the case that there is both a group of elves and a group of reindeer waiting.

```scala mdoc
import io.github.timwspence.cats.stm._
import cats.data._
import cats.effect._
import cats.implicits._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.global

def meetInStudy(id: Int): IO[Unit] = IO(println(show"Elf $id meeting in the study"))

def deliverToys(id: Int): IO[Unit] = IO(println(show"Reindeer $id delivering toys"))

sealed abstract case class Gate(capacity: Int, tv: TVar[Int]){
  def pass: IO[Unit] = Gate.pass(this)
  def operate: IO[Unit] = Gate.operate(this)
}; object Gate{
  def of(capacity: Int) =
    TVar.of(0).map(new Gate(capacity, _){})

  def pass(g: Gate): IO[Unit] =
    STM.atomically[IO]{
      for {
        nLeft <- g.tv.get
        _ <- STM.check(nLeft > 0)
        _ <- g.tv.modify(_ - 1)
      } yield ()
    }

  def operate(g: Gate): IO[Unit] =
    for {
      _ <- STM.atomically[IO](g.tv.set(g.capacity))
      _ <- STM.atomically[IO]{
        for {
          nLeft <- g.tv.get
          _ <- STM.check(nLeft === 0)
        } yield ()
      }
    } yield ()
}

sealed abstract case class Group(
  n: Int,
  tv: TVar[(Int, Gate, Gate)]
){
  def join: IO[(Gate, Gate)] = Group.join(this)
  def await: STM[(Gate, Gate)] = Group.await(this)
}; object Group {
  def of(n: Int): IO[Group] = STM.atomically[IO]{
    for {
      g1 <- Gate.of(n)
      g2 <- Gate.of(n)
      tv <- TVar.of((n, g1, g2))
    } yield new Group(n, tv){}
  }

  def join(g: Group): IO[(Gate, Gate)] = STM.atomically[IO]{
    for {
      (nLeft, g1, g2) <- g.tv.get
      _ <- STM.check(nLeft > 0)
      _ <- g.tv.set((nLeft - 1, g1, g2))
    } yield (g1, g2)
  }
  def await(g: Group): STM[(Gate, Gate)] = for {
    (nLeft, g1, g2) <- g.tv.get
    _ <- STM.check(nLeft === 0)
    newG1 <- Gate.of(g.n)
    newG2 <- Gate.of(g.n)
    _ <- g.tv.set((g.n, newG1, newG2))
  } yield (g1, g2)
}

def helper1(group: Group, doTask: IO[Unit]): IO[Unit] = for {
  (inGate, outGate) <- group.join
  _ <- inGate.pass
  _ <- doTask
  _ <- outGate.pass
} yield ()

def elf2(group: Group, id: Int): IO[Unit] =
  helper1(group, meetInStudy(id))


def reindeer2(group: Group, id: Int): IO[Unit] =
  helper1(group, deliverToys(id))



implicit val T: Timer[IO] = IO.timer(global)
implicit val CS : ContextShift[IO] = IO.contextShift(global)

def randomDelay: IO[Unit] = {
  IO(scala.util.Random.nextInt(10000))
    .flatMap{n => Timer[IO].sleep(n.micros)}
}

def elf(g: Group, i: Int): IO[Fiber[IO, Nothing]] = {
  (elf2(g, i) >> randomDelay).foreverM.start
}

def reindeer(g: Group, i: Int): IO[Fiber[IO, Nothing]] = {
  (reindeer2(g, i) >> randomDelay).foreverM.start
}

def choose[A](choices: NonEmptyList[(STM[A], A => IO[Unit])]): IO[Unit] = {
  def actions : NonEmptyList[STM[IO[Unit]]] =  choices.map{
    case (guard, rhs) =>  for {
      value <- guard
    } yield rhs(value)
  }
  for {
    act <- STM.atomically[IO]{actions.reduceLeft(_.orElse(_))}
    _ <- act
  } yield ()
}

def santa(elfGroup: Group, reinGroup: Group): IO[Unit] = {
  def run(task: String, gates: (Gate, Gate)): IO[Unit] = for {
    _ <- IO(println(show"Ho! Ho! Ho! let’s $task"))
    _ <- gates._1.operate
    _ <- gates._2.operate
  } yield ()

  for {
    _ <- IO(println("----------"))
    _ <- choose[(Gate, Gate)](NonEmptyList.of(
      (reinGroup.await, { g: (Gate, Gate) => run("deliver toys", g)}),
      (elfGroup.await, { g: (Gate, Gate) => run("meet in study", g)})
    ))
  } yield ()
}


def main: IO[Unit] = for {
  elfGroup <- Group.of(3)
  _ <- List(1,2,3,4,5,6,7,8,9,10).traverse_(n => elf(elfGroup,n))
  reinGroup <- Group.of(9)
  _ <- List(1,2,3,4,5,6,7,8,9).traverse_(n => reindeer(reinGroup, n))
  _ <- santa(elfGroup, reinGroup).foreverM.void
} yield ()

main.timeout(2.seconds).attempt.unsafeRunSync
```

---
layout: home
title:  "Home"
section: "home"
position: 0
---

# Overview

This project aims to extend [Cats Effect](https://typelevel.org/cats-effect/) with
a software transactional memory implementation similar to the Haskell implementation
originally described in [Beautiful Concurrency](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf)
and now implemented in [this package](http://hackage.haskell.org/package/stm).

### Usage

`libraryDependencies += "io.github.timwspence" %% "cats-stm" % "0.8.0"`

You can find more details in the [docs](docs/) but usage looks something like the following:

```scala mdoc
import cats.effect.{ExitCode, IO, IOApp}
import io.github.timwspence.cats.stm.{TVar, STM}
import scala.concurrent.duration._

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    for {
      accountForTim   <- TVar.of[Long](100).atomically[IO]
      accountForSteve <- TVar.of[Long](0).atomically[IO]
      _               <- printBalances(accountForTim, accountForSteve)
      _               <- giveTimMoreMoney(accountForTim).start
      _               <- transfer(accountForTim, accountForSteve)
      _               <- printBalances(accountForTim, accountForSteve)
    } yield ExitCode.Success

  private def transfer(accountForTim: TVar[Long], accountForSteve: TVar[Long]): IO[Unit] =
    STM.atomically[IO] {
      for {
        balance <- accountForTim.get
        _       <- STM.check(balance > 100)
        _       <- accountForTim.modify(_ - 100)
        _       <- accountForSteve.modify(_ + 100)
      } yield ()
    }

  private def giveTimMoreMoney(accountForTim: TVar[Long]): IO[Unit] =
    for {
      _ <- IO.sleep(5000.millis)
      _ <- STM.atomically[IO](accountForTim.modify(_ + 1))
    } yield ()

  private def printBalances(accountForTim: TVar[Long], accountForSteve: TVar[Long]): IO[Unit] =
    for {
      (amountForTim, amountForSteve) <- STM.atomically[IO](for {
        t <- accountForTim.get
        s <- accountForSteve.get
      } yield (t, s))
      _ <- IO(println(s"Tim: $amountForTim"))
      _ <- IO(println(s"Steve: $amountForSteve"))
    } yield ()

}
Main.run(List()).unsafeRunSync
```

### Credits

This software was inspired by [Beautiful Concurrency](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf) and informed by ZIO which has a common origin in that paper via the [stm package](http://hackage.haskell.org/package/stm).

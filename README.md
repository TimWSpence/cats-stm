Cats STM [![CircleCI](https://circleci.com/gh/TimWSpence/cats-stm/tree/master.svg?style=svg)](https://circleci.com/gh/TimWSpence/cats-stm/tree/master)
========

An implementation of STM for [Cats Effect](https://typelevel.org/cats-effect/), inspired by
[Beautiful Concurrency](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf).

Usage
-----

```scala
import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = for {
    accountForTim   <- TVar.make[Long](100).commit[IO]
    accountForSteve <- TVar.make[Long](0).commit[IO]
    _               <- printBalances(accountForTim, accountForSteve)
    _               <- giveTimMoreMoney(accountForTim).start
    _               <- transfer(accountForTim, accountForSteve)
    _               <- printBalances(accountForTim, accountForSteve)
  } yield ExitCode.Success

  private def transfer(accountForTim: TVar[Long], accountForSteve: TVar[Long]): IO[Unit] = for {
    _ <- STM.atomically[IO] {
      for {
        balance <- accountForTim.get
        _       <- STM.check(balance > 100)
        _       <- accountForTim.modify(_ - 100)
        _       <- accountForSteve.modify(_ + 100)
      } yield ()
    }
  } yield ()

  private def giveTimMoreMoney(accountForTim: TVar[Long]): IO[Unit] = for {
    _ <- IO(Thread.sleep(5000))
    _ <- STM.atomically[IO](accountForTim.modify(_ + 1))
  } yield ()

  private def printBalances(accountForTim: TVar[Long], accountForSteve: TVar[Long]): IO[Unit] = for {
    _ <- IO(println(s"Tim: ${accountForTim.value}"))
    _ <- IO(println(s"Steve: ${accountForSteve.value}"))
  } yield ()

}
```

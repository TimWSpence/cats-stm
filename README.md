Cats STM [![CircleCI](https://circleci.com/gh/TimWSpence/cats-stm/tree/master.svg?style=svg)](https://circleci.com/gh/TimWSpence/cats-stm/tree/master)
========

An implementation of Software Transactional Memory for [Cats Effect](https://typelevel.org/cats-effect/), inspired by
[Beautiful Concurrency](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf).


Usage
-----

`libraryDependencies += "com.github.timwspence" %% "cats-stm" % "0.0.1"`

I haven't setup cross-building yet so I'm afraid this is only available on
2.12 for the moment.

The core abstraction is the `TVar` (transactional var), which exposes operations in the
`STM` monad. Once constructed, `STM` actions can be atomically evaluated in the `IO`
monad.

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

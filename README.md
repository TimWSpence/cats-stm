# Cats STM
[![Build Status](https://travis-ci.com/TimWSpence/cats-stm.svg?branch=master)](https://travis-ci.com/TimWSpence/cats-stm) [![Join the chat at https://gitter.im/cats-stm/community](https://badges.gitter.im/cats-stm/community.svg)](https://gitter.im/cats-stm/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

An implementation of Software Transactional Memory for [Cats Effect](https://typelevel.org/cats-effect/), inspired by
[Beautiful Concurrency](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf).

For more information, see the [documentation](https://timwspence.github.io/cats-stm/).


### Usage

`libraryDependencies += "io.github.timwspence" %% "cats-stm" % "0.7.0"`

The core abstraction is the `TVar` (transactional var), which exposes operations in the
`STM` monad. Once constructed, `STM` actions can be atomically evaluated in the `IO`
monad.

```scala
import cats.effect.{ExitCode, IO, IOApp}
import io.github.timwspence.cats.stm.{TVar, STM}
import scala.concurrent.duration._

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = for {
    accountForTim   <- TVar.of[Long](100).commit[IO]
    accountForSteve <- TVar.of[Long](0).commit[IO]
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

  private def giveTimMoreMoney(accountForTim: TVar[Long]): IO[Unit] = for {
    _ <- IO.sleep(5000.millis)
    _ <- STM.atomically[IO](accountForTim.modify(_ + 1))
  } yield ()

  private def printBalances(accountForTim: TVar[Long], accountForSteve: TVar[Long]): IO[Unit] = for {
    amountForTim   <- accountForTim.get.commit[IO]
    _              <- IO(println(s"Tim: $amountForTim"))
    amountForSteve <- accountForSteve.get.commit[IO]
    _              <- IO(println(s"Steve: $amountForSteve"))
  } yield ()

}
```

### Documentation

The documentation is built using [sbt microsites](https://47deg.github.io/sbt-microsites/). You
can generate it via `sbt makeMicrosite`. You can view it locally via `cd docs/target/site && jekyll serve`.

### Credits

This software was inspired by [Beautiful Concurrency](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf) and informed by ZIO which has a common origin in that paper via the [stm package](http://hackage.haskell.org/package/stm).

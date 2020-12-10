<img align="right" src="docs/src/main/resources/microsite/img/impure-logo.png" height="200px" style="padding-left: 20px"/>

# Cats STM
[![Build Status](https://github.com/TimWSpence/cats-stm/workflows/Continuous%20Integration/badge.svg)](https://github.com/TimWSpence/cats-stm/actions?query=workflow%3A%22Continuous+Integration%22)
[![Join the chat at https://gitter.im/cats-stm/community](https://badges.gitter.im/cats-stm/community.svg)](https://gitter.im/cats-stm/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

An implementation of Software Transactional Memory for [Cats Effect](https://typelevel.org/cats-effect/), inspired by
[Beautiful Concurrency](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf).

For more information, see the [documentation](https://timwspence.github.io/cats-stm/).


### Usage

`libraryDependencies += "io.github.timwspence" %% "cats-stm" % "0.8.0"`

The core abstraction is the `TVar` (transactional var), which exposes operations in the
`Txn` monad. Once constructed, `Txn` actions can be atomically evaluated in the `IO`
monad.

Here is a contrived example of what this looks like in practice. We use the
`check` combinator to retry transferring money from Tim and Steve until we have
enough money in Tim's account:

```scala
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
      (amountForTim, amountForSteve) <- stm.commit(for {
        t <- accountForTim.get
        s <- accountForSteve.get
      } yield (t, s))
      _ <- IO(println(s"Tim: $amountForTim"))
      _ <- IO(println(s"Steve: $amountForSteve"))
    } yield ()

}
```

### Documentation

The documentation is built using [docusaurus](https://docusaurus.io/). You can
generate it via `nix-shell --run "sbt docs/docusaurusCreateSite"` . You can view
it via `nix-shell --run "cd website && npm start"`

### Credits

This software was inspired by [Beautiful Concurrency](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf) and the [stm package](http://hackage.haskell.org/package/stm).

Many thanks to [@impurepics](https://twitter.com/impurepics) for the awesome logo!

## Tool Sponsorship

<img width="185px" height="44px" align="right" src="https://www.yourkit.com/images/yklogo.png"/>Development of Cats STM is generously supported in part by [YourKit](https://www.yourkit.com) through the use of their excellent Java profiler.

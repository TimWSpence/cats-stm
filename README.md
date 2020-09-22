# Cats STM
[![Build Status](https://travis-ci.com/TimWSpence/cats-stm.svg?branch=master)](https://travis-ci.com/TimWSpence/cats-stm) [![Join the chat at https://gitter.im/cats-stm/community](https://badges.gitter.im/cats-stm/community.svg)](https://gitter.im/cats-stm/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

An implementation of Software Transactional Memory for [Cats Effect](https://typelevel.org/cats-effect/), inspired by
[Beautiful Concurrency](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf).

For more information, see the [documentation](https://timwspence.github.io/cats-stm/).


### Usage

`libraryDependencies += "io.github.timwspence" %% "cats-stm" % "0.8.0"`

The core abstraction is the `TVar` (transactional var), which exposes operations in the
`STM` monad. Once constructed, `STM` actions can be atomically evaluated in the `IO`
monad.

Here is a contrived example of what this looks like in practice. We use the
`check` combinator to retry transferring money from Tim and Steve until we have
enough money in Tim's account:

```scala
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
```

### Documentation

The documentation is built using [sbt microsites](https://47deg.github.io/sbt-microsites/). You
can generate it via `sbt docs/makeMicrosite`. You can view it locally via `cd docs/target/site && jekyll serve`.

### Credits

This software was inspired by [Beautiful Concurrency](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf) and informed by ZIO which has a common origin in that paper via the [stm package](http://hackage.haskell.org/package/stm).

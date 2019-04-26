package com.github.timwspence.cats.stm

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    for {
      accountForTim   <- TVar.make[Long](100).commit[IO]
      accountForSteve <- TVar.make[Long](0).commit[IO]
      _               <- printBalances(accountForTim, accountForSteve)
      _               <- giveTimMoreMoney(accountForTim).start
      _               <- transfer(accountForTim, accountForSteve)
      _               <- printBalances(accountForTim, accountForSteve)
    } yield ExitCode.Success

  private def transfer(accountForTim: TVar[Long], accountForSteve: TVar[Long]): IO[Unit] =
    for {
      _ <- STM.atomically[IO] {
        for {
          balance <- accountForTim.get
          _       <- STM.check({ println(balance); balance > 100 })
          _       <- accountForTim.modify(_ - 100)
          _       <- accountForSteve.modify(_ + 100)
        } yield ()
      }
    } yield ()

  private def giveTimMoreMoney(accountForTim: TVar[Long]): IO[Unit] =
    for {
      _ <- IO(Thread.sleep(5000))
      _ <- STM.atomically[IO](accountForTim.modify(_ + 1))
    } yield ()

  private def printBalances(accountForTim: TVar[Long], accountForSteve: TVar[Long]): IO[Unit] =
    for {
      _ <- IO(println(s"Tim: ${accountForTim.value}"))
      _ <- IO(println(s"Steve: ${accountForSteve.value}"))
    } yield ()

}

package io.github.timwspence.cats.stm

import cats.implicits._
import cats.effect._

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    for {
      accountForTim   <- TVar.of[Long](100).commit[IO]
      accountForSteve <- TVar.of[Long](0).commit[IO]
      _               <- printBalances(accountForTim, accountForSteve)
      _               <- giveTimMoreMoney(accountForTim).replicateA(15).start
      a               <- transfer(accountForTim, accountForSteve).replicateA(15).start
      // a               <- printBalances(accountForTim, accountForSteve).foreverM.start
      _ <- a.join
      _ <- IO(println("Final balances:"))
      _ <- printBalances(accountForTim, accountForSteve)
    } yield ExitCode.Success

  private def transfer(accountForTim: TVar[Long], accountForSteve: TVar[Long]): IO[Unit] =
    for {
      _ <- STM.atomically[IO] {
        for {
          balance <- accountForTim.get
          _       <- STM.check({ println("Checking balance"); balance > 100 })
          _       <- accountForTim.modify(_ - 100)
          _       <- accountForSteve.modify(_ + 100)
        } yield ()
      }
      _ <- printBalances(accountForTim, accountForSteve)
      _ <- IO(Thread.sleep(5000))
    } yield ()

  private def giveTimMoreMoney(accountForTim: TVar[Long]): IO[Unit] =
    for {
      _ <- IO(Thread.sleep(1000))
      _ <- STM.atomically[IO](accountForTim.modify(_ + 10))
    } yield ()

  private def printBalances(accountForTim: TVar[Long], accountForSteve: TVar[Long]): IO[Unit] =
    for {
      _ <- IO(println(s"Tim: ${accountForTim.value}"))
      _ <- IO(println(s"Steve: ${accountForSteve.value}"))
      _ <- IO(Thread.sleep(5000))
    } yield ()

}

package org.timwspence.cats.stm

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = for {
    accountForTim   <- STM.atomically(TVar.make(100))
    accountForSteve <- STM.atomically(TVar.make(0))
    _               <- IO(println(s"Tim: ${accountForTim.value}"))
    _               <- IO(println(s"Steve: ${accountForSteve.value}"))
    _               <- STM.atomically {
      for {
        balance <- accountForTim.get
        _       <- accountForTim.set(0)
        _       <- accountForSteve.modify(_ + balance)
      } yield ()
    }
    _               <- IO(println(s"Tim: ${accountForTim.value}"))
    _               <- IO(println(s"Steve: ${accountForSteve.value}"))
  } yield ExitCode.Success
}

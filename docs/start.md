---
id: start
title:  "Getting started"
---

```scala
libraryDependencies += "io.github.timwspence" %% "cats-stm" % "0.10.1"
```

### Defining a transaction

Transactions have type `Txn[A]` and are composed of operations on mutable
`TVar` references.

```scala
import cats.effect.IO
import io.github.timwspence.cats.stm._

def wibble(stm: STM[IO])(tvar: stm.TVar[Int]): stm.Txn[Int] = {
  for {
    current <- tvar.get
    updated = current + 1
    _ <- tvar.set(updated)
  } yield updated
}
``` 

`TVar` is effectively parameterized on an effect `F[_]`. We make the API nicer to
work with by making it a dependent type of the STM runtime `STM[F]` (see the
`stm.TVar[Int]` above).

### Committing a transaction

```scala
val run: IO[Int] = {
  def run(stm: STM[IO]): IO[Int] = {
    import stm._
    
    for {
      tvar <- stm.commit(TVar.of[Int](0))
      res  <- stm.commit(wibble(stm)(tvar))
    } yield res
  }
  
  STM.runtime[IO].flatMap(run(_))
}
```

### Retrying a transaction

Retries can be introduced via the `check` combinator. If this transaction is committed
then it will retry the commit until the predicate passed to `check` is satisfied.

```scala

def waitTillValueExceeds100(tvar: TVar[Int]): IO[Int] =
  stm.commit(
    for {
      current <- tvar.get
      _       <- stm.check(current > 100)
    } yield current
  )
```

### Alternatives

The combinator `orElse` introduces an alternative transaction to try in the event
that one retries.

```scala
def transferAvailableFunds(from1: TVar[Int], from2: TVar[Int], to: TVar[Int]): IO[Unit] =
  def transferFrom(from: TVar[Int]): Txn[Unit] =
    for {
      current <- from.get
      _       <- stm.check(current > 100)
      _       <- from.modify(_ - 100)
      _       <- to.modify(_ + 100)
    } yield ()

  stm.commit(
    transferFrom(from1).orElse(transferFrom(from2))
  )
```

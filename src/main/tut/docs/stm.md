---
layout: docs
title: STM
---
# STM

### Overview

`STM` is a monad which describes transactions involving `TVar`s. It is executed via
`STM.atomically`:

```tut
import cats.effect.IO
import io.github.timwspence.cats.stm.{STM, TVar}

val prog: IO[Unit] = for {
  to   <- TVar.make(0).commit[IO]
  from <- TVar.make(100).commit[IO]
  txn  <- STM.atomically[IO] {
    for {
      balance <- from.get
      _       <- from.modify(_ - balance)
      _       <- to.modify(_ + balance)
    } yield ()
  }
} yield ()
```

### Retries

`STM.atomically` supports the concept of retries, which can be introduced via
`STM.check`:

```tut
import cats.effect.IO
import io.github.timwspence.cats.stm.{STM, TVar}

val to   = TVar.make(1).commit[IO].unsafeRunSync
val from = TVar.make(0).commit[IO].unsafeRunSync

val txn  = STM.atomically[IO] {
  for {
    balance <- from.get
    _       <- STM.check(balance > 100)
    _       <- from.modify(_ - 100)
    _       <- to.modify(_ + 100)
  } yield ()
}
```

`txn.unsafeRunSync` will block until the transaction succeeds (or throws an
exception!). Internally, this is implemented by keeping track of which `TVar`s are
involved in a transaction and retrying any pending transactions every time a `TVar`
is committed.

### OrElse

`STM.orElse` is built on top of the retry logic and allows you to attempt an
alternative action if the first retries:

```tut
import cats.effect.IO
import io.github.timwspence.cats.stm.{STM, TVar}

val to   = TVar.make(1).commit[IO].unsafeRunSync
val from = TVar.make(0).commit[IO].unsafeRunSync

val transferHundred: STM[Unit] = for {
  _ <- from.modify(_ - 100)
  _ <- to.modify(_ + 100)
} yield ()

val transferRemaining: STM[Unit] = for {
  balance <- from.get
  _       <- from.modify(_ - balance)
  _       <- to.modify(_ + balance)
} yield ()

val txn  = for {
  _ <- transferHundred.orElse(transferRemaining)
} yield ()
```

### Aborting

Transactions can be aborted via `STM.abort`:

```tut
import cats.effect.IO
import cats.syntax.flatMap._
import io.github.timwspence.cats.stm.{STM, TVar}

val to   = TVar.make(1).commit[IO].unsafeRunSync
val from = TVar.make(0).commit[IO].unsafeRunSync

val txn  = for {
  balance <- from.get
  _       <- if (balance < 100)
               STM.abort(new RuntimeException("Balance must be at least 100"))
             else
               STM.unit
  _ <- from.modify(_ - 100)
  _ <- to.modify(_ + 100)
} yield ()
```

Note that aborting a transaction will not modify any of the `TVar`s involved.

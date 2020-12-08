---
layout: docs
title:  "STM"
number: 3
---

# STM

### Overview

`Txn` is a monad which describes transactions involving `TVar`s. It is executed via
`STM#atomically`:

```scala mdoc:reset
import cats.implicits._
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.github.timwspence.cats.stm.STM

val stm = STM.runtime[IO].unsafeRunSync()
import stm._

val prog: IO[(Int, Int)] = for {
  to   <- stm.commit(TVar.of(0))
  from <- stm.commit(TVar.of(100))
  _  <- stm.commit {
    for {
      balance <- from.get
      _       <- from.modify(_ - balance)
      _       <- to.modify(_ + balance)
    } yield ()
  }
  v   <- stm.commit((to.get, from.get).tupled)
} yield v

val result = prog.unsafeRunSync()
```

### Retries

`STM.atomically` supports the concept of retries, which can be introduced via
`STM.check`:

```scala mdoc:reset
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.github.timwspence.cats.stm.STM

val stm = STM.runtime[IO].unsafeRunSync()
import stm._

val to   = stm.commit(TVar.of(1)).unsafeRunSync()
val from = stm.commit(TVar.of(0)).unsafeRunSync()

val txn: IO[Unit]  = stm.commit {
  for {
    balance <- from.get
    _       <- stm.check(balance > 100)
    _       <- from.modify(_ - 100)
    _       <- to.modify(_ + 100)
  } yield ()
}
```

`txn.unsafeRunSync()` will block until the transaction succeeds (or throws an
exception!). Internally, this is implemented by keeping track of which `TVar`s are
involved in a transaction and retrying any pending transactions every time a `TVar`
is committed.

### OrElse

`STM.orElse` is built on top of the retry logic and allows you to attempt an
alternative action if the first retries:

```scala mdoc:reset
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.github.timwspence.cats.stm.STM

val stm = STM.runtime[IO].unsafeRunSync()
import stm._

val to   = stm.commit(TVar.of(1)).unsafeRunSync()
val from = stm.commit(TVar.of(0)).unsafeRunSync()

val transferHundred: Txn[Unit] = for {
  b <- from.get
  _ <- stm.check(b > 100)
  _ <- from.modify(_ - 100)
  _ <- to.modify(_ + 100)
} yield ()

val transferRemaining: Txn[Unit] = for {
  balance <- from.get
  _       <- from.modify(_ - balance)
  _       <- to.modify(_ + balance)
} yield ()

val txn  = for {
  _    <- transferHundred.orElse(transferRemaining)
  f    <- from.get
  t    <- to.get
} yield f -> t

val result = stm.commit(txn).unsafeRunSync()
```

### Aborting

Transactions can be aborted via `STM.abort`:

```scala mdoc:reset
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.github.timwspence.cats.stm.STM

val stm = STM.runtime[IO].unsafeRunSync()
import stm._

val to   = stm.commit(TVar.of(1)).unsafeRunSync()
val from = stm.commit(TVar.of(0)).unsafeRunSync()

val txn  = for {
  balance <- from.get
  _       <- if (balance < 100)
               stm.abort(new RuntimeException("Balance must be at least 100"))
             else
               stm.unit
  _ <- from.modify(_ - 100)
  _ <- to.modify(_ + 100)
} yield ()

val result = stm.commit(txn).attempt.unsafeRunSync()
```

Note that aborting a transaction will not modify any of the `TVar`s involved.

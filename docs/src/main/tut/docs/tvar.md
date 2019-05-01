---
layout: docs
title: TVar
---
# TVar

A `TVar` (transactional variable) is a mutable memory location that can be be
read and modified via `STM` actions.

```tut
import cats.effect.IO
import io.github.timwspence.cats.stm._

val to   = TVar.of(0).commit[IO].unsafeRunSync
val from = TVar.of(100).commit[IO].unsafeRunSync
val txn: STM[Unit] = for {
  balance <- from.get
  _       <- from.modify(_ - balance)
  _       <- to.modify(_ + balance)
} yield ()
```

Note that this does not modify either `from` or `to`!! It merely describes a
transaction which must be executed via `STM.atomically`

---
layout: docs
title:  "TVar"
number: 4
---

# TVar

A `TVar` (transactional variable) is a mutable memory location that can be be
read and modified via `STM` actions.

```tut:book
import cats.effect.IO

import io.github.timwspence.cats.stm._

val to   = TVar.of(0).commit[IO].unsafeRunSync
val from = TVar.of(100).commit[IO].unsafeRunSync
val txn: STM[(Int, Int)] = for {
  balance <- from.get
  _       <- from.modify(_ - balance)
  _       <- to.modify(_ + balance)
  res1    <- from.get
  res2    <- to.get
} yield res1 -> res2

val result = txn.commit[IO].unsafeRunSync
```

Note that this does not modify either `from` or `to`!! It merely describes a
transaction which must be executed via `STM.atomically`

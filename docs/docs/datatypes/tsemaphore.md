---
layout: docs
title:  "TSemaphore"
number: 6
---

# TSemaphore

A convenience implementation of a semaphore in the `STM` monad, built on top of
[`TVar`](tvar.html).

```scala mdoc
import cats.effect.{IO, ContextShift}

import io.github.timwspence.cats.stm.{STM, TSemaphore}

import scala.concurrent.ExecutionContext.global

implicit val CS: ContextShift[IO] = IO.contextShift(global)

val txn: STM[Long] = for {
  tsem  <- TSemaphore.make(1)
  _     <- tsem.acquire
  zero  <- tsem.available
  _     <- tsem.release
} yield zero

val result = txn.atomically[IO].unsafeRunSync
```

---
layout: docs
title:  "TSemaphore"
number: 6
---

# TSemaphore

A convenience implementation of a semaphore in the `STM` monad, built on top of
[`TVar`](tvar.html).

```scala mdoc
import cats.effect.IO

import io.github.timwspence.cats.stm.{STM, TSemaphore}

val txn: STM[Long] = for {
  tsem  <- TSemaphore.make(1)
  _     <- tsem.acquire
  zero  <- tsem.available
  _     <- tsem.release
} yield zero

val result = txn.atomically[IO].unsafeRunSync
```

---
id: tsemaphore
title:  "TSemaphore"
---

A convenience implementation of a semaphore in the `STM` monad, built on top of
[`TVar`](../theory/tvar.md).

```scala mdoc
import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.github.timwspence.cats.stm.STM

val stm = STM.runtime[IO].unsafeRunSync()
import stm._

val txn: Txn[Long] = for {
  tsem  <- TSemaphore.make(1)
  _     <- tsem.acquire
  zero  <- tsem.available
  _     <- tsem.release
} yield zero

val result = stm.commit(txn).unsafeRunSync()
```

---
layout: docs
title: TSem
---
# TSemaphore

A convenience implementation of a semaphore in the `STM` monad, built on top of
[`TVar`](tmar.html).

```tut
import cats.syntax.semigroup._
import cats.instances.string._

import io.github.timwspence.cats.stm.{STM, TSemaphore}

val txn: STM[Long] = for {
  tsem  <- TSemaphore.make(1)
  _     <- tsem.acquire
  zero  <- tsem.available
  _     <- tsem.release
} yield zero
```

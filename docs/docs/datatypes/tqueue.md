---
layout: docs
title:  "TQueue"
number: 5
---

# TQueue

A convenience implementation of a queue in the `STM` monad, built on top of
[`TVar`](tvar.html).

```scala mdoc
import cats.effect.IO
import cats.syntax.semigroup._
import cats.instances.string._

import io.github.timwspence.cats.stm.{STM, TQueue}

val txn: STM[String] = for {
  tqueue <- TQueue.empty[String]
  _      <- tqueue.put("hello")
  _      <- tqueue.put("world")
  hello  <- tqueue.read
  world  <- tqueue.peek
} yield hello |+| world

val result = txn.atomically[IO].unsafeRunSync
```

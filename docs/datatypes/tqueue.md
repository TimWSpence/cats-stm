---
id: tqueue
title:  "TQueue"
---

A convenience implementation of a queue in the `STM` monad, built on top of
[`TVar`](../theory/tvar.md).

```scala mdoc
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.semigroup._
import cats.instances.string._

import io.github.timwspence.cats.stm.STM

val stm = STM.runtime[IO].unsafeRunSync()
import stm._

val txn: Txn[String] = for {
  tqueue <- TQueue.empty[String]
  _      <- tqueue.put("hello")
  _      <- tqueue.put("world")
  hello  <- tqueue.read
  world  <- tqueue.peek
} yield hello |+| world

val result = stm.commit(txn).unsafeRunSync()
```

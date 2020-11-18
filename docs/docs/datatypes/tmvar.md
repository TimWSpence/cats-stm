---
layout: docs
title:  "TMVar"
number: 6
---

# TMVar

A convenience implementation built on top of [`TVar`](tmvar.html), which provides
similar semantics to Cats Effect [`MVar`](https://typelevel.org/cats-effect/concurrency/mvar.html).

You can think of this as a mutable memory location that may contain a value.
Writes will block if full and reads will block if empty.

```scala mdoc
import cats.effect.{ContextShift, IO}
import cats.syntax.semigroup._
import cats.instances.string._
import scala.concurrent.ExecutionContext.global

import io.github.timwspence.cats.stm.{STM, TMVar}

implicit val CS: ContextShift[IO] = IO.contextShift(global)

val txn: STM[String] = for {
  tmvar     <- TMVar.empty[String]
  _         <- tmvar.put("Hello")   //Would block if full
  hello     <- tmvar.take           //Would block if empty
  _         <- tmvar.put("world")   //Would block if full
  world     <- tmvar.read           //Would block if empty.
} yield hello |+| world

val result = txn.atomically[IO].unsafeRunSync()
```

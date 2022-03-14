---
id: tmvar
title:  "TMVar"
---

A convenience implementation built on top of [`TVar`](tvar.md), which provides
similar semantics to Cats Effect [`MVar`](https://typelevel.org/cats-effect/concurrency/mvar.html).

You can think of this as a mutable memory location that may contain a value.
Writes will block if full and reads will block if empty.

```scala mdoc
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.semigroup._
import cats.instances.string._

import io.github.timwspence.cats.stm.STM

val stm = STM.runtime[IO].unsafeRunSync()
import stm._

val txn: Txn[String] = for {
  tmvar     <- TMVar.empty[String]
  _         <- tmvar.put("Hello")   //Would block if full
  hello     <- tmvar.take           //Would block if empty
  _         <- tmvar.put("world")   //Would block if full
  world     <- tmvar.read           //Would block if empty.
} yield hello |+| world

val result = stm.commit(txn).unsafeRunSync()
```

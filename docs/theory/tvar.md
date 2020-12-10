---
id: tvar
title:  "TVar"
---

A `TVar` (transactional variable) is a mutable memory location that can be be
read and modified via `STM` actions.

```scala mdoc
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.github.timwspence.cats.stm.STM

val stm = STM.runtime[IO].unsafeRunSync()
import stm._

val to   = stm.commit(TVar.of(1)).unsafeRunSync()
val from = stm.commit(TVar.of(0)).unsafeRunSync()

val txn: Txn[(Int, Int)] = for {
  balance <- from.get
  _       <- from.modify(_ - balance)
  _       <- to.modify(_ + balance)
  res1    <- from.get
  res2    <- to.get
} yield res1 -> res2

val result = stm.commit(txn).unsafeRunSync()
```

Note that this does not modify either `from` or `to`!! It merely describes a
transaction which must be executed via `STM#commit`

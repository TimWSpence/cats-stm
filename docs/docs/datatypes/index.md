---
layout: docs
title:  "Data Types"
position: 2
---
## Introduction

### The problem

Concurrency is *hard* and so are locks. Consider modelling the transfer of
balances between bank accounts. You might end up with something like this:

```scala mdoc:silent
case class Account(private var balance: Long) {
  def get = this.synchronized {
    balance
  }
  def modify(f: Long => Long) = this.synchronized {
    balance = f(balance)
  }
}

def transfer(from: Account, to: Account, amount: Long): Unit = {
  from.synchronized {
    to.synchronized {
      from.modify(_ - 100)
      to.modify(_ + 100)
    }
  }
}
```

There are a lot of problems with this. Firstly, locks are difficult to reason about
and acquiring them is manual and hence prone to any of the following problems:
 * Taking too few locks - leaves you prone to concurrency bugs
 * Taking too many locks - inhibits concurrency and can cause deadlock
 * Taking the wrong locks - the programmer is responsible for taking the correct lock
 * Taking locks in the wrong order - can cause deadlock

In addition, this approach requires manual recovery of errors and handling of retries.
Suppose in the above example, we wanted to retry the transaction until `from` had a
balance of at least 100? Perhaps not that difficult but if the transfer was nested
in the middle of a larger transaction which had already acquired locks and modified
state then it would rapidly become very difficult indeed!

### A solution

A solution to this is to abstract mutable state behind a `TVar` which exposes
operations in an `STM` monad. These operations can be composed in the `STM`
monad and then atomically evaluated as an `IO` action.

Why does this solve the problem?

Separating the definition of the transaction from its execution means that we can
execute the transaction against a log and only commit the final states to the `TVar`s
if the whole transaction succeeds. It also allows us to transparently handle retries
as we do not modify the `TVar` state until we are sure that the transaction has succeeded.

Note that transactions having the type `STM[A]` also prohibits performing `IO` actions
during a transaction. This is important as the transaction may be retried multiple
times before it succeeds and hence the evaluation would not be predictable or
referentially transparent if it could perform `IO`.

For a fuller explanation of the above, I would strongly encourage you to read
[the paper](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf)!

---
id: intro
title:  "Introduction"
---

### The problem

Reasoning about mutable state in the presence of concurrency is *hard* and
reasoning about the traditional solution (locks/mutexes) is even harder.
Consider the problem of transferring balances between bank accounts. The lock-based
solution would look something like this:

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
  //Acquire an exclusive lock on from
  from.synchronized {
    //Acquire an exclusive lock on to
    to.synchronized {
      from.modify(_ - amount)
      to.modify(_ + amount)
    }
  }
}
```

This kind of lock-based atomicity is prone to the following problems:
 * Taking too few locks - leaves you prone to concurrency bugs
 * Taking too many locks - inhibits concurrency and can cause deadlock
 * Taking the wrong locks - the programmer is responsible for taking the correct lock
 * Taking locks in the wrong order - can cause deadlock (see the [Dining Philosophers](https://en.wikipedia.org/wiki/Dining_philosophers_problem))
 * Pessimistic - must take all the locks you _might_ need
 * Not releasing locks upon cancellation/error - can cause deadlock
 * Retrying and waiting for conditions is tricky and manual

### Composition

Not only are locks tricky, manual and error-prone, they also *do not compose*.
Suppose we have the following functions:
```scala
def foo: Unit = ???
def bar: Unit = ???
```

Assume that they each acquire some locks to mutate some state atomically.

Now suppose that we want to perform the composition `foo >>> bar` atomically. How would we do that?

The steps involved would look something like this:
 - Read the _entire_ callgraph of `foo` and `bar` to determine what locks they acquire
 - Hope that there is a well-defined ordering on these to avoid dining philosopher-style problems
 - Hope that the locks are re-entrant (can be acquired multiple times)
 - Acquire all the locks in the correct order
 - Perform `foo >>> bar`
 - Ensure we release all locks correctly, even in the presence of errors/cancellation
 - Discover that the business requirements have changed and we now need to execute
   `foo >>> baz` atomically instead
 - Cry

### A solution

We need a composable abstraction for expressing the notion of a transaction on mutable
memory. As functional programmers, we are extremely familiar with such a concept - monads!

As with most problems in functional programming, the solution is to define a monadic
datatype that solves the problem and then find a way to interpret it.

In this case, that means we end up with something like this:

```scala
trait STM[F[_]] {
  trait TVar[A] {
    def get: Txn[A]
    
    def modify(f: A => A): Txn[Unit]
  }
  
  trait Txn[A] {
    def flatMap[B](f: A => Txn[B]): Txn[B]
  }
  
  def commit[A](txn: Txn[A]): F[A]
}

//Atomically transfer the contents of from to to
def example(stm: STM[IO]): IO[Unit] = {
  import stm._
  for {
    from <- stm.commit(TVar.of(100))
    to <- stm.commit(TVar.of(0))
    _ <- stm.commit(
      for {
        curr <- from.get
        _ <- from.set(0)
        _ <- to.set(curr)
      } yield ()
    )
  } yield ()
}
```

In other words, we introduce a new type `TVar` of mutable variables, whose operations
(get/set/modify) are suspended in the `Txn` monad. We can compose these to form larger
transactions such as 

```scala
val transfer: Txn[Unit] = for {
  curr <- from.get
  _ <- from.set(0)
  _ <- to.set(curr)
} yield ()
```

which can be executed atomically using `stm.commit`

Why does this solve the problem?

Separating the definition of the transaction from its execution means that we
can execute the transaction against a log (similar to a database) and only
commit the final states to the `TVar`s if the whole transaction succeeds. It
also allows us to transparently handle retries as we do not modify the `TVar`
state until we are sure that the transaction has succeeded.

Note that transactions having the type `Txn[A]` also prohibits performing `IO` actions
during a transaction. This is important as the transaction may be retried multiple
times before it succeeds and hence the evaluation would not be predictable or
referentially transparent if it could perform `IO`.

For a fuller explanation of the above, I would strongly encourage you to read
[the paper](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/beautiful.pdf)!

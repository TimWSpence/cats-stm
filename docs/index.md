<img align="right" src="static/logo.png" height="200px" style="padding-left: 20px"/>

# Cats STM

[![Build Status](https://github.com/TimWSpence/cats-stm/workflows/Continuous%20Integration/badge.svg)](https://github.com/TimWSpence/cats-stm/actions?query=workflow%3A%22Continuous+Integration%22)
[![Latest version](https://index.scala-lang.org/timwspence/cats-stm/cats-stm/latest.svg?color=orange)](https://index.scala-lang.org/timwspence/cats-stm/cats-stm)
[![Discord](https://img.shields.io/discord/632277896739946517.svg?label=&logo=discord&logoColor=ffffff&color=404244&labelColor=6A7EC2)](https://discord.gg/QNnHKHq5Ts)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

Cats STM is a library for writing composable in-memory transactions which will handling correct locking, optimistic concurrency and automatic retries for you. It is intended as a safe, composable alternative to locks and semaphores that also allows a higher level of concurrency.

### Transactional and Safe

Write `Txn` expressions in terms of mutable `TVar`s (analogous to Ref from Cats Effect). Run `Txn[A]` expressions transactionally to obtain an `IO[A]` (or `F[A]` for the `Async[F]` of your choice). The `STM` runtime will determine what locks need to be acquired and in what order to avoid deadlock.

### Composable

Trivially compose transactional expressions into larger ones without explicit locking or requiring any knowledge of the internals of the subexpressions and what locks they require.

### Automatic retries

Specify pre-conditions and the committing of a transaction will be automatically retried until the pre-conditions are satisifed.

### Fine-grained, optimistic concurrency

There are no global locks and per-`TVar` locks are acquired only when a transaction has succeeded and should be committed.

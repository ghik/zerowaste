# Zerowaste

Scala 2 compiler plugin to detect unused expressions (non-`Unit`).

## Introduction

In purely functional programming paradigm, all expressions are _pure_ - their evaluation does not cause side effects. 
Side effects are therefore expressed with an `IO`-like type (e.g. Cats Effect `IO`) and they only actually happen when 
the `IO` is run - preferably with a single, "impure" invocation hidden somewhere in library code.

Because of that, in pure FP it never makes sense to discard the result of an expression, e.g.

```scala
val number = {
  discardedExpression // pointless!
  42
}
```

This is a very easy mistake to make which may be the cause of very tricky bugs, e.g. when the discarded expression
is an `IO` that was supposed to do something important. The Scala compiler does not detect this mistake because Scala 
is not a purely functional language - it can rarely be sure that expressions are actually pure.

This plugin fixes that by reporting a warning for all discarded expressions whose type is anything other than `Unit`.

## Usage

Enable the plugin in `build.sbt`:

```scala
libraryDependencies += compilerPlugin("com.github.ghik" % "zerowaste" % "<version>" cross CrossVersion.full)
```

The plugin issues warnings, but it is often a good idea to turn them into compilation errors:

```scala
scalacOptions += "-Werror"
```

Note that such warnings, despite being converted to errors, can be still suppressed with the `@nowarn` annotation:

```scala
import scala.annotation.nowarn

val number = {
  discardedExpression: @nowarn("msg=discarded expression")
  42
}
```

## Compatibility

Zerowaste is currently available for Scala 2.12 and 2.13

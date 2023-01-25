# Zerowaste

Scala compiler plugin to detect unused expressions (non-`Unit`).

Zerowaste is currently available for Scala 2.12.17+, 2.13.10+ and 3.2.1+

Compiler plugins must be cross-built for every minor and patch version of Scala. If `zerowaste` is not available for a Scala version that you want to use (most likely some freshly released one), please file an issue or submit a PR.

### How to submit a PR with a new Scala version

1. Add your desired Scala version to `crossScalaVersions` in `build.sbt`
1. Run `sbt githubWorkflowGenerate`
1. Commit the changes in `build.sbt` and github workflows
1. Submit a PR

## Introduction

In purely functional programming, expressions are always _pure_ - they evaluate with no side effects.
Side effects are therefore expressed with an `IO`-like type (e.g. Cats Effect `IO`) and they only happen when
the `IO` is run - preferably with a single, "impure" invocation hidden somewhere in library code.

Because of that, it does not make sense to ever discard a result of an expression in purely functional code, e.g.

```scala
val number = {
  discardedExpression // pointless!
  42
}
```

This is a very easy mistake to make. It may become a cause of some very tricky bugs, e.g. when the discarded expression
is an `IO` that was supposed to do something important. Scala compiler does not detect these because Scala
is not a purely functional language so the compiler cannot assume that all expressions are pure.

This plugin addresses this problem by reporting a warning for every discarded expression whose type is different than `Unit`.

## Usage

Enable the plugin in `build.sbt`:

```scala
libraryDependencies += compilerPlugin("com.github.ghik" % "zerowaste" % "<version>" cross CrossVersion.full)
```

The plugin issues warnings, but it is often a good idea to turn them into compilation errors:

```scala
scalacOptions += "-Werror"
```

Note that these warnings, despite being converted to errors, can be suppressed with the `@nowarn` annotation:

```scala
import scala.annotation.nowarn

val number = {
  discardedExpression: @nowarn("msg=discarded expression")
  42
}
```

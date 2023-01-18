# Zerowaste

Scala compiler plugin to detect unused expressions (non-`Unit`).

## Introduction

In purely functional programming, expressions are always _pure_ - their evaluation has no side effects.
Side effects are therefore expressed with an `IO`-like type (e.g. Cats Effect `IO`) and they only happen when
the `IO` is run - preferably with a single, "impure" invocation hidden somewhere in library code.

Because of that, in pure FP it never makes sense to discard the result of an expression, e.g.

```scala
val number = {
  discardedExpression // pointless!
  42
}
```

This is an easy mistake to make and it may cause very tricky bugs, e.g. when the discarded expression
was an `IO` that did something important. The Scala compiler does not detect this kind of mistake because Scala
is not a purely functional language so the compiler cannot assume that all expressions are pure.

This plugin reports a warning for any discarded expression whose type is not `Unit`.

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

## Compatibility

Zerowaste is currently available for Scala 2.12.17+, 2.13.10+ and 3.2.1+

Compiler plugins must be cross-built for every minor and patch version of Scala. If `zerowaste` is not available for your Scala version, please file an issue or submit a PR.

### How to submit a PR with new Scala version

#. Add your desired Scala version to `crossScalaVersions` in `build.sbt`
#. Run `sbt githubWorkflowGenerate`
#. Commit the changes in `build.sbt` and github workflows
#. Submit a PR

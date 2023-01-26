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

Pure functional programming operates under the principle that expressions are free from side-effects.
Side-effects are instead handled through an IO-like type, such as Cats Effect's IO, and are only executed upon explicit,
unsafe `runX` invocation, usually hidden somewhere in library code.

As a consequence, discarding a result of an expression in purely functional code can always be assumed a mistake, e.g.

```scala
val number = {
  discardedExpression // pointless!
  42
}
```

This is an easy mistake and it can lead to tricky bugs, such as when an important IO action is unintentionally discarded. 
The Scala compiler cannot detect this issue as Scala is not a purely functional language and cannot assume all expressions are pure.

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

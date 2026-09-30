# Zerowaste

Scala compiler plugin to detect unused expressions (non-`Unit`).

Zerowaste is currently available for Scala 2.12.17+, 2.13.10+, 3.2.1+, 3.3.0+, and 3.4.0+

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

As a consequence, discarding a result of an expression in purely functional code can always be assumed to be a mistake, e.g.

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

## Custom discardable types

By default, only expressions of type `Unit` may be discarded. You can extend this to other types
(e.g. ScalaTest's `Assertion`, or your own `Done`/`Ack`-like types) by listing them in a file:

```
# zerowaste-discardable.txt
# One type per line. Blank lines and lines starting with `#` or `//` are ignored.
org.scalatest.Assertion
com.example.Ack
com.example.Result[Unit]
```

and passing that file to the plugin with the `discardable` option:

```scala
scalacOptions += s"-P:zerowaste:discardable=${baseDirectory.value / "zerowaste-discardable.txt"}"
```

Every type is written using regular Scala type syntax and is resolved as if it appeared in a Scala source file
without a package declaration. This means that fully qualified names must be used, except for names available
through root imports (`scala._`, `java.lang._` and `scala.Predef._`), e.g. `Option[Unit]`.
Types may refer to classes on the compilation classpath as well as classes defined in the sources being compiled.

Discardability is checked with a subtype test, so every subtype of a listed type is also discardable
(e.g. listing `Option[Unit]` also makes `Some[Unit]` and `None.type` discardable). Types which are not resolvable,
or which are not fully applied (e.g. `Option` instead of `Option[Unit]`), are reported as compilation errors.

Multiple files can be combined, either by separating their paths with the platform path separator
(`:` on Unix, `;` on Windows, i.e. `java.io.File.pathSeparator`) or by repeating the option:

```scala
scalacOptions += s"-P:zerowaste:discardable=${file("common.txt")}${java.io.File.pathSeparator}${file("project-specific.txt")}"
// or
scalacOptions ++= Seq("-P:zerowaste:discardable=common.txt", "-P:zerowaste:discardable=project-specific.txt")
```

Note that file paths must not contain a comma, because the compiler splits plugin options on commas.


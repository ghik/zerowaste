package com.github.ghik.zerowaste

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.util.control.NonFatal

/**
 * A single type listed in a "discardable types" file, together with its location (for error reporting).
 */
final case class DiscardableTypeSpec(file: String, line: Int, code: String) {
  def location: String = s"$file:$line"
}

/**
 * Parsed plugin options (`-P:zerowaste:...`), shared between Scala 2 and Scala 3 implementations.
 *
 * Supported options:
 *  - `discardable=<path>` - a file with a list of Scala types (one per line) whose values may be discarded,
 *    just like values of type `Unit`. Blank lines and lines starting with `#` or `//` are ignored.
 *    Types are resolved as if they were written in a Scala source file without any package declaration,
 *    i.e. only fully qualified names and names available through root imports (`scala._`, `java.lang._`,
 *    `scala.Predef._`) can be used. Every subtype of a discardable type is also discardable.
 *    Multiple files may be specified by separating paths with the platform path separator
 *    (`:` on Unix, `;` on Windows) or by repeating the option.
 */
final case class ZerowasteOptions(discardableTypes: List[DiscardableTypeSpec], errors: List[String])

object ZerowasteOptions {
  final val DiscardableOption = "discardable"

  def parse(options: List[String]): ZerowasteOptions = {
    val specs = List.newBuilder[DiscardableTypeSpec]
    val errors = List.newBuilder[String]
    options.foreach { opt =>
      val eqIdx = opt.indexOf('=')
      val (key, value) = if (eqIdx < 0) (opt, "") else (opt.substring(0, eqIdx), opt.substring(eqIdx + 1))
      key match {
        case DiscardableOption if value.nonEmpty =>
          value.split(File.pathSeparator).iterator.map(_.trim).filter(_.nonEmpty).foreach { path =>
            try specs ++= readDiscardableTypes(path) catch {
              case NonFatal(e) => errors += s"could not read discardable types file $path: $e"
            }
          }
        case DiscardableOption =>
          errors += s"option '$DiscardableOption' requires a file path: $DiscardableOption=<path>"
        case _ =>
          errors += s"unknown option: $opt"
      }
    }
    ZerowasteOptions(specs.result(), errors.result())
  }

  private def readDiscardableTypes(path: String): List[DiscardableTypeSpec] = {
    val content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
    content.split("\r\n|\r|\n", -1).toList.zipWithIndex.flatMap { case (rawLine, idx) =>
      val line = rawLine.trim
      if (line.isEmpty || line.startsWith("#") || line.startsWith("//")) Nil
      else List(DiscardableTypeSpec(path, idx + 1, line))
    }
  }
}

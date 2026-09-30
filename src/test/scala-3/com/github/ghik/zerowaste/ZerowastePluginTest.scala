package com.github.ghik.zerowaste

import dotty.tools.dotc.Compiler
import dotty.tools.dotc.config.Settings.Setting
import dotty.tools.dotc.core.Contexts.*
import dotty.tools.dotc.plugins.Plugin
import dotty.tools.io.{Path, PlainFile}
import org.scalactic.source.Position
import org.scalatest.funsuite.AnyFunSuite

class ZerowastePluginTest extends AnyFunSuite {
  val compiler = new Compiler

  def testFile(
    filename: String,
    expectedWarnings: Int = 0,
    expectedErrors: Int = 0,
    pluginOptions: List[String] = Nil,
  )(using Position): Unit = {
    val ctxBase = new ContextBase {
      override protected def loadRoughPluginsList(using Context): List[Plugin] =
        new ZerowastePlugin :: Nil
    }
    given ctx: Context = ctxBase.initialCtx

    // scala < 3.8.0 has `-usejavacp` and >= 3.8.0 has `-Yusejavacp`
    val setting = ctx.settings.allSettings.find(s => List("-Yusejavacp", "-usejavacp").contains(s.name))
    setting.foreach(_.asInstanceOf[Setting[Boolean]].update(true))
    ctx.settings.pluginOptions.update(pluginOptions.map(opt => s"zerowaste:$opt"))

    val run = compiler.newRun
    run.compile(List(PlainFile(Path(s"testdata/$filename"))))
    assert(ctx.reporter.errorCount == expectedErrors)
    assert(ctx.reporter.warningCount == expectedWarnings)
  }

  test("zerowaste") {
    testFile("zerowaste.scala", 14)
  }

  test("Cats Effect IO") {
    testFile("catsio.scala", 1)
  }

  test("custom discardable types") {
    testFile("discardable.scala", 4, pluginOptions = List("discardable=testdata/discardable-types.txt"))
  }

  test("custom discardable types from multiple files (repeated option)") {
    testFile("discardable.scala", 3, pluginOptions = List(
      "discardable=testdata/discardable-types.txt",
      "discardable=testdata/discardable-types-extra.txt",
    ))
  }

  test("custom discardable types from multiple files (path separator)") {
    val paths = List("testdata/discardable-types.txt", "testdata/discardable-types-extra.txt").mkString(java.io.File.pathSeparator)
    testFile("discardable.scala", 3, pluginOptions = List(s"discardable=$paths"))
  }

  test("custom discardable types not configured") {
    testFile("discardable.scala", 14)
  }

  test("invalid discardable types") {
    testFile("discardable.scala", 14, expectedErrors = 4, pluginOptions = List("discardable=testdata/discardable-types-bad.txt"))
  }

  test("missing discardable types file") {
    testFile("discardable.scala", 14, expectedErrors = 1, pluginOptions = List("discardable=testdata/nonexistent.txt"))
  }

  test("unknown plugin option") {
    testFile("discardable.scala", 14, expectedErrors = 1, pluginOptions = List("foo=bar"))
  }
}

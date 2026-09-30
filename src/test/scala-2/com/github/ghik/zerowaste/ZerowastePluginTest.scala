package com.github.ghik.zerowaste

import org.scalactic.source.Position
import org.scalatest.funsuite.AnyFunSuite

import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{Global, Settings}

class ZerowastePluginTest extends AnyFunSuite {
  def testFile(
    filename: String,
    expectedWarnings: Int = 0,
    expectedErrors: Int = 0,
    pluginOptions: List[String] = Nil,
  )(implicit pos: Position): Unit = {
    val settings = new Settings
    settings.usejavacp.value = true
    settings.pluginOptions.value = pluginOptions.map(opt => s"zerowaste:$opt")

    // avoid saving classfiles to disk
    val outDir = new VirtualDirectory("(memory)", None)
    settings.outputDirs.setSingleOutput(outDir)
    val reporter = new ConsoleReporter(settings)

    val global: Global = new Global(settings, reporter) {
      override protected def loadRoughPluginsList(): List[Plugin] =
        new ZerowastePlugin(this) :: super.loadRoughPluginsList()
    }

    val run = new global.Run
    run.compile(List(s"testdata/$filename"))
    assert(reporter.errorCount == expectedErrors)
    assert(reporter.warningCount == expectedWarnings)
  }

  test("zerowaste") {
    testFile("zerowaste.scala", 14)
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

  // option errors are reported when plugins are loaded, before any compiler phase runs,
  // so Scala 2 aborts the compilation immediately and no warnings are emitted

  test("missing discardable types file") {
    testFile("discardable.scala", 0, expectedErrors = 1, pluginOptions = List("discardable=testdata/nonexistent.txt"))
  }

  test("unknown plugin option") {
    testFile("discardable.scala", 0, expectedErrors = 1, pluginOptions = List("foo=bar"))
  }
}

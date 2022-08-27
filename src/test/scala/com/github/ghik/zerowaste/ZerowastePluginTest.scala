package com.github.ghik.zerowaste

import org.scalactic.source.Position
import org.scalatest.funsuite.AnyFunSuite

import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{Global, Settings}

class ZerowastePluginTest extends AnyFunSuite {
  val settings = new Settings
  settings.usejavacp.value = true

  // avoid saving classfiles to disk
  val outDir = new VirtualDirectory("(memory)", None)
  settings.outputDirs.setSingleOutput(outDir)
  val reporter = new ConsoleReporter(settings)

  val global: Global = new Global(settings, reporter) {
    override protected def loadRoughPluginsList(): List[Plugin] =
      new ZerowastePlugin(this) :: super.loadRoughPluginsList()
  }

  def compile(filenames: String*): Unit = {
    reporter.reset()
    val run = new global.Run
    run.compile(filenames.toList.map(f => s"testdata/$f"))
  }

  def assertWarnings(count: Int)(implicit pos: Position): Unit =
    assert(reporter.warningCount == count)

  def testFile(filename: String, expectedWarnings: Int = 0)(implicit pos: Position): Unit = {
    compile(filename)
    assertWarnings(expectedWarnings)
  }

  test("zerowaste") {
    testFile("zerowaste.scala", 5)
  }
}

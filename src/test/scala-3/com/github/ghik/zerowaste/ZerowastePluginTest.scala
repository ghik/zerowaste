package com.github.ghik.zerowaste

import dotty.tools.dotc.Compiler
import dotty.tools.dotc.core.Contexts.*
import dotty.tools.dotc.plugins.Plugin
import dotty.tools.io.{Path, PlainFile}
import org.scalactic.source.Position
import org.scalatest.funsuite.AnyFunSuite

class ZerowastePluginTest extends AnyFunSuite {
  val compiler = new Compiler

  def testFile(filename: String, expectedWarnings: Int = 0)(using Position): Unit = {
    val ctxBase = new ContextBase {
      override protected def loadRoughPluginsList(using Context): List[Plugin] =
        new ZerowastePlugin :: Nil
    }
    given ctx: Context = ctxBase.initialCtx
    ctx.settings.usejavacp.update(true)

    val run = compiler.newRun
    run.compile(List(PlainFile(Path(s"testdata/$filename"))))
    assert(ctx.reporter.warningCount == expectedWarnings)
  }

  test("zerowaste") {
    testFile("zerowaste.scala", 14)
  }

  test("Cats Effect IO") {
    testFile("catsio.scala", 1)
  }
}

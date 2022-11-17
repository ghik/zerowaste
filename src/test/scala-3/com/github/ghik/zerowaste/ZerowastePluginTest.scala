package com.github.ghik.zerowaste

import org.scalatest.funsuite.AnyFunSuite
import dotty.tools.dotc.Compiler
import dotty.tools.dotc.core.Contexts.*
import dotty.tools.dotc.plugins.Plugin
import dotty.tools.io.{Path, PlainFile}
import org.scalactic.source.Position

class ZerowastePluginTest extends AnyFunSuite {
  val compiler = new Compiler

  def testFile(filename: String, expectedWarnings: Int = 0)(implicit pos: Position): Unit = {
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
}

package com.github.ghik.zerowaste

import scala.tools.nsc.Reporting.WarningCategory
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}

final class ZerowastePlugin(val global: Global) extends Plugin { plugin =>
  import global._

  val name = "zerowaste"
  val description = "Scala compiler plugin that disallows discarding of non-Unit expressions"
  val components: List[PluginComponent] = List(component)

  private object component extends PluginComponent {
    val global: plugin.global.type = plugin.global
    val runsAfter: List[String] = List("typer")
    override val runsBefore: List[String] = List("patmat")
    val phaseName = "zerowaste"
    override def description: String = "detect discarded non-Unit expressions"

    def newPhase(prev: Phase): StdPhase = new StdPhase(prev) {
      def apply(unit: CompilationUnit): Unit =
        detectDiscarded(unit.body, discarded = false)
    }

    private def detectDiscarded(tree: Tree, discarded: Boolean): Unit = tree match {
      case tree if !discarded && tree.tpe != null && tree.tpe =:= definitions.UnitTpe =>
        detectDiscarded(tree, discarded = true)

      case _: Ident |
           _: Select |
           _: Apply |
           _: TypeApply |
           _: Function |
           _: New |
           _: Super |
           _: This |
           _: Literal
        if discarded && tree.tpe != null && !(tree.tpe <:< definitions.UnitTpe) =>

        currentRun.reporting.warning(tree.pos, "discarded non-Unit expression", WarningCategory.Unused, NoSymbol)

      case Block(stats, expr) =>
        stats.foreach(detectDiscarded(_, discarded = true))
        detectDiscarded(expr, discarded)

      case Template(parents, self, body) =>
        parents.foreach(detectDiscarded(_, discarded = false))
        detectDiscarded(self, discarded = false)
        body.foreach(detectDiscarded(_, discarded = true))

      case If(_, thenp, elsep) =>
        detectDiscarded(thenp, discarded)
        detectDiscarded(elsep, discarded)

      case LabelDef(_, _, rhs) =>
        detectDiscarded(rhs, discarded = true)

      case Try(body, catches, finalizer) =>
        detectDiscarded(body, discarded)
        catches.foreach(detectDiscarded(_, discarded))
        detectDiscarded(finalizer, discarded = true)

      case CaseDef(_, _, body) =>
        detectDiscarded(body, discarded)

      case Match(_, cases) =>
        cases.foreach(detectDiscarded(_, discarded))

      case Annotated(_, arg) =>
        detectDiscarded(arg, discarded)

      case Typed(expr, _) =>
        detectDiscarded(expr, discarded)

      case tree =>
        tree.children.foreach(detectDiscarded(_, discarded = false))
    }
  }
}

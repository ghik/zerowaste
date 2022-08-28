package com.github.ghik.zerowaste

import scala.tools.nsc.Reporting.WarningCategory
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}

final class ZerowastePlugin(val global: Global) extends Plugin { plugin =>
  import global._

  val name = "zerowaste"
  val description = "Scala compiler plugin that disallows discarding of non-Unit expressions"
  val components: List[PluginComponent] = List(component)

  object MacroExpansionTree {
    def unapply(tree: Tree): Option[Tree] =
      analyzer.macroExpandee(tree) match {
        case EmptyTree | `tree` => None
        case t => Some(t)
      }
  }

  object Applies {
    private def un(tree: Tree): (Tree, List[List[Tree]]) = tree match {
      case Apply(prefix, args) =>
        val (t, argss) = un(prefix)
        (t, args :: argss)
      case t =>
        (t, Nil)
    }

    def unapply(tree: Tree): Some[(Tree, List[List[Tree]])] =
      Some(un(tree))
  }

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

    private def notUnit(tree: Tree): Boolean =
      tree.tpe != null && !(tree.tpe <:< definitions.UnitTpe)

    private def report(tree: Tree): Unit =
      currentRun.reporting.warning(tree.pos, "discarded expression with non-Unit value", WarningCategory.Unused, NoSymbol)

    // Note: not checking Literal, This and Function trees because the compiler already does that
    private def detectDiscarded(tree: Tree, discarded: Boolean): Unit = tree match {
      case MacroExpansionTree(tree) =>
        detectDiscarded(tree, discarded)

      case tree if !discarded && tree.tpe != null && tree.tpe =:= definitions.UnitTpe =>
        detectDiscarded(tree, discarded = true)

      case Applies(Select(_: This | _: Super, termNames.CONSTRUCTOR), argss) =>
        argss.foreach(_.foreach(detectDiscarded(_, discarded = false)))

      case _: Ident if discarded && notUnit(tree) =>
        report(tree)

      case Select(prefix, _) if discarded && notUnit(tree) =>
        report(tree)
        detectDiscarded(prefix, discarded = false)

      case Apply(fun, args) if discarded && notUnit(tree) =>
        report(tree)
        (fun :: args).foreach(detectDiscarded(_, discarded = false))

      case TypeApply(fun, args) if discarded && notUnit(tree) =>
        report(tree)
        (fun :: args).foreach(detectDiscarded(_, discarded = false))

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

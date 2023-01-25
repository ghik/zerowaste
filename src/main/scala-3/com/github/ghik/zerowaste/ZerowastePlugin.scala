package com.github.ghik.zerowaste

import dotty.tools.dotc.ast.Trees.*
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Decorators.*
import dotty.tools.dotc.core.StdNames.*
import dotty.tools.dotc.core.Symbols.*
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.transform.Pickler
import dotty.tools.dotc.typer.TyperPhase

class ZerowastePlugin extends StandardPlugin {
  def name = "zerowaste"
  def description = "Scala compiler plugin that disallows discarding of non-Unit expressions"

  def init(options: List[String]): List[PluginPhase] =
    new ZerowastePhase :: Nil
}

class ZerowastePhase extends PluginPhase {
  import tpd.*

  def phaseName = "zerowaste"
  override def description: String = "detect discarded non-Unit expressions"

  override def runsBefore: Set[String] = Set(Pickler.name)
  override def runsAfter: Set[String] = Set(TyperPhase.name)

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

  private def notUnit(tree: Tree)(using Context): Boolean =
    !(tree.tpe <:< defn.UnitType)

  private def report(tree: Tree)(using Context): Unit =
    dotty.tools.dotc.report.warning("discarded expression with non-Unit value", tree.srcPos)

  private def complete[T <: AnyRef](v: Lazy[T] | T)(using Context): T = v match {
    case l: Lazy[T@unchecked] => l.complete
    case t: T@unchecked => t
  }

  override def transformUnit(tree: Tree)(using Context): Tree = {
    def detectDiscarded(tree: Tree, discarded: Boolean): Unit = tree match {
      case tree if !discarded && tree.tpe =:= defn.UnitType =>
        detectDiscarded(tree, discarded = true)

      case Applies(Select(_: This | _: Super, nme.CONSTRUCTOR), argss) =>
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

      case Template(constr, parents, self, body) =>
        detectDiscarded(constr, discarded = false)
        complete(parents).foreach(detectDiscarded(_, discarded = false))
        detectDiscarded(self, discarded = false)
        complete(body).foreach(detectDiscarded(_, discarded = true))

      case If(_, thenp, elsep) =>
        detectDiscarded(thenp, discarded)
        detectDiscarded(elsep, discarded)

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
        val trav = new TreeTraverser {
          def traverse(t: Tree)(using Context): Unit =
            detectDiscarded(t, discarded = false)
        }
        trav.foldOver((), tree)
    }

    detectDiscarded(tree, discarded = false)
    tree
  }
}

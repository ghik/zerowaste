package com.github.ghik.zerowaste

import dotty.tools.dotc.ast.Trees.*
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Contexts.{Context, ctx}
import dotty.tools.dotc.core.Decorators.*
import dotty.tools.dotc.core.StdNames.*
import dotty.tools.dotc.core.Symbols.*
import dotty.tools.dotc.core.Types.Type
import dotty.tools.dotc.parsing.{Parsers, Tokens}
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.reporting.StoreReporter
import dotty.tools.dotc.transform.Pickler
import dotty.tools.dotc.typer.TyperPhase
import dotty.tools.dotc.util.SourceFile

class ZerowastePlugin extends StandardPlugin {
  def name = "zerowaste"
  def description = "Scala compiler plugin that disallows discarding of non-Unit expressions"

  override def init(options: List[String]): List[PluginPhase] =
    new ZerowastePhase(ZerowasteOptions.parse(options)) :: Nil
}

class ZerowastePhase(options: ZerowasteOptions) extends PluginPhase {
  import tpd.*

  def this() = this(ZerowasteOptions(Nil, Nil))

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

  // Custom discardable types are resolved lazily, upon first use within a run,
  // so that types defined in the sources being compiled are already entered by the typer.
  private var discardableTypesResolved = false
  private var discardableTypes: List[Type] = Nil

  private def customDiscardableTypes(using Context): List[Type] = {
    if (!discardableTypesResolved) {
      discardableTypesResolved = true
      options.errors.foreach(err => dotty.tools.dotc.report.error(s"zerowaste: $err"))
      discardableTypes = options.discardableTypes.flatMap(resolveDiscardableType)
    }
    discardableTypes
  }

  /**
   * Parses and typechecks a type as if it was written in a source file without a package declaration.
   * Errors are reported as a single error pointing at the discardable types file.
   */
  private def resolveDiscardableType(spec: DiscardableTypeSpec)(using Context): Option[Type] = {
    val source = SourceFile.virtual(s"<zerowaste discardable type at ${spec.location}>", spec.code)
    val reporter = new StoreReporter()
    // The context of the current unit has the root imports (scala._, java.lang._, scala.Predef._)
    // and the root class as the owner, i.e. exactly the context of a top-level definition without a package.
    val resolveCtx = ctx.fresh.setSource(source).setReporter(reporter)
    val tpe = {
      given Context = resolveCtx
      val parser = new Parsers.Parser(source)
      val untpdTree = parser.typ()
      parser.accept(Tokens.EOF)
      val typer = ctx.typer
      val tpdTree = typer.checkSimpleKinded(typer.typedType(untpdTree))
      tpdTree.tpe
    }
    val errors = reporter.pendingMessages(using resolveCtx).filter(_.level >= dotty.tools.dotc.interfaces.Diagnostic.ERROR)
    if (errors.nonEmpty || tpe.isError) {
      val details = errors.map(_.message).mkString(": ", "; ", "")
      dotty.tools.dotc.report.error(s"zerowaste: invalid discardable type `${spec.code}` (${spec.location})$details")
      None
    } else {
      Some(tpe)
    }
  }

  private def isDiscardable(tpe: Type)(using Context): Boolean =
    tpe <:< defn.UnitType || customDiscardableTypes.exists(tpe <:< _)

  private def notDiscardable(tree: Tree)(using Context): Boolean =
    !isDiscardable(tree.tpe)

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

      case _: Ident if discarded && notDiscardable(tree) =>
        report(tree)

      case Select(prefix, _) if discarded && notDiscardable(tree) =>
        report(tree)
        detectDiscarded(prefix, discarded = false)

      case Apply(fun, args) if discarded && notDiscardable(tree) =>
        report(tree)
        (fun :: args).foreach(detectDiscarded(_, discarded = false))

      case TypeApply(fun, args) if discarded && notDiscardable(tree) =>
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

package com.github.ghik.zerowaste

import scala.tools.nsc.Reporting.WarningCategory
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}

final class ZerowastePlugin(val global: Global) extends Plugin { plugin =>
  import global._

  val name = "zerowaste"
  val description = "Scala compiler plugin that disallows discarding of non-Unit expressions"
  val components: List[PluginComponent] = List(component)

  private var parsedOptions: ZerowasteOptions = ZerowasteOptions(Nil, Nil)

  override def init(options: List[String], error: String => Unit): Boolean = {
    parsedOptions = ZerowasteOptions.parse(options)
    parsedOptions.errors.foreach(err => error(s"zerowaste: $err"))
    true
  }

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

    def newPhase(prev: Phase): StdPhase = new ZerowastePhase(prev)

    /**
     * Parses and typechecks a type as if it was written in a source file without a package declaration.
     * Errors are reported as a single error pointing at the discardable types file
     * (except for syntax errors, which are reported directly by the parser).
     */
    private def resolveDiscardableType(spec: DiscardableTypeSpec): Option[Type] = {
      def invalid(details: String): Option[Type] = {
        globalError(s"zerowaste: invalid discardable type `${spec.code}` (${spec.location})$details")
        None
      }

      val errorsBefore = reporter.errorCount
      val parser = newUnitParser(spec.code, s"<zerowaste discardable type at ${spec.location}>")
      val parsed = parser.typ()
      parser.accept(scala.tools.nsc.ast.parser.Tokens.EOF)
      if (reporter.errorCount > errorsBefore) None
      else {
        val typer = analyzer.newTyper(analyzer.rootContext(parser.unit))
        typer.silent(_.typedType(parsed)) match {
          case analyzer.SilentResultValue(tpt) if !tpt.isErroneous && tpt.tpe != null && !tpt.tpe.isError =>
            Some(tpt.tpe)
          case analyzer.SilentResultValue(_) =>
            invalid("")
          case err: analyzer.SilentTypeError =>
            invalid(err.errors.map(_.errMsg).mkString(": ", "; ", ""))
        }
      }
    }

    private class ZerowastePhase(prev: Phase) extends StdPhase(prev) {
      // Custom discardable types are resolved lazily, upon first use within a run,
      // so that types defined in the sources being compiled are already entered by the typer.
      private lazy val customDiscardableTypes: List[Type] =
        enteringTyper(parsedOptions.discardableTypes.flatMap(resolveDiscardableType))

      def apply(unit: CompilationUnit): Unit =
        detectDiscarded(unit.body, discarded = false)

      private def isDiscardable(tpe: Type): Boolean =
        tpe <:< definitions.UnitTpe || customDiscardableTypes.exists(tpe <:< _)

      private def notDiscardable(tree: Tree): Boolean =
        tree.tpe != null && !isDiscardable(tree.tpe)

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
}

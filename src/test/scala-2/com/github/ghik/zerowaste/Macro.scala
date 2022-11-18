package com.github.ghik.zerowaste

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object Macro {
  def inlinedValue: Int = macro inlinedValueImpl

  def inlinedValueImpl(c: blackbox.Context): c.Tree = {
    import c.universe._
    q"""
       "totallyUnused".trim
       42
     """
  }
}

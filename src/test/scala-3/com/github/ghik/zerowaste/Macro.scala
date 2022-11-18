package com.github.ghik.zerowaste

object Macro {
  inline def inlinedValue: Int = {
    "totallyUnused".trim
    42
  }
}

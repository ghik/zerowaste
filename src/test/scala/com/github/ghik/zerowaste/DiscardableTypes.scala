package com.github.ghik.zerowaste

// Types used in tests of custom discardable types (see testdata/discardable-types.txt)

trait Discardable
class DiscardableSub extends Discardable

class Wrapper[+T]

// listed in testdata/discardable-types-extra.txt
trait Extra

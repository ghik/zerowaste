import com.github.ghik.zerowaste._

object zerowaste {
  def stuff: Int = 42
  def handle(int: Int): String = int.toString
  def summon[T]: T = throw new Exception("nie")

  // should emit warnings
  stuff
  new String
  "".trim
  handle(42)
  summon[Double]

  if ("".isEmpty) stuff else stuff

  try stuff catch {
    case _: Exception => stuff
  } finally {
    stuff
  }

  val useless: Unit = {
    stuff
    stuff
  }

  ("": Any) match {
    case "" => stuff
    case _ => stuff
  }

  // should not emit warnings
  throw new Exception("nienie")

  val t = Macro.inlinedValue // macro

  class Klas(arg: Int) {
    def this() = this(42)
  }

  abstract class Base[T: scala.reflect.ClassTag]
  class Sub extends Base[Int]
}

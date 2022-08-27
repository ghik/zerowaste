object zerowaste {
  def stuff: Int = 42
  def handle(int: Int): String = int.toString
  def summon[T]: T = throw new Exception("nie")

  stuff
  new String
  "".trim
  handle(42)
  summon[Double]
}

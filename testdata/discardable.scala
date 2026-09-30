import com.github.ghik.zerowaste._

object discardable {
  def d: Discardable = null
  def sub: DiscardableSub = null
  def wu: Wrapper[Unit] = null
  def wn: Wrapper[Nothing] = null
  def wi: Wrapper[Int] = null
  def ou: Option[Unit] = None
  def su: Some[Unit] = Some(())
  def oi: Option[Int] = None
  def ex: Extra = null
  def stuff: Int = 42

  // should not emit warnings when discardable types are configured
  d
  sub
  wu
  wn
  ou
  su
  if (stuff > 0) d else sub
  val useless: Unit = {
    sub
    d
  }

  // should emit warnings (unless discardable-types-extra.txt is also configured)
  ex

  // should emit warnings
  wi
  oi
  stuff
}

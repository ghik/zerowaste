import cats.effect.*

object NonUnitDiscard extends IOApp.Simple :
  val run: IO[Unit] =
    IO.ref(List.empty[Int]).flatMap { ref =>
      IO.println("discared value")
      ref.set(List.range(0, 11))
    }

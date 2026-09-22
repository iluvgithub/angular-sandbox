package com.myway.angular.sandbox.service.gridpoll

import cats.effect.*
import cats.effect.std.Random
import fs2.Stream
import fs2.concurrent.Topic

import scala.concurrent.duration.*

/** `run` is a never-ending stream: roughly once a second (jittered a little so it doesn't look
  * robotic) it picks a random cell, gives it a new value, and publishes the change to every
  * subscriber.
  */
final class GridService private (
  state: Ref[IO, Vector[Vector[Double]]],
  topic: Topic[IO, CellUpdate],
  rows: Int,
  cols: Int
) {

  private val HUNDRED_SCALE: Double = 100.0
  private val QUEUE_SIZE: Int       = 64

  def snapshot: IO[GridSnapshot] =
    state.get.map(grid => GridSnapshot(rows, cols, grid.map(_.toList).toList))

  /** Live feed of cell changes. Each subscriber gets its own bounded queue. */
  def updates: Stream[IO, CellUpdate] =
    topic.subscribe(maxQueued = QUEUE_SIZE)

  private def round(v: Double): Double =
    math.round(v * HUNDRED_SCALE) / HUNDRED_SCALE

  private def tick(random: Random[IO]): IO[Unit] =
    for {
      r        <- random.betweenInt(0, rows)
      c        <- random.betweenInt(0, cols)
      newValue <- random.betweenDouble(0.0, 100.0)
      now      <- IO.realTimeInstant
      rounded = round(newValue)
      _ <- state.update(grid => grid.updated(r, grid(r).updated(c, rounded)))
      _ <- topic.publish1(CellUpdate(r, c, rounded, now.toEpochMilli))
    } yield ()

  /** Runs forever, updating one cell roughly every second (900ms-1300ms jitter). */
  def run: Stream[IO, Unit] =
    Stream.eval(Random.scalaUtilRandom[IO]).flatMap { random =>
      Stream
        .repeatEval(random.betweenInt(900, 1300).map(_.millis))
        .evalMap(delay => IO.sleep(delay) *> tick(random))
    }
}

object GridService {

  def createDefaultGrid: IO[GridService] = create(5, 4)

  def create(rows: Int, cols: Int): IO[GridService] =
    for {
      initial <- IO.pure(Vector.fill(rows)(Vector.fill(cols)(0.0)))
      state   <- Ref.of[IO, Vector[Vector[Double]]](initial)
      topic   <- Topic[IO, CellUpdate]
    } yield new GridService(state, topic, rows, cols)

}

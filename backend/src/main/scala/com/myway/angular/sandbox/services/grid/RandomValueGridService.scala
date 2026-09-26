package com.myway.angular.sandbox.services.grid

import cats.effect.IO
import fs2.Stream
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.dsl.io.{Ok, _}
import org.http4s.{ServerSentEvent, _}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Random

object RandomValueGridService {

  val Period: FiniteDuration = 800.millis

  val RANGE = 10000

  val DIVISOR = 100.0

  private val MinDim     = 1
  private val MaxDim     = 50
  private val ROWS_PARAM = "rows"
  private val COLS_PARAM = "cols"

  final case class Cell(i: Int, j: Int, value: Double)

  object RowsParam extends OptionalQueryParamDecoderMatcher[Int](ROWS_PARAM)
  object ColsParam extends OptionalQueryParamDecoderMatcher[Int](COLS_PARAM)

  def randomCellStream(rows: Int, cols: Int): Stream[IO, ServerSentEvent] =
    Stream
      .awakeEvery[IO](Period)
      .evalMap { _ =>
        IO {
          val i     = Random.nextInt(rows)
          val j     = Random.nextInt(cols)
          val value = Math.round(Random.nextDouble() * RANGE) / DIVISOR
          Cell(i, j, value)
        }
      }
      .map(cell => ServerSentEvent(data = Some(cell.asJson.noSpaces)))

  def respond(optRows: Option[Int], optCols: Option[Int]): IO[Response[IO]] = {

    val rows = clampDim(optRows, 5)
    val cols = clampDim(optCols, 3)
    Ok(RandomValueGridService.randomCellStream(rows, cols))

  }

  private def clampDim(value: Option[Int], default: Int): Int =
    value.filter(v => v >= MinDim && v <= MaxDim).getOrElse(default)

}

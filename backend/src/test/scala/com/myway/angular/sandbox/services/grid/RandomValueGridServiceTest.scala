package com.myway.angular.sandbox.services.grid

import cats.effect.IO
import cats.effect.testkit.TestControl
import io.circe.parser.decode
import munit.CatsEffectSuite
import org.http4s.{MediaType, Status, Uri}
import org.http4s.dsl.io._
import io.circe.generic.auto._
import scala.concurrent.duration._

class RandomValueGridServiceTest extends CatsEffectSuite {

  // --- randomCellStream: value generation -----------------------------------

  test("randomCellStream emits cells whose (i, j) stay within the requested bounds") {
    val rows = 3
    val cols = 2

    val program: IO[Unit] =
      RandomValueGridService
        .randomCellStream(rows, cols)
        .take(20)
        .compile
        .toList
        .map { events =>
          assertEquals(events.size, 20)

          events.foreach { sse =>
            val payload = sse.data.getOrElse(fail("SSE event is missing a data payload"))
            val cell = decode[RandomValueGridService.Cell](payload)
              .getOrElse(fail(s"could not decode SSE payload: $payload"))

            assert(cell.i >= 0 && cell.i < rows, s"i=${cell.i} is out of bounds for rows=$rows")
            assert(cell.j >= 0 && cell.j < cols, s"j=${cell.j} is out of bounds for cols=$cols")
            assert(
              cell.value >= 0.0 && cell.value <= (RandomValueGridService.RANGE / RandomValueGridService.DIVISOR),
              s"value=${cell.value} is outside the expected [0, 100] range",
            )
          }
        }

    TestControl.executeEmbed(program)
  }

  test("randomCellStream degenerates to a fixed cell when rows=1 and cols=1") {
    val program: IO[Unit] =
      RandomValueGridService
        .randomCellStream(1, 1)
        .take(5)
        .compile
        .toList
        .map { events =>
          events.foreach { sse =>
            val cell = decode[RandomValueGridService.Cell](sse.data.get).toOption.get
            assertEquals(cell.i, 0)
            assertEquals(cell.j, 0)
          }
        }

    TestControl.executeEmbed(program)
  }

  test("randomCellStream paces emissions roughly Period apart") {
    val program: IO[FiniteDuration] =
      for {
        start <- IO.monotonic
        _     <- RandomValueGridService.randomCellStream(1, 1).take(4).compile.drain
        end   <- IO.monotonic
      } yield end - start

    // Runs under TestControl's simulated clock, so this resolves instantly in
    // wall-clock time even though 4 * Period of *virtual* time elapses.
    TestControl.executeEmbed(program).map { elapsed =>
      assert(
        elapsed >= RandomValueGridService.Period * 3,
        s"expected at least 3 full periods to elapse for 4 spaced events, got $elapsed",
      )
    }
  }

  // --- respond: HTTP-level behavior ------------------------------------------

  test("respond returns 200 OK with an SSE content type") {
    RandomValueGridService.respond(None, None).map { resp =>
      assertEquals(resp.status, Status.Ok)
      assertEquals(resp.contentType.map(_.mediaType), Some(MediaType.`text/event-stream`))
    }
  }

  test("respond falls back to the default rows/cols when out-of-range values are supplied") {
    // rows/cols are clamped internally (see clampDim), so out-of-range input must
    // not blow up the request - it should still succeed with the defaults (5, 3).
    RandomValueGridService.respond(Some(-1), Some(9999)).map { resp =>
      assertEquals(resp.status, Status.Ok)
    }
  }

  // --- query param matchers ---------------------------------------------------

  test("RowsParam/ColsParam extract typed values from the query string") {
    val uri = Uri.unsafeFromString("/api/stream?rows=7&cols=2")

    uri.multiParams match {
      case RandomValueGridService.RowsParam(rowsOpt) +& RandomValueGridService.ColsParam(colsOpt) =>
        assertEquals(rowsOpt, Some(7))
        assertEquals(colsOpt, Some(2))
      case other =>
        fail(s"query params did not match RowsParam +& ColsParam: $other")
    }
  }

  test("RowsParam/ColsParam are None when absent from the query string") {
    val uri = Uri.unsafeFromString("/api/stream")

    uri.multiParams match {
      case RandomValueGridService.RowsParam(rowsOpt) +& RandomValueGridService.ColsParam(colsOpt) =>
        assertEquals(rowsOpt, None)
        assertEquals(colsOpt, None)
      case other =>
        fail(s"query params did not match RowsParam +& ColsParam: $other")
    }
  }
}


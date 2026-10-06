package com.myway.angular.sandbox.services.onoffstream

import cats.effect.{IO, Ref}
import fs2.Stream
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, Response, Status}

import scala.concurrent.duration._
final case class StreamTick(tick: Long, timestamp: String, streaming: Boolean)

trait OnOffState {
  def dataStream: Stream[IO, StreamTick]
  def turnOn: IO[Unit]
  def turnOff: IO[Unit]
}
final class OnOffStateImpl(
  streamingFlag: Ref[IO, Boolean],
  tickCounter: Ref[IO, Long]
) extends  OnOffState {

  def turnOn: IO[Unit] =
    IO.println("turn on") >> streamingFlag.set(true)

  def turnOff: IO[Unit] =
    IO.println("turn off") >> streamingFlag.set(false)

  /** Infinite fs2 stream, ticking every 2 seconds, emitting only while "on" */
  def dataStream: Stream[IO, StreamTick] =
    Stream
      .awakeEvery[IO](2.seconds)
      .evalMap { _ =>
        for {
          isOn <- streamingFlag.get
          n    <- tickCounter.updateAndGet(_ + 1)
        } yield StreamTick(n, java.time.Instant.now.toString, isOn)
      }
      .filter(_.streaming) // only emit ticks while streaming is on
}

object OnOffState {
  def create: IO[OnOffState] =
    for {
      flag    <- Ref.of[IO, Boolean](false)
      counter <- Ref.of[IO, Long](0L)
    } yield new OnOffStateImpl(flag, counter)
}

object OnOffStateUtil {

  def prepareRoute(state: OnOffState): IO[Response[IO]#Self#Self] = {
    val sseStream: Stream[IO, Byte] =
      state.dataStream
        .map(tick => s"data: ${tick.asJson.noSpaces}\n\n")
        .through(fs2.text.utf8.encode)
    IO.pure(
      Response[IO](Status.Ok)
        .withEntity(sseStream)
        .withContentType(`Content-Type`(MediaType.`text/event-stream`))
    )
  }
}

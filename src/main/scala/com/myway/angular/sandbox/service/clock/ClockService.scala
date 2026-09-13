package com.myway.angular.sandbox.service.clock

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration._
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.semigroupk._
import com.comcast.ip4s._
import fs2.Stream
import org.http4s.{HttpRoutes, Response, StaticFile}
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger
import org.http4s.server.staticcontent.resourceServiceBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

object ClockService {

  private val clockFormat = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

  def clock(wsb: WebSocketBuilder2[IO]):IO[Response[IO]] =  {
    val ticks:   Stream[IO, WebSocketFrame] =
      Stream.awakeEvery[IO](1.second).map { _ =>
        WebSocketFrame.Text(LocalDateTime.now().format(clockFormat))
      }
    wsb.build(send = ticks, receive = _ => Stream.empty)
  }
}

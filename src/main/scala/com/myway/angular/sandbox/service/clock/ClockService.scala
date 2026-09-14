package com.myway.angular.sandbox.service.clock

import cats.effect.IO
import fs2.Stream
import org.http4s.ServerSentEvent

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration._

object ClockService {

  private val clockFormat = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

  private def timestamp: String = LocalDateTime.now().format(clockFormat)

  def clockNow: IO[String] = IO(timestamp)

}

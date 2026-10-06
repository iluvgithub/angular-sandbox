package com.myway.angular.sandbox.server

import cats.effect.IO
import cats.syntax.all._
import com.myway.angular.sandbox.services.chat.ChatService
import com.myway.angular.sandbox.services.grid.RandomValueGridService
import com.myway.angular.sandbox.services.onoffstream.OnOffState
import com.myway.angular.sandbox.services.uppercase.UpperCaseService
import fs2.concurrent.Topic
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.implicits._
import org.mockito.{ArgumentMatchersSugar, IdiomaticMockito}

class RoutesTest
    extends CatsEffectSuite
    with IdiomaticMockito
    with ArgumentMatchersSugar {

  private def buildTopics: IO[Map[String, Topic[IO, String]]] =
    List("Asterix", "Obelix")
      .traverse(room => Topic[IO, String].map(room -> _))
      .map(_.toMap)

  private def hasHeader(
      resp: Response[IO],
      name: String,
      value: String
  ): Boolean =
    resp.headers.headers.exists(h =>
      h.name.toString.equalsIgnoreCase(name) && h.value == value
    )

  private case class Mocks(
      upperCase: UpperCaseService,
      grid: RandomValueGridService,
      chat: ChatService,
      routes: Routes
  )

  private def newMocks(): Mocks = {
    val upperCase = mock[UpperCaseService]
    val grid = mock[RandomValueGridService]
    val chat = mock[ChatService]
    Mocks(upperCase, grid, chat, Routes(upperCase, grid, chat))
  }

  test(
    "GET /api/stream?rows=&cols= delegates to RandomValueGridService.respond with the parsed params"
  ) {
    val m = newMocks()
    m.grid.respond(Some(5), Some(3)) returns IO.pure(Response[IO](Status.Ok))

    for {
      topics <- buildTopics
      req = Request[IO](Method.GET, uri"/api/stream?rows=5&cols=3")
      onOffState <- OnOffState.create
      resp <- m.routes.routes(topics, onOffState).orNotFound(req)
    } yield {
      assertEquals(resp.status, Status.Ok)
      m.grid.respond(Some(5), Some(3)) was called
      m.upperCase wasNever called
      m.chat wasNever called
    }
  }

  test("GET /api/stream with no query params passes None/None through") {
    val m = newMocks()
    m.grid.respond(None, None) returns IO.pure(Response[IO](Status.Ok))

    for {
      onOffState <- OnOffState.create
      topics <- buildTopics
      req = Request[IO](Method.GET, uri"/api/stream")
      _ <- m.routes.routes(topics, onOffState).orNotFound(req)
    } yield m.grid.respond(None, None) was called
  }

  test(
    "POST /api/uppercase delegates to UpperCaseService.respond with the same request"
  ) {
    val m = newMocks()
    val req = Request[IO](Method.POST, uri"/api/uppercase")
    m.upperCase.respond(req) returns IO.pure(Response[IO](Status.Ok))

    for {
      topics <- buildTopics
      resp <- m.routes.routes(topics).orNotFound(req)
    } yield {
      assertEquals(resp.status, Status.Ok)
      m.upperCase.respond(req) was called
      m.grid wasNever called
      m.chat wasNever called
    }
  }

  test(
    "GET /api/health returns 200 with a JSON status body, independent of any service"
  ) {
    val m = newMocks()
    val req = Request[IO](Method.GET, uri"/api/health")

    for {
      topics <- buildTopics
      resp <- m.routes.routes(topics).orNotFound(req)
      body <- resp.as[String]
    } yield {
      assertEquals(resp.status, Status.Ok)
      assertEquals(
        resp.contentType.map(_.mediaType),
        Some(MediaType.application.json)
      )
      assert(
        body.contains(""""status":"ok""""),
        s"unexpected health body: $body"
      )
      m.upperCase wasNever called
      m.grid wasNever called
      m.chat wasNever called
    }
  }

  test(
    "GET /api/chat/{room}/stream delegates to ChatService.subscribe with the extracted room"
  ) {
    val m = newMocks()

    for {
      topics <- buildTopics
      _ = m.chat.subscribe(topics, "Asterix") returns IO.pure(
        Response[IO](Status.Ok)
      )
      req = Request[IO](Method.GET, uri"/api/chat/Asterix/stream")
      resp <- m.routes.routes(topics).orNotFound(req)
    } yield {
      assertEquals(resp.status, Status.Ok)
      m.chat.subscribe(topics, "Asterix") was called
    }
  }

  test(
    "POST /api/chat/send delegates to ChatService.sendMessage with the same request"
  ) {
    val m = newMocks()
    val req = Request[IO](Method.POST, uri"/api/chat/send")

    for {
      topics <- buildTopics
      _ = m.chat.sendMessage(topics, req) returns IO.pure(
        Response[IO](Status.Ok)
      )
      resp <- m.routes.routes(topics).orNotFound(req)
    } yield {
      assertEquals(resp.status, Status.Ok)
      m.chat.sendMessage(topics, req) was called
    }
  }

  test(
    "an unmatched API path falls through without touching any service mock"
  ) {
    val m = newMocks()
    val req = Request[IO](Method.GET, uri"/api/does-not-exist")

    for {
      topics <- buildTopics
      _ <- m.routes.routes(topics).orNotFound(req)
    } yield {
      m.upperCase wasNever called
      m.grid wasNever called
      m.chat wasNever called
    }
  }

}

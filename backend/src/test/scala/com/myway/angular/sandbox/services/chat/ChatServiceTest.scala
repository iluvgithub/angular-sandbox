package com.myway.angular.sandbox.services.chat

import cats.effect.IO
import cats.effect.testkit.TestControl
import io.circe.generic.auto._
import io.circe.syntax._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.implicits._

import scala.concurrent.duration._

class ChatServiceTest extends CatsEffectSuite {

  val chatService = ChatServiceInstance
  import org.http4s.circe.CirceEntityCodec._
  private def sendRequest(
      room: String,
      sender: String,
      text: String
  ): Request[IO] =
    Request[IO](Method.POST, uri"/api/chat/send")
      .withEntity(chatService.ChatMessage(room, sender, text))

  private def broadcastJson(sender: String, text: String): String =
    chatService.ChatBroadcast(sender, text).asJson.noSpaces

  // --- otherRoom ---------------------------------------------------------------

  test("otherRoom returns the sibling room for each known room") {
    assertEquals(chatService.otherRoom("Asterix"), Some("Obelix"))
    assertEquals(chatService.otherRoom("Obelix"), Some("Asterix"))
  }

  // --- sendMessage ---------------------------------------------------------------

  test(
    "sendMessage cross-posts the same broadcast to both the target room and its sibling"
  ) {
    val program =
      for {
        topics <- chatService.buildTopics
        asterixSub <- topics("Asterix")
          .subscribe(maxQueued = 16)
          .take(1)
          .compile
          .lastOrError
          .start
        obelixSub <- topics("Obelix")
          .subscribe(maxQueued = 16)
          .take(1)
          .compile
          .lastOrError
          .start
        _ <- IO.sleep(
          50.millis
        ) // let both subscriptions register before publishing
        resp <- chatService.sendMessage(
          topics,
          sendRequest("Asterix", "alice", "hello")
        )
        _ = assertEquals(resp.status, Status.Ok)
        asterixMsg <- asterixSub.joinWithNever
        obelixMsg <- obelixSub.joinWithNever
      } yield (asterixMsg, obelixMsg)

    TestControl.executeEmbed(program).map { case (asterixMsg, obelixMsg) =>
      val expected = broadcastJson("alice", "hello")
      assertEquals(
        asterixMsg,
        expected,
        "the target room should receive the broadcast"
      )
      assertEquals(
        obelixMsg,
        expected,
        "the sibling room should also receive the same broadcast"
      )
    }
  }

  test(
    "sendMessage returns 404 for an unknown room, but still leaks the message into Asterix (bug)"
  ) {
    val program =
      for {
        topics <- chatService.buildTopics
        asterixSub <- topics("Asterix")
          .subscribe(maxQueued = 16)
          .take(1)
          .compile
          .lastOrError
          .start
        _ <- IO.sleep(50.millis)
        resp <- chatService.sendMessage(
          topics,
          sendRequest("NoSuchRoom", "bob", "leaked message")
        )
        _ = assertEquals(resp.status, Status.NotFound)
        leaked <- asterixSub.joinWithNever
      } yield leaked

    TestControl.executeEmbed(program).map { leaked =>
      assertEquals(leaked, broadcastJson("bob", "leaked message"))
    }
  }

  // --- subscribe -------------------------------------------------------------------

  test(
    "subscribe(\"Asterix\") actually streams messages published to Obelix, not Asterix (cross-wired)"
  ) {
    val program =
      for {
        topics <- chatService.buildTopics
        resp <- chatService.subscribe(topics, "Asterix")
        _ = assertEquals(resp.status, Status.Ok)
        bodyFiber <- resp.body
          .through(fs2.text.utf8.decode)
          .interruptAfter(300.millis)
          .compile
          .string
          .start
        _ <- IO.sleep(50.millis)
        _ <- topics("Obelix").publish1(broadcastJson("carol", "hi from obelix"))
        body <- bodyFiber.joinWithNever
      } yield body

    TestControl.executeEmbed(program).map { body =>
      assert(
        body.contains("hi from obelix"),
        s"expected the Obelix-published message to appear on the Asterix stream, got: $body"
      )
    }
  }

  test(
    "subscribe(\"Obelix\") streams messages published to Asterix (cross-wired, symmetric case)"
  ) {
    val program =
      for {
        topics <- chatService.buildTopics
        resp <- chatService.subscribe(topics, "Obelix")
        _ = assertEquals(resp.status, Status.Ok)
        bodyFiber <- resp.body
          .through(fs2.text.utf8.decode)
          .interruptAfter(300.millis)
          .compile
          .string
          .start
        _ <- IO.sleep(50.millis)
        _ <- topics("Asterix").publish1(
          broadcastJson("dave", "hi from asterix")
        )
        body <- bodyFiber.joinWithNever
      } yield body

    TestControl.executeEmbed(program).map { body =>
      assert(
        body.contains("hi from asterix"),
        s"expected the Asterix-published message, got: $body"
      )
    }
  }

  test(
    "subscribe never returns 404, even for an unrecognized room (consequence of the otherRoom bug)"
  ) {
    val program =
      for {
        topics <- chatService.buildTopics
        resp <- chatService.subscribe(topics, "NoSuchRoom")
      } yield resp.status

    TestControl.executeEmbed(program).map { status =>
      // Documents the current, arguably-wrong contract: because otherRoom("NoSuchRoom")
      // resolves to Some("Asterix") rather than None, this "succeeds" with a stream of
      // Asterix's topic instead of rejecting the unknown room with 404.
      assertEquals(status, Status.Ok)
    }
  }
}

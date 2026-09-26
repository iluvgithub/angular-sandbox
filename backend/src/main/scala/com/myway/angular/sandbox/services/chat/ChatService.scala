package com.myway.angular.sandbox.services.chat

import cats.effect._
import cats.syntax.all._
import fs2.concurrent.Topic
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
object ChatService {

  // The only two known chatrooms. Extend this list to add more rooms.
  val Rooms: List[String] = List("Asterix", "Obelix")

  def otherRoom(room: String): Option[String] = Rooms.filterNot(_.equals(room)).headOption
  final case class ChatMessage(room: String, text: String)
  final case class ChatBroadcast(sender: String, text: String)

  implicit val chatMessageDecoder: EntityDecoder[IO, ChatMessage] =
    jsonOf[IO, ChatMessage]

  def buildTopics: IO[Map[String, Topic[IO, String]]] =
    Rooms.traverse(room => Topic[IO, String].map(room -> _)).map(_.toMap)

  def sendMessage(topics: Map[String, Topic[IO, String]], req: Request[IO]): IO[Response[IO]] =
    for {
      msg <- req.as[ChatMessage]
      room        = msg.room
      optOther    = otherRoom(room)
      messageText = msg.text
      _ <- optOther.flatMap(topics.get).map(_.publish1(s">$messageText")).sequence
      resp <- topics.get(room) match {
        case Some(topic) =>
                     topic.publish1(ChatBroadcast(room, messageText).asJson.noSpaces) *> Ok(Map("status" -> "sent").asJson)
        case None =>
          NotFound(s"""{"error":"Unknown chat room: $room"}""")      }
    } yield resp

  def subscribe(topics: Map[String, Topic[IO, String]], room: String): IO[Response[IO]] =
    (
      for {
        otherRoom  <- otherRoom(room)
        otherTopic <- topics.get(otherRoom)
      } yield otherTopic
    ) match {
      case Some(otherTopic) =>
        Ok(
          otherTopic
            .subscribe(maxQueued = 64)
            .map(text => ServerSentEvent(data = Some(s"$text")))
        )
      case None =>
        NotFound(s"""{"error":"Unknown chat room: $room"}""")
    }

}

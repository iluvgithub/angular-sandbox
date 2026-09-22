package com.myway.angular.sandbox.service.gridpoll

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

/** A single cell change, pushed to clients over SSE as it happens. */
final case class CellUpdate(row: Int, col: Int, value: Double, timestamp: Long)

object CellUpdate {
  implicit val encoder: Encoder[CellUpdate] = deriveEncoder
  implicit val decoder: Decoder[CellUpdate] = deriveDecoder
}

/** Full grid snapshot, used for the initial page load before SSE updates start arriving. */
final case class GridSnapshot(rows: Int, cols: Int, values: List[List[Double]])

object GridSnapshot {
  implicit val encoder: Encoder[GridSnapshot] = deriveEncoder
  implicit val decoder: Decoder[GridSnapshot] = deriveDecoder

}
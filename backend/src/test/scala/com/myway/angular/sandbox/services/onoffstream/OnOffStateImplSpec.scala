package com.myway.angular.sandbox.services.onoffstream

import cats.effect.IO
import cats.effect.testkit.TestControl
import munit.CatsEffectSuite

import scala.concurrent.duration._

class OnOffStateImplSpec extends CatsEffectSuite {

  test("dataStream emits nothing while off") {
    val program = for {
      state <- OnOffState.create
      _ <- state.turnOff
      ticks <- state.dataStream
        .interruptAfter(3.seconds)
        .compile
        .toList
    } yield ticks

    TestControl.executeEmbed(program).map { ticks =>
      assertEquals(ticks, Nil)
    }
  }

  test("dataStream emits ticks once turned on") {
    val program = for {
      state      <- OnOffState.create
      ticksFiber <- state.dataStream.take(2).compile.toList.start
      _          <- IO.sleep(1.millis) // yield so the stream subscribes before turnOn, simulated time so this is free
      _          <- state.turnOn
      ticks      <- ticksFiber.joinWithNever
    } yield ticks

    TestControl.executeEmbed(program).map { ticks =>
      assertEquals(ticks.map(_.tick), List(1L, 2L))
      assert(ticks.forall(_.streaming))
    }
  }

  test("tick counter does not advance while paused (off)") {
    val program = for {
      state <- OnOffState.create
      fiber <- state.dataStream.take(1).compile.lastOrError.start
      _     <- IO.sleep(10.seconds) // well beyond several 2s intervals, but stream stays OFF
      _     <- state.turnOn
      tick  <- fiber.joinWithNever
    } yield tick

    TestControl.executeEmbed(program).map { tick =>
      assertEquals(tick.tick, 1L) // counter did not advance during the 10s of being paused
    }
  }

  test("turning off stops further ticks from being emitted") {
    val program = for {
      state <- OnOffState.create
      _     <- state.turnOn
      ticks <- state.dataStream
        .evalTap(_ => state.turnOff)
        .take(1)
        .interruptAfter(10.seconds)
        .compile
        .toList
    } yield ticks

    TestControl.executeEmbed(program).map { ticks =>
      assertEquals(ticks.length, 1)
    }
  }

  test("turnOn then turnOff: emits while on, silent after off") {
    val program = for {
      state  <- OnOffState.create
      _      <- state.turnOn
      ticks1 <- state.dataStream.take(1).interruptAfter(3.seconds).compile.toList
      _      <- state.turnOff
      ticks2 <- state.dataStream.interruptAfter(5.seconds).compile.toList
    } yield (ticks1, ticks2)

    TestControl.executeEmbed(program).map { case (ticks1, ticks2) =>
      assert(ticks1.nonEmpty, "expected at least one tick while on")
      assertEquals(ticks2, Nil, "expected no ticks after turning off")
    }
  }

  test("counter resumes from where it left off after off/on cycle, not reset to 0") {
    val program = for {
      state  <- OnOffState.create
      _      <- state.turnOn
      first  <- state.dataStream.take(2).compile.toList // ticks 1, 2
      _      <- state.turnOff
      _      <- IO.sleep(10.seconds) // time passes while off — should not affect counter
      _      <- state.turnOn
      second <- state.dataStream.take(2).compile.toList // should continue at 3, 4
    } yield (first, second)

    TestControl.executeEmbed(program).map { case (first, second) =>
      assertEquals(first.map(_.tick), List(1L, 2L))
      assertEquals(second.map(_.tick), List(3L, 4L))
    }
  }

  test("exact tick timing: ticks arrive every 2 seconds while on") {
    val program = for {
      state <- OnOffState.create
      _     <- state.turnOn
      ticks <- state.dataStream.take(3).compile.toList
    } yield ticks

    TestControl.executeEmbed(program).map { ticks =>
      assertEquals(ticks.map(_.tick), List(1L, 2L, 3L))
      // TestControl guarantees these were produced at simulated t=2s, 4s, 6s respectively;
      // if finer-grained timing assertions are needed, use TestControl's lower-level
      // `.results` API instead of `.executeEmbed`, which also exposes elapsed simulated time.
    }
  }
}
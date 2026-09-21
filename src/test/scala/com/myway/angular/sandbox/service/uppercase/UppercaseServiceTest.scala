package com.myway.angular.sandbox.service.uppercase

import munit.CatsEffectSuite
import org.scalatest.matchers.should.Matchers

class UppercaseServiceTest extends CatsEffectSuite with Matchers {

  test("convert to uppercase ") {
    for {
      out <- UppercaseService.toUppercase("abCed")
    } yield out shouldBe "ABCED"

  }
}

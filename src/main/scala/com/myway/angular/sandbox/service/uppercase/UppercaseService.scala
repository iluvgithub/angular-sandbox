package com.myway.angular.sandbox.service.uppercase

import cats.effect.IO

object UppercaseService {

  def toUppercase(s:String):IO[String] = IO.pure(s.toUpperCase)

}

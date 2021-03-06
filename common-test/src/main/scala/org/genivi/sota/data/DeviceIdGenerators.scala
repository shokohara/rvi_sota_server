/**
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 * License: MPL-2.0
 */
package org.genivi.sota.data

import cats.syntax.show._
import org.genivi.sota.data.Device.DeviceId
import org.scalacheck.{Arbitrary, Gen}

/**
  * Created by vladimir on 16/03/16.
  */
trait DeviceIdGenerators {
  /**
    * For property based testing purposes, we need to explain how to
    * randomly generate (possibly invalid) VINs.
    *
    * @see [[https://www.scalacheck.org/]]
    */

  val genVin: Gen[DeviceId] =
    for {
      vin <- SemanticVin.genSemanticVin
    } yield DeviceId(vin.show)

  implicit lazy val arbVin: Arbitrary[DeviceId] =
    Arbitrary(genVin)

  val genVinChar: Gen[Char] =
    Gen.oneOf('A' to 'Z' diff List('I', 'O', 'Q'))

  val genInvalidDeviceId: Gen[DeviceId] = {

    val genTooLongVin: Gen[String] = for {
      n  <- Gen.choose(18, 100) // scalastyle:ignore magic.number
      cs <- Gen.listOfN(n, genVinChar)
    } yield cs.mkString

    val genTooShortVin: Gen[String] = for {
      n  <- Gen.choose(1, 16) // scalastyle:ignore magic.number
      cs <- Gen.listOfN(n, genVinChar)
    } yield cs.mkString

    val genNotAlphaNumVin: Gen[String] =
      Gen.listOfN(17, Arbitrary.arbitrary[Char]). // scalastyle:ignore magic.number
        suchThat(_.exists(!_.isLetterOrDigit)).flatMap(_.mkString)

    Gen.oneOf(genTooLongVin, genTooShortVin, genNotAlphaNumVin)
       .map(DeviceId)
  }

  def getInvalidVin: DeviceId =
    genInvalidDeviceId.sample.getOrElse(getInvalidVin)
}

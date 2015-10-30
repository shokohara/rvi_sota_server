/**
 * Copyright: Copyright (C) 2015, Jaguar Land Rover
 * License: MPL-2.0
 */
package org.genivi.sota.resolver.test

import eu.timepit.refined.Refined
import org.scalacheck._
import org.scalatest.FlatSpec
import org.genivi.sota.resolver.filters._
import org.genivi.sota.resolver.filters.FilterAST._


class FilterParserSpec extends FlatSpec {

  val apaS = s"""vin_matches "apa""""
  val apaF = VinMatches(Refined("apa"))

  val bepaS = s"""vin_matches "bepa""""
  val bepaF = VinMatches(Refined("bepa"))

  "The filter parser" should "parse VIN matches" in {
    assert(parseFilter(apaS) == Right(apaF))
  }

  it should "parse has package matches" in {
    assert(parseFilter(s"""has_package "cepa" "1.2.0"""") == Right(HasPackage(Refined("cepa"), Refined("1.2.0"))))
  }

  it should "not parse has package matches without a version" in {
    assert(parseFilter(s"""has_package "cepa" OR $apaS""").isLeft)
  }

  it should "parse conjunctions of filters" in {
    assert(parseFilter(s"$apaS AND $apaS") == Right(And(apaF, apaF)))
  }

  it should "parse disjunctions of filters" in {
    assert(parseFilter(s"$apaS OR $apaS") == Right(Or(apaF, apaF)))
  }

  it should "parse negations of filters" in {
    assert(parseFilter(s"NOT $apaS") == Right(Not(apaF)))
  }

  it should "parse conjunctions with higher precedence than disjunction" in {
    assert(parseFilter(s"$apaS AND $bepaS OR $apaS")
      == Right(Or(And(apaF, bepaF), apaF)))
  }

  it should "parse negation with higher precedence than conjunction" in {
    assert(parseFilter(s"NOT $apaS AND $bepaS")
      == Right(And(Not(apaF), bepaF)))
  }

  it should "allow the precedence to be changed by use of parenthesis" in {
    assert(parseFilter(s"$apaS AND ($bepaS OR $apaS)")
      == Right(And(apaF, Or(bepaF, apaF))))
  }

  it should "not parse nodes without children" in {
    assert(parseFilter("AND").isLeft
      && parseFilter(s"$apaS OR").isLeft
      && parseFilter(s"$apaS OR AND $apaS").isLeft)
  }

  it should "not parse leaves with valid regexes" in {
    assert(parseFilter(s"""vin_matches "SAJNX5745SC......"""") ===
      Right(VinMatches(Refined("SAJNX5745SC......"))))
  }

  it should "not parse leaves with invalid regexes" in {
    assert(parseFilter(s"""vin_matches "*" """).isLeft)
  }

}

class FilterQuerySpec extends ResourceWordSpec {

  import org.genivi.sota.resolver.vehicles.Vehicle
  import org.genivi.sota.resolver.packages.Package
  import org.genivi.sota.resolver.components.Component

  val vin1 = Vehicle(Refined("APABEPA1234567890"))
  val vin2 = Vehicle(Refined("APACEPA1234567890"))
  val vin3 = Vehicle(Refined("APADEPA1234567890"))
  val vin4 = Vehicle(Refined("BEPAEPA1234567890"))
  val vin5 = Vehicle(Refined("DEPAEPA1234567890"))

  val pkg1 = Package.Id(Refined("pkg1"), Refined("1.0.0"))
  val pkg2 = Package.Id(Refined("pkg2"), Refined("1.0.0"))
  val pkg3 = Package.Id(Refined("pkg3"), Refined("1.0.1"))
  val pkg4 = Package.Id(Refined("pkg4"), Refined("1.0.1"))

  val part1: Refined[String, Component.ValidPartNumber] = Refined("part1")
  val part2: Refined[String, Component.ValidPartNumber] = Refined("part2")
  val part3: Refined[String, Component.ValidPartNumber] = Refined("part3")
  val part4: Refined[String, Component.ValidPartNumber] = Refined("part4")

  val vins: Seq[(Vehicle, (Seq[Package.Id], Seq[Component.PartNumber]))] =
    List( (vin1, (List(pkg1), List(part1)))
        , (vin2, (List(pkg2), List(part2)))
        , (vin3, (List(pkg3), List(part3)))
        , (vin4, (List(pkg4), List(part4)))
        , (vin5, (List(),     List()))
        )

  def run(f: FilterAST): Seq[Vehicle] =
    vins.filter(query(f)).map(_._1)


  "Filter queries" should {

    "filter by matching VIN" in {
      run(VinMatches(Refined(".*")))       shouldBe List(vin1, vin2, vin3, vin4, vin5)
      run(VinMatches(Refined(".*BEPA.*"))) shouldBe List(vin1, vin4)
    }

    "filter by matching package" in {

      // Note that vin5 isn't matched, because it has no components!
      run(HasPackage(Refined(".*"),       Refined(".*")))    shouldBe List(vin1, vin2, vin3, vin4)

      run(HasPackage(Refined(".*"),       Refined("1.0.0"))) shouldBe List(vin1, vin2)
      run(HasPackage(Refined("pkg(3|4)"), Refined(".*")))    shouldBe List(vin3, vin4)

    }

    "filter by matching component" in {
      run(HasComponent(Refined(".*")))        shouldBe List(vin1, vin2, vin3, vin4)
      run(HasComponent(Refined("part(1|4)"))) shouldBe List(vin1, vin4)
    }

    "filter by a combination of matching VINs, packages and components" in {
      run(And(VinMatches(Refined(".*")),
              And(HasPackage(Refined(".*"), Refined(".*")),
                  HasComponent(Refined(".*"))))) shouldBe List(vin1, vin2, vin3, vin4)

      run(And(VinMatches(Refined(".*BEPA.*")),
              And(HasPackage(Refined("pkg.*"), Refined(".*")),
                  HasComponent(Refined("part4"))))) shouldBe List(vin4)
    }

  }
}

object ArbitraryFilterAST {

  def genFilterHelper(i: Int): Gen[FilterAST] = {

    def genNullary = Gen.oneOf(True, False)

    def genUnary(n: Int) = for {
        f     <- genFilterHelper(n / 2)
        unary <- Gen.oneOf(Not, Not)
      } yield unary(f)

    def genBinary(n: Int) = for {
        l      <- genFilterHelper(n / 2)
        r      <- genFilterHelper(n / 2)
        binary <- Gen.oneOf(Or, And)
      } yield binary(l, r)

    def genLeaf = Gen.oneOf(
        for {
          s <- Gen.nonEmptyContainerOf[List, Char](Gen.alphaNumChar)
          leaf <- Gen.oneOf(VinMatches, HasComponent)
        } yield leaf(Refined(s.mkString)),
        for {
          s <- Gen.nonEmptyContainerOf[List, Char](Gen.alphaNumChar)
          t <- Gen.nonEmptyContainerOf[List, Char](Gen.alphaNumChar)
        } yield HasPackage(Refined(s.mkString), Refined(t.mkString))
    )

    i match {
      case 0 => genLeaf
      case n => Gen.frequency(
        (2, genNullary),
        (8, genUnary(n)),
        (10, genBinary(n))
      )
    }
  }

  def genFilter: Gen[FilterAST] = Gen.sized(genFilterHelper)

  implicit lazy val arbFilterAST: Arbitrary[FilterAST] =
    Arbitrary(genFilter)
}

object FilterParserPropSpec extends ResourcePropSpec {

  import ArbitraryFilterAST.arbFilterAST

  property("The filter parser parses pretty printed filters") {
    forAll { f: FilterAST =>
      parseFilter(ppFilter(f)) shouldBe Right(f)
    }
  }

  property("does not parse junk") {
    forAll { f: FilterAST =>
      parseFilter(ppFilter(f) + "junk").isLeft shouldBe true
    }
  }
}
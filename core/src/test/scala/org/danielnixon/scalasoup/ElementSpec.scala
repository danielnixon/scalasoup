package org.danielnixon.elsewhere

import cats.effect.IO
import org.danielnixon.scalasoup._
import org.http4s.client.middleware.FollowRedirect
import org.scalatest._

class ElementSpec extends FlatSpec with Matchers {

  "Using an invalid regex string" should "fail to compile" in {
    val document = Document.createShell("")
    assertTypeError("""document.elementsMatchingOwnText("(")""")
  }

  /**
    * TODO Tests for CssSelectorString
    * CssSelectorString.fromString success
    * CssSelectorString.fromString failure
    * CssSelectorString.fromStringUnsafe success
    * CssSelectorString.fromStringUnsafe failure
    */

  "The select method" should "compile with a valid CSS selector" in {
    val document = Document.createShell("")
    document.select("a")
  }

  "The select method" should "fail to compile with an invalid CSS selector" in {
    val document = Document.createShell("")
    assertTypeError("""document.select("a[")""")
  }

  "The Wikipedia readme example" should "compile" in {
    import org.http4s.client.blaze._

    val httpClient = FollowRedirect[IO](maxRedirects = 3)(Http1Client[IO]().unsafeRunSync())
    val uri = "https://en.wikipedia.org/"

    val task = httpClient.expect[String](uri).map { html =>

      val doc = ScalaSoup.parse(html, uri)

      println(doc.title)
      val newsHeadlines = doc.select("#mp-itn b a")
      for (headline <- newsHeadlines) {
        println(s"${headline.attr("title")} ${headline.absUrl("href")}")
      }
    }

    () => task.unsafeRunSync()
    httpClient.shutdownNow()
  }

  // TODO: test all of the withFoo methods.
  "Simple mutation methods (withFoo)" should "leave the original element unchanged" in {
    val original = Element("div")
    val updated = original.withAddClass("foo")

    original.hasClass("foo") shouldBe false
    updated.hasClass("foo") shouldBe true
  }

  // TODO: test all of the excludingSelf methods.
  "Selecting children" should "exclude the current element, even when it matches the selector" in {
    val document = ScalaSoup.parse("""<html lang="en"><head><title>Hello, World!</title></head><body>Hello, World!</body></html>""")
    val children = document.selectChildren("*")
    val all = document.select("*")

    children.filter(_.nodeName === "#document") should be(empty)
    all.filter(_.nodeName === "#document") should not be empty
  }

  // TODO: Test HasParentElement and UnknownParentElement methods.
}
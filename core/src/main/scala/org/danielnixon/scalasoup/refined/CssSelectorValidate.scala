package org.danielnixon.scalasoup.refined

import eu.timepit.refined.api.Validate

/**
  * A refined predicate that parses JSoup CSS queries at compile time.
  *
  * @see https://github.com/fthomas/refined
  * @see https://github.com/fthomas/refined/blob/master/modules/docs/custom_predicates.md
  */
object CssSelectorValidate {
  final case class CssSelector()

  implicit def cssSelectorValidate: Validate.Plain[String, CssSelector] =
    Validate.fromPartial(org.jsoup.select.QueryParser.parse, "CssSelector", CssSelector())
}

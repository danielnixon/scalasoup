package org.danielnixon

import org.danielnixon.scalasoup.Element.{HasParentElement, UnknownParentElement}
import org.danielnixon.scalasoup.refined.CssSelectorValidate.CssSelector

import scala.language.implicitConversions

package object scalasoup extends RefinedSupport {
  implicit def elementToHasParent(element: Element[ParentState.HasParent]): HasParentElement =
    new HasParentElement(element)

  implicit def elementToUnknownParent(element: Element[_ <: ParentState]): UnknownParentElement =
    new UnknownParentElement(element)
}

/**
  * @see https://github.com/fthomas/refined
  */
trait RefinedSupport {
  import eu.timepit.refined.api.{RefType, Refined, Validate}
  import eu.timepit.refined.refineV
  import eu.timepit.refined.macros.RefineMacro
  import eu.timepit.refined.string.Regex

  import scala.language.experimental.macros

  type RegexString = String Refined Regex

  implicit def regexToString(regex: RegexString): String = regex.value

  object RegexString {
    def fromString(regex: String): Either[String, RegexString] = refineV[Regex](regex)
    def fromStringUnsafe(regex: String): RegexString = fromString(regex).fold(sys.error, identity)
  }

  type CssSelectorString = String Refined CssSelector

  implicit def cssSelectorToString(cssSelector: CssSelectorString): String = cssSelector.value

  object CssSelectorString {
    def fromString(cssSelector: String): Either[String, CssSelectorString] = refineV[CssSelector](cssSelector)
    def fromStringUnsafe(cssSelector: String): CssSelectorString = fromString(cssSelector).fold(sys.error, identity)
  }

  // We duplicate this here so ScalaSoup users don't have to import anything from any refined packages.
  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  implicit def autoRefineV[T, P](t: T)(
    implicit rt: RefType[Refined],
    v: Validate[T, P]
  ): Refined[T, P] = macro RefineMacro.impl[Refined, T, P]
}

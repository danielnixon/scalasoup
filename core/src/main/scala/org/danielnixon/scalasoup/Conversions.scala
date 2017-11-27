package org.danielnixon.scalasoup

import scala.collection.immutable.List
import scala.collection.JavaConverters._

object Conversions {
  def convertList[A <: ParentState](underlying: org.jsoup.select.Elements): List[Element[A]] =
    underlying.asScala.toList.map(Element.fromUnderlying[A])

  def convertOption[A <: ParentState](underlying: org.jsoup.nodes.Element): Option[Element[A]] =
    Option(underlying).map(Element.fromUnderlying)

  // TODO: Depend on cats-mtl and factor out this duplication?
  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  def excludingSelf(element: Element[_ <: ParentState], op: Element[_ <: ParentState] => List[Element[_ <: ParentState]]): List[Element[ParentState.HasParent]] =
    op(element).filterNot(_.underlying eq element.underlying).map(_.asInstanceOf[Element[ParentState.HasParent]])

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  def excludingSelf(element: Element[_ <: ParentState], op: Element[_ <: ParentState] => Option[Element[_ <: ParentState]]): Option[Element[ParentState.HasParent]] =
    op(element).filterNot(_.underlying eq element.underlying).map(_.asInstanceOf[Element[ParentState.HasParent]])
}

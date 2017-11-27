package org.danielnixon.scalasoup

import cats.Eq
import cats.free.Free.liftF
import org.danielnixon.scalasoup.impl._

import scala.language.implicitConversions

package object dsl {
  val modifyDocument: Modification[Document[ParentState.NoParent]] = liftF(DocumentModification(identity))

  implicit def nodeToDsl[A <: ParentState](node: Node[A]): NodeDsl[A] = new NodeDsl(node)
  implicit def textNodeToDsl[A <: ParentState](node: TextNode[A]): TextNodeDsl[A] = new TextNodeDsl(node)
  implicit def dataNodeToDsl[A <: ParentState](node: DataNode[A]): DataNodeDsl[A] = new DataNodeDsl(node)
  implicit def elementToDsl[A <: ParentState](element: Element[A]): ElementDsl[A] = new ElementDsl(element)
  implicit def documentToDsl(document: Document[ParentState.NoParent]): DocumentDsl = new DocumentDsl(document)
  implicit def attributeToDsl(attribute: Attribute): AttributeDsl = new AttributeDsl(attribute)
  implicit def attributesToDsl(attributes: Attributes): AttributesDsl = new AttributesDsl(attributes)

  // Eq instances. We don't provide instances for nodes/elements/documents because their equals methods
  // are all based on reference equality.
  implicit val attributeEq: Eq[Attribute] = Eq.fromUniversalEquals[Attribute]
  implicit val attributesEq: Eq[Attributes] = Eq.fromUniversalEquals[Attributes]
}

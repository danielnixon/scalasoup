package org.danielnixon.scalasoup.impl

import org.danielnixon.scalasoup._

sealed trait ModificationA[A]

final case class NodeModification[A, B <: ParentState](node: Node[B], mod: Node[B] => A) extends ModificationA[A]

final case class TextNodeModification[A, B <: ParentState](node: TextNode[B], mod: TextNode[B] => A) extends ModificationA[A]

final case class DataNodeModification[A, B <: ParentState](node: DataNode[B], mod: DataNode[B] => A) extends ModificationA[A]

final case class ElementModification[A, B <: ParentState](element: Element[B], mod: Element[B] => A) extends ModificationA[A]

final case class DocumentModification[A](mod: Document[ParentState.NoParent] => A) extends ModificationA[A]

final case class AttributeModification[A](attribute: Attribute, mod: Attribute => A) extends ModificationA[A]

final case class AttributesModification[A](attributes: Attributes, mod: Attributes => A) extends ModificationA[A]
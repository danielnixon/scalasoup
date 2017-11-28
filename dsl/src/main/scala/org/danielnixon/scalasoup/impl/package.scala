package org.danielnixon.scalasoup

import java.nio.charset.Charset

import cats.free.Free
import cats.free.Free.liftF

package object impl {

  type Modification[A] = Free[ModificationA, A]

  class NodeDsl[B <: ParentState](val node: Node[B]) extends AnyVal {

    private def modification[A](node: Node[B], mod: Node[B] => A): Modification[A] =
      liftF[ModificationA, A](NodeModification[A, B](node, mod))

    def removeAttr(attributeKey: String): Modification[Unit] = modification(node, _.removeAttr(attributeKey))

    def setAttr(attributeKey: String, attributeValue: String): Modification[Unit] =
      modification(node, _.setAttr(attributeKey, attributeValue))

    def clearAttributes: Modification[Unit] = modification(node, _.clearAttributes())

    def setBaseUri(baseUri: String): Modification[Unit] = modification(node, _.setBaseUri(baseUri))

    def remove(implicit ev: HasParent[B]): Modification[Unit] = modification(node, _.remove())

    def before(html: String)(implicit ev: HasParent[B]): Modification[Unit] =
      modification(node, _.before(html))

    def before(in: Node[_ <: ParentState])(implicit ev: HasParent[B]): Modification[Unit] =
      modification(node, _.before(in))

    def after(html: String)(implicit ev: HasParent[B]): Modification[Unit] =
      modification(node, _.after(html))

    def after(in: Node[_ <: ParentState])(implicit ev: HasParent[B]): Modification[Unit] =
      modification(node, _.after(in))

    def wrap(html: String)(implicit ev: HasParent[B]): Modification[Unit] =
      modification(node, _.wrap(html))

    def unwrap(implicit ev: HasParent[B]): Modification[Option[Node[ParentState.HasParent]]] =
      modification(node, _.unwrap())

    def replaceWith(in: Node[_ <: ParentState])(implicit ev: HasParent[B]): Modification[Unit] =
      modification(node, _.replaceWith(in))
  }

  class ElementDsl[B <: ParentState](val element: Element[B]) extends AnyVal {

    private def modification[A](element: Element[B], mod: Element[B] => A): Modification[A] =
      liftF[ModificationA, A](ElementModification[A, B](element, mod))

    def setTagName(tagName: String): Modification[Unit] = modification(element, _.setTagName(tagName))

    def setAttr(attributeKey: String, attributeValue: Boolean): Modification[Unit] =
      modification(element, _.setAttr(attributeKey, attributeValue))

    def appendChild(child: Node[_ <: ParentState]): Modification[Unit] = modification(element, _.appendChild(child))

    def appendTo(parent: Element[_ <: ParentState]): Modification[Unit] = modification(element, _.appendTo(parent))

    def prependChild(child: Node[_ <: ParentState]): Modification[Unit] = modification(element, _.prependChild(child))

    def insertChildren(index: Int, children: List[Node[_ <: ParentState]]): Modification[Unit] =
      modification(element, _.insertChildren(index, children))

    def appendElement(tagName: String): Modification[Element[ParentState.HasParent]] =
      modification(element, _.appendElement(tagName))

    def prependElement(tagName: String): Modification[Element[ParentState.HasParent]] =
      modification(element, _.prependElement(tagName))

    def appendText(text: String): Modification[Unit] = modification(element, _.appendText(text))

    def prependText(text: String): Modification[Unit] = modification(element, _.prependText(text))

    def append(html: String): Modification[Unit] = modification(element, _.append(html))

    def prepend(html: String): Modification[Unit] = modification(element, _.prepend(html))

    def empty: Modification[Unit] = modification(element, _.empty())

    def setText(text: String): Modification[Unit] = modification(element, _.setText(text))

    def setClassNames(classNames: Set[String]): Modification[Unit] = modification(element, _.setClassNames(classNames))

    def addClass(className: String): Modification[Unit] = modification(element, _.addClass(className))

    def removeClass(className: String): Modification[Unit] = modification(element, _.removeClass(className))

    def toggleClass(className: String): Modification[Unit] = modification(element, _.toggleClass(className))

    def setValue(value: String): Modification[Unit] = modification(element, _.setValue(value))

    def setHtml(html: String): Modification[Unit] = modification(element, _.setHtml(html))
  }

  class DocumentDsl(val document: Document[ParentState.NoParent]) extends AnyVal {

    private def modification[A](mod: Document[ParentState.NoParent] => A): Modification[A] =
      liftF[ModificationA, A](DocumentModification[A](mod))

    def setTitle(title: String): Modification[Unit] = modification(_.setTitle(title))

    def normalise: Modification[Unit] = modification(_.normalise())

    def setCharset(charset: Charset): Modification[Unit] = modification(_.setCharset(charset))

    def setUpdateMetaCharsetElement(update: Boolean): Modification[Unit] =
      modification(_.setUpdateMetaCharsetElement(update))

    def setOutputSettings(outputSettings: OutputSettings): Modification[Unit] =
      modification(_.setOutputSettings(outputSettings))

    def setQuirksMode(quirksMode: org.jsoup.nodes.Document.QuirksMode): Modification[Unit] =
      modification(_.setQuirksMode(quirksMode))

    /**
      * Clone a document and apply the specified modifications.
      *
      * @param program The modifications to apply to the document.
      * @return A clone of the document with the described modifications applied.
      */
    def withModifications(program: Modification[_]): Document[ParentState.NoParent] = {
      val clone = document.clone
      program.foldMap(Compiler(clone))
      clone
    }
  }

  class TextNodeDsl[B <: ParentState](val node: TextNode[B]) extends AnyVal {

    private def modification[A](textNode: TextNode[B], mod: TextNode[B] => A): Modification[A] =
      liftF[ModificationA, A](TextNodeModification[A, B](textNode, mod))

    def setText(text: String): Modification[Unit] = modification(node, _.setText(text))

    def splitText(offset: Int): Modification[TextNode[B]] = modification(node, _.splitText(offset))
  }

  class DataNodeDsl[B <: ParentState](val node: DataNode[B]) extends AnyVal {

    private def modification[A](dataNode: DataNode[B], mod: DataNode[B] => A): Modification[A] =
      liftF[ModificationA, A](DataNodeModification[A, B](dataNode, mod))

    def setData(data: String): Modification[Unit] = modification(node, _.setData(data))
  }

  class AttributeDsl(val attribute: Attribute) extends AnyVal {
    def setKey(key: String): Modification[Unit] = liftF(AttributeModification(attribute, _.setKey(key)))

    def setValue(value: String): Modification[Unit] = liftF(AttributeModification(attribute, _.setValue(value)))
  }

  class AttributesDsl(val attributes: Attributes) extends AnyVal {
    def put(key: String, value: String): Modification[Unit] = liftF(AttributesModification(attributes, _.put(key, value)))

    def put(key: String, value: Boolean): Modification[Unit] = liftF(AttributesModification(attributes, _.put(key, value)))

    def put(attribute: Attribute): Modification[Unit] = liftF(AttributesModification(attributes, _.put(attribute)))

    def remove(key: String): Modification[Unit] = liftF(AttributesModification(attributes, _.remove(key)))

    def removeIgnoreCase(key: String): Modification[Unit] = liftF(AttributesModification(attributes, _.removeIgnoreCase(key)))

    def addAll(incoming: Attributes): Modification[Unit] = liftF(AttributesModification(attributes, _.addAll(incoming)))
  }
}

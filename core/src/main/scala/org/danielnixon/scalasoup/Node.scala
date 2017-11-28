package org.danielnixon.scalasoup

import java.nio.charset.Charset

import org.jsoup.nodes.Document.QuirksMode
import org.jsoup.nodes.Document.OutputSettings.Syntax
import org.jsoup.nodes.Entities.EscapeMode

import scala.collection.JavaConverters._
import scala.collection.immutable.{List, Map, Set}
import scala.util.control.Exception.catching
import scala.util.matching.Regex
import Conversions._

sealed abstract class Node[A <: ParentState] private[scalasoup](private[scalasoup] val underlying: org.jsoup.nodes.Node) extends HasOwner {

  def nodeName: String = underlying.nodeName

  def hasParent: Boolean = underlying.hasParent

  def attr(attributeKey: String): String = underlying.attr(attributeKey)

  def attributes: Attributes = new Attributes(ownerDocument, underlying.attributes)

  def withAttr(attributeKey: String, attributeValue: String): Node[ParentState.NoParent] =
    withClone(_.attr(attributeKey, attributeValue))

  def hasAttr(attributeKey: String): Boolean = underlying.hasAttr(attributeKey)

  def withRemoveAttr(attributeKey: String): Node[ParentState.NoParent] = withClone(_.removeAttr(attributeKey))

  def withClearAttributes: Node[ParentState.NoParent] = withClone(_.clearAttributes())

  def baseUri: String = underlying.baseUri

  def withBaseUri(baseUri: String): Node[ParentState.NoParent] = withClone(_.setBaseUri(baseUri: String))

  // TODO: Add phantom type to Node to track whether we have a base URI or not. Require a base URI to call absUrl.
  def absUrl(attributeKey: String): String = underlying.absUrl(attributeKey)

  def childNode(index: Int): Option[Node[ParentState.HasParent]] = {
    val opt = catching[org.jsoup.nodes.Node](classOf[IndexOutOfBoundsException]).opt(underlying.childNode(index))
    opt.map(Node.fromUnderlying)
  }

  def childNodes: List[Node[ParentState.HasParent]] =
    underlying.childNodes.asScala.toList.map(Node.fromUnderlying[ParentState.HasParent])

  def childNodeSize: Int = underlying.childNodeSize

  // TODO: Knowledge of ParentState means we can expose non-optional `parent` and `parentNode`.
  def parent: Option[Node[_ <: ParentState]] = Option(underlying.parent).map(Node.fromUnderlying)

  def parentNode: Option[Node[_ <: ParentState]] = Option(underlying.parentNode).map(Node.fromUnderlying)

  def root: Node[ParentState.NoParent] = Node.fromUnderlying(underlying.root)

  override def ownerDocument: Option[Document[ParentState.NoParent]] = Option(underlying.ownerDocument).map(Document.fromUnderlying)

  def siblingNodes(implicit ev: HasParent[A]): List[Node[A]] =
    underlying.siblingNodes.asScala.toList.map(Node.fromUnderlying[A])

  def nextSibling(implicit ev: HasParent[A]): Option[Node[A]] =
    Option(underlying.nextSibling).map(Node.fromUnderlying)

  def previousSibling(implicit ev: HasParent[A]): Option[Node[A]] =
    Option(underlying.previousSibling).map(Node.fromUnderlying)

  def siblingIndex(implicit ev: HasParent[A]): Int = underlying.siblingIndex

  // TODO traverse and filter

  def outerHtml: String = underlying.outerHtml

  // TODO: Do we want to expose this?
  def html[T <: Appendable](appendable: T): Unit = { underlying.html(appendable); () }

  override def toString: String = underlying.toString

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  override def equals(other: Any): Boolean = underlying.equals(other)

  def hasSameValue(other: Any): Boolean = underlying.hasSameValue(other)

  override def clone: Node[ParentState.NoParent] = Node.fromUnderlying(underlying.clone)

  def shallowClone: Node[ParentState.NoParent] = Node.fromUnderlying(underlying.shallowClone)

  private val withClone = ScalaSoup.withClone(underlying)(_.clone())(Node.fromUnderlying[ParentState.NoParent]) _

}

object Node {
  private[scalasoup] def fromUnderlying[A <: ParentState](underlying: org.jsoup.nodes.Node): Node[A] = {
    underlying match {
      case u: org.jsoup.nodes.Element => Element.fromUnderlying[A](u)
      case u: org.jsoup.nodes.DataNode => new DataNode[A](u)
      case u: org.jsoup.nodes.TextNode => new TextNode[A](u)
      case u: org.jsoup.nodes.Comment => new Comment[A](u)
      case u: org.jsoup.nodes.DocumentType => new DocumentType[A](u)
      case u: org.jsoup.nodes.XmlDeclaration => new XmlDeclaration[A](u)
    }
  }

  private[scalasoup] implicit class MutableNode[A <: ParentState](val node: Node[A]) extends AnyVal {

    def setAttr(attributeKey: String, attributeValue: String): Unit = node.underlying.attr(attributeKey, attributeValue)

    def removeAttr(attributeKey: String): Unit = node.underlying.removeAttr(attributeKey)

    def clearAttributes(): Unit = node.underlying.clearAttributes()

    def setBaseUri(baseUri: String): Unit = node.underlying.setBaseUri(baseUri)

    def remove()(implicit ev: HasParent[A]): Unit = node.underlying.remove()

    def before(html: String)(implicit ev: HasParent[A]): Unit = { node.underlying.before(html); () }

    def before(node: Node[_ <: ParentState])(implicit ev: HasParent[A]): Unit = {
      node.underlying.before(node.underlying.clone)
      ()
    }

    def after(html: String)(implicit ev: HasParent[A]): Unit = { node.underlying.after(html); () }

    def after(n: Node[_ <: ParentState])(implicit ev: HasParent[A]): Unit = {
      node.underlying.after(n.underlying.clone)
      ()
    }

    def wrap(html: String)(implicit ev: HasParent[A]): Unit = { node.underlying.wrap(html); () }

    def unwrap()(implicit ev: HasParent[A]): Option[Node[ParentState.HasParent]] =
      Option(node.underlying.unwrap()).map(Node.fromUnderlying)

    def replaceWith(in: Node[_ <: ParentState])(implicit ev: HasParent[A]): Unit =
      node.underlying.replaceWith(in.underlying.clone)
  }
}

final class TextNode[A <: ParentState] private[scalasoup](
                                                           private[scalasoup] override val underlying: org.jsoup.nodes.TextNode
                                                         ) extends Node[A](underlying) {
  def text: String = underlying.text

  def withText(text: String): TextNode[ParentState.NoParent] = withClone(_.text(text))

  def wholeText: String = underlying.getWholeText

  def isBlank: Boolean = underlying.isBlank

  private val withClone = ScalaSoup.withClone(underlying)(n => new org.jsoup.nodes.TextNode(n.text))(new TextNode[ParentState.NoParent](_)) _
}

object TextNode {
  def createFromEncoded(encodedText: String): TextNode[ParentState.NoParent] =
    new TextNode(org.jsoup.nodes.TextNode.createFromEncoded(encodedText))

  private[scalasoup] implicit class MutableTextNode[A <: ParentState](val node: TextNode[A]) extends AnyVal {
    def setText(text: String): Unit = { node.underlying.text(text); () }

    def splitText(offset: Int): TextNode[A] = new TextNode(node.underlying.splitText(offset))
  }
}

final class DataNode[A <: ParentState] private[scalasoup](private[scalasoup] override val underlying: org.jsoup.nodes.DataNode) extends Node[A](underlying) {
  def wholeData: String = underlying.getWholeData
}

object DataNode {
  def createFromEncoded(encodedData: String, baseUri: String): DataNode[ParentState.NoParent] =
    new DataNode[ParentState.NoParent](org.jsoup.nodes.DataNode.createFromEncoded(encodedData, baseUri))

  private[scalasoup] implicit class MutableDataNode[A <: ParentState](val node: DataNode[A]) extends AnyVal {
    def setData(data: String): Unit = { node.underlying.setWholeData(data); () }
  }
}

final class Comment[A <: ParentState] private[scalasoup](private[scalasoup] override val underlying: org.jsoup.nodes.Comment) extends Node[A](underlying) {
  def data: String = underlying.getData
}

final class DocumentType[A <: ParentState] private[scalasoup](private[scalasoup] override val underlying: org.jsoup.nodes.DocumentType) extends Node[A](underlying)

final class XmlDeclaration[A <: ParentState] private[scalasoup](private[scalasoup] override val underlying: org.jsoup.nodes.XmlDeclaration) extends Node[A](underlying) {
  def name: String = underlying.name
  def wholeDeclaration: String = underlying.getWholeDeclaration
}

sealed class Element[A <: ParentState] private[scalasoup](private[scalasoup] override val underlying: org.jsoup.nodes.Element) extends Node[A](underlying) {

  def tagName: String = underlying.tagName

  def withTagName(tagName: String): Element[ParentState.NoParent] = withClone(_.tagName(tagName))

  def tag: org.jsoup.parser.Tag = underlying.tag

  def isBlock: Boolean = underlying.isBlock

  def id: String = underlying.id

  override def withAttr(attributeKey: String, attributeValue: String): Element[ParentState.NoParent] =
    withClone(_.attr(attributeKey, attributeValue))

  def withAttr(attributeKey: String, attributeValue: Boolean): Element[ParentState.NoParent] =
    withClone(_.attr(attributeKey, attributeValue))

  def dataset: Map[String, String] = underlying.dataset.asScala.toMap

  override def parent: Option[Element[_ <: ParentState]] = convertOption(underlying.parent)

  def parents: List[Element[_ <: ParentState]] = convertList(underlying.parents)

  def child(index: Int): Option[Element[ParentState.HasParent]] = {
    val opt = catching[org.jsoup.nodes.Element](classOf[IndexOutOfBoundsException]).opt(underlying.child(index))
    opt.map(Element.fromUnderlying)
  }

  def children: List[Element[ParentState.HasParent]] = convertList(underlying.children)

  def textNodes: List[TextNode[ParentState.HasParent]] =
    underlying.textNodes.asScala.toList.map(new TextNode[ParentState.HasParent](_))

  def dataNodes: List[DataNode[ParentState.HasParent]] =
    underlying.dataNodes.asScala.toList.map(new DataNode[ParentState.HasParent](_))

  def selectChildren(cssQuery: CssSelectorString): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.select(cssQuery))

  def selectFirstChild(cssQuery: CssSelectorString): Option[Element[ParentState.HasParent]] =
    excludingSelf(this, _.selectFirst(cssQuery))

  def childrenByTag(tagName: String): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByTag(tagName))

  def childById(id: String): Option[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementById(id))

  def childrenByClass(className: String): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByClass(className))

  def childrenByAttribute(key: String): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByAttribute(key))

  def childrenByAttributeStarting(keyPrefix: String): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByAttributeStarting(keyPrefix))

  def childrenByAttributeValue(key: String, value: String): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByAttributeValue(key, value))

  def childrenByAttributeValueNot(key: String, value: String): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByAttributeValueNot(key, value))

  def childrenByAttributeValueStarting(key: String, valuePrefix: String): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByAttributeValueStarting(key, valuePrefix))

  def childrenByAttributeValueEnding(key: String, valueSuffix: String): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByAttributeValueEnding(key, valueSuffix))

  def childrenByAttributeValueContaining(key: String, `match`: String): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByAttributeValueContaining(key, `match`))

  def childrenByAttributeValueMatching(key: String, regex: Regex): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByAttributeValueMatching(key, regex))

  def childrenByAttributeValueMatching(key: String, regex: RegexString): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByAttributeValueMatching(key, regex))

  def childrenByIndexLessThan(index: Int): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByIndexLessThan(index))

  def childrenByIndexGreaterThan(index: Int): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByIndexGreaterThan(index))

  def childrenByIndexEquals(index: Int): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsByIndexEquals(index))

  def childrenContainingText(searchText: String): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsContainingText(searchText))

  def childrenContainingOwnText(searchText: String): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsContainingOwnText(searchText))

  def childrenMatchingText(regex: Regex): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsMatchingText(regex))

  def childrenMatchingText(regex: RegexString): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsMatchingText(regex))

  def childrenMatchingOwnText(regex: Regex): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsMatchingOwnText(regex))

  def childrenMatchingOwnText(regex: RegexString): List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.elementsMatchingOwnText(regex))

  def allChildren: List[Element[ParentState.HasParent]] =
    excludingSelf(this, _.allElements)

  def is(cssQuery: CssSelectorString): Boolean = underlying.is(cssQuery)

  // TODO: Wrap org.jsoup.select.Evaluator
  // def is(evaluator: Evaluator): Boolean = underlying.is(evaluator)

  def withAppendChild(child: Node[_ <: ParentState]): Element[ParentState.NoParent] = withClone(_.appendChild(child.underlying.clone))

  def withPrependChild(child: Node[_ <: ParentState]): Element[ParentState.NoParent] =
    withClone(_.prependChild(child.underlying.clone))

  def withInsertChildren(index: Int, children: List[Node[_ <: ParentState]]): Element[ParentState.NoParent] =
    withClone(_.insertChildren(index, children.map(_.underlying.clone).asJava))

  def withAppendElement(tagName: String): Element[ParentState.NoParent] = withClone(_.appendElement(tagName))

  def withPrependElement(tagName: String): Element[ParentState.NoParent] = withClone(_.prependElement(tagName))

  def withAppendText(text: String): Element[ParentState.NoParent] = withClone(_.appendText(text))

  def withPrependText(text: String): Element[ParentState.NoParent] = withClone(_.prependText(text))

  def withAppend(html: String): Element[ParentState.NoParent] = withClone(_.append(html))

  def withPrepend(html: String): Element[ParentState.NoParent] = withClone(_.prepend(html))

  def withEmpty: Element[ParentState.NoParent] = withClone(_.empty())

  def cssSelector: CssSelectorString = CssSelectorString.fromStringUnsafe(underlying.cssSelector)

  def siblingElements(implicit ev: HasParent[A]): List[Element[A]] =
    convertList(underlying.siblingElements)

  def nextElementSibling(implicit ev: HasParent[A]): Option[Element[A]] =
    convertOption(underlying.nextElementSibling)

  def previousElementSibling(implicit ev: HasParent[A]): Option[Element[A]] =
    convertOption(underlying.previousElementSibling)

  def firstElementSibling(implicit ev: HasParent[A]): Option[Element[A]] =
    convertOption(underlying.firstElementSibling)

  def elementSiblingIndex(implicit ev: HasParent[A]): Int = underlying.elementSiblingIndex

  def lastElementSibling(implicit ev: HasParent[A]): Option[Element[A]] =
    convertOption(underlying.lastElementSibling)

  def text: String = underlying.text

  def wholeText: String = underlying.wholeText

  def ownText: String = underlying.ownText

  def withText(text: String): Element[ParentState.NoParent] = withClone(_.text(text))

  def hasText: Boolean = underlying.hasText

  def data: String = underlying.data

  def className: String = underlying.className

  def classNames: Set[String] = underlying.classNames.asScala.toSet

  def withClassNames(classNames: Set[String]): Element[ParentState.NoParent] =
    withClone(_.classNames(classNames.asJava))

  def hasClass(className: String): Boolean = underlying.hasClass(className)

  def withAddClass(className: String): Element[ParentState.NoParent] = withClone(_.addClass(className))

  def withRemoveClass(className: String): Element[ParentState.NoParent] = withClone(_.removeClass(className))

  def withToggleClass(className: String): Element[ParentState.NoParent] = withClone(_.toggleClass(className))

  def value: String = underlying.`val`

  def withValue(value: String): Element[ParentState.NoParent] = withClone(_.`val`(value))

  def html: String = underlying.html

  def withHtml(html: String): Element[ParentState.NoParent] = withClone(_.html(html))

  override def toString: String = underlying.toString

  override def clone: Element[ParentState.NoParent] = Element.fromUnderlying(underlying.clone)

  override def shallowClone: Element[ParentState.NoParent] = Element.fromUnderlying(underlying.shallowClone)

  private val withClone = ScalaSoup.withClone(underlying)(_.clone())(Element.fromUnderlying[ParentState.NoParent]) _
}

object Element {

  // TODO: Expose other constructors.
  def apply(tag: String): Element[ParentState.NoParent] =
    fromUnderlying[ParentState.NoParent](new org.jsoup.nodes.Element(tag))

  private[scalasoup] def fromUnderlying[A <: ParentState](underlying: org.jsoup.nodes.Element): Element[A] = {
    underlying match {
      case u: org.jsoup.nodes.Document => Document.fromUnderlying[A](u)
      case u: org.jsoup.nodes.FormElement => new FormElement[A](u)
      case u: org.jsoup.nodes.Element => new Element[A](u)
    }
  }

  implicit class HasParentElement(val element: Element[ParentState.HasParent]) extends AnyVal {
    def select(cssQuery: CssSelectorString): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.select(cssQuery))

    def selectFirst(cssQuery: CssSelectorString): Option[Element[ParentState.HasParent]] =
      convertOption(element.underlying.selectFirst(cssQuery))

    def elementsByTag(tagName: String): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByTag(tagName))

    def elementById(id: String): Option[Element[ParentState.HasParent]] =
      convertOption(element.underlying.getElementById(id))

    def elementsByClass(className: String): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByClass(className))

    def elementsByAttribute(key: String): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByAttribute(key))

    def elementsByAttributeStarting(keyPrefix: String): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByAttributeStarting(keyPrefix))

    def elementsByAttributeValue(key: String, value: String): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByAttributeValue(key, value))

    def elementsByAttributeValueNot(key: String, value: String): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByAttributeValueNot(key, value))

    def elementsByAttributeValueStarting(key: String, valuePrefix: String): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByAttributeValueStarting(key, valuePrefix))

    def elementsByAttributeValueEnding(key: String, valueSuffix: String): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByAttributeValueEnding(key, valueSuffix))

    def elementsByAttributeValueContaining(key: String, `match`: String): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByAttributeValueContaining(key, `match`))

    def elementsByAttributeValueMatching(key: String, regex: Regex): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByAttributeValueMatching(key, regex.pattern))

    def elementsByAttributeValueMatching(key: String, regex: RegexString): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByAttributeValueMatching(key, regex))

    def elementsByIndexLessThan(index: Int): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByIndexLessThan(index))

    def elementsByIndexGreaterThan(index: Int): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByIndexGreaterThan(index))

    def elementsByIndexEquals(index: Int): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsByIndexEquals(index))

    def elementsContainingText(searchText: String): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsContainingText(searchText))

    def elementsContainingOwnText(searchText: String): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsContainingOwnText(searchText))

    def elementsMatchingText(regex: Regex): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsMatchingText(regex.pattern))

    def elementsMatchingText(regex: RegexString): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsMatchingText(regex))

    def elementsMatchingOwnText(regex: Regex): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsMatchingOwnText(regex.pattern))

    def elementsMatchingOwnText(regex: RegexString): List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getElementsMatchingOwnText(regex))

    def allElements: List[Element[ParentState.HasParent]] =
      convertList(element.underlying.getAllElements)
  }

  implicit class UnknownParentElement(val element: Element[_ <: ParentState]) extends AnyVal {
    def select(cssQuery: CssSelectorString): List[Element[_ <: ParentState]] =
      convertList(element.underlying.select(cssQuery))

    def selectFirst(cssQuery: CssSelectorString): Option[Element[_ <: ParentState]] =
      convertOption(element.underlying.selectFirst(cssQuery))

    def elementsByTag(tagName: String): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByTag(tagName))

    def elementById(id: String): Option[Element[_ <: ParentState]] =
      convertOption(element.underlying.getElementById(id))

    def elementsByClass(className: String): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByClass(className))

    def elementsByAttribute(key: String): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByAttribute(key))

    def elementsByAttributeStarting(keyPrefix: String): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByAttributeStarting(keyPrefix))

    def elementsByAttributeValue(key: String, value: String): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByAttributeValue(key, value))

    def elementsByAttributeValueNot(key: String, value: String): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByAttributeValueNot(key, value))

    def elementsByAttributeValueStarting(key: String, valuePrefix: String): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByAttributeValueStarting(key, valuePrefix))

    def elementsByAttributeValueEnding(key: String, valueSuffix: String): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByAttributeValueEnding(key, valueSuffix))

    def elementsByAttributeValueContaining(key: String, `match`: String): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByAttributeValueContaining(key, `match`))

    def elementsByAttributeValueMatching(key: String, regex: Regex): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByAttributeValueMatching(key, regex.pattern))

    def elementsByAttributeValueMatching(key: String, regex: RegexString): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByAttributeValueMatching(key, regex))

    def elementsByIndexLessThan(index: Int): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByIndexLessThan(index))

    def elementsByIndexGreaterThan(index: Int): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByIndexGreaterThan(index))

    def elementsByIndexEquals(index: Int): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsByIndexEquals(index))

    def elementsContainingText(searchText: String): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsContainingText(searchText))

    def elementsContainingOwnText(searchText: String): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsContainingOwnText(searchText))

    def elementsMatchingText(regex: Regex): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsMatchingText(regex.pattern))

    def elementsMatchingText(regex: RegexString): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsMatchingText(regex))

    def elementsMatchingOwnText(regex: Regex): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsMatchingOwnText(regex.pattern))

    def elementsMatchingOwnText(regex: RegexString): List[Element[_ <: ParentState]] =
      convertList(element.underlying.getElementsMatchingOwnText(regex))

    def allElements: List[Element[_ <: ParentState]] =
      convertList(element.underlying.getAllElements)
  }

  private[scalasoup] implicit class MutableDocument[A <: ParentState](val element: Element[A]) extends AnyVal {

    def setTagName(tagName: String): Unit = element.underlying.tagName(tagName)

    def setAttr(attributeKey: String, attributeValue: Boolean): Unit = {
      element.underlying.attr(attributeKey, attributeValue)
      ()
    }

    def appendChild(child: Node[_ <: ParentState]): Unit = { element.underlying.appendChild(child.underlying.clone); () }

    def appendTo(parent: Element[_ <: ParentState]): Unit = { element.underlying.appendTo(parent.underlying.clone); () }

    def prependChild(child: Node[_ <: ParentState]): Unit = { element.underlying.prependChild(child.underlying.clone); () }

    def insertChildren(index: Int, children: List[Node[_ <: ParentState]]): Unit = {
      element.underlying.insertChildren(index, children.map(_.underlying.clone).asJava)
      ()
    }

    def insertChildren(index: Int, children: Node[_ <: ParentState]*): Unit = insertChildren(index, children.toList)

    def appendElement(tagName: String): Element[ParentState.HasParent] = Element.fromUnderlying(element.underlying.appendElement(tagName))

    def prependElement(tagName: String): Element[ParentState.HasParent] = Element.fromUnderlying(element.underlying.prependElement(tagName))

    def appendText(text: String): Unit = { element.underlying.appendText(text); () }

    def prependText(text: String): Unit = { element.underlying.prependText(text); () }

    def append(html: String): Unit = { element.underlying.append(html: String); () }

    def prepend(html: String): Unit = { element.underlying.prepend(html: String); () }

    def empty(): Unit = { element.underlying.empty(); () }

    def setText(text: String): Unit = { element.underlying.text(text); () }

    def setClassNames(classNames: Set[String]): Unit = element.underlying.classNames(classNames.asJava)

    def addClass(className: String): Unit = { element.underlying.addClass(className); () }

    def removeClass(className: String): Unit = { element.underlying.removeClass(className); () }

    def toggleClass(className: String): Unit = { element.underlying.toggleClass(className); () }

    def setValue(value: String): Unit = { element.underlying.`val`(value); () }

    def setHtml(html: String): Unit = { element.underlying.html(html); () }
  }
}

final class FormElement[A <: ParentState] private[scalasoup](private[scalasoup] override val underlying: org.jsoup.nodes.FormElement) extends Element[A](underlying) {
  def elements: List[Element[ParentState.HasParent]] =
    convertList[ParentState.HasParent](underlying.elements)

  def formData: List[(String, String)] = underlying.formData.asScala.toList.map(kv => (kv.key, kv.value))
}

trait HasOwner {
  def ownerDocument: Option[Document[ParentState.NoParent]]
}

final class Document[A <: ParentState] private[scalasoup] (private[scalasoup] override val underlying: org.jsoup.nodes.Document) extends Element[A](underlying) {
  def location: String = underlying.location

  def head: Option[Element[ParentState.HasParent]] = convertOption(underlying.head)

  def body: Option[Element[ParentState.HasParent]] = convertOption(underlying.body)

  def title: String = underlying.title

  def withTitle(title: String): Document[ParentState.NoParent] = withClone(_.title(title))

  def createElement(tagName: String): Element[ParentState.NoParent] =
    Element.fromUnderlying(underlying.createElement(tagName))

  def withNormalise(): Document [ParentState.NoParent]= withClone(_.normalise())

  def charset: Charset = underlying.charset

  def withCharset(charset: Charset): Document[ParentState.NoParent] = withClone(_.charset(charset))

  def updateMetaCharsetElement: Boolean = underlying.updateMetaCharsetElement

  def withUpdateMetaCharsetElement(update: Boolean): Document[ParentState.NoParent] =
    withClone(_.updateMetaCharsetElement(update))

  override def clone: Document[ParentState.NoParent] = Document.fromUnderlying(underlying.clone)

  def outputSettings: OutputSettings = OutputSettings.fromUnderlying(ownerDocument, underlying.outputSettings)

  def withOutputSettings(outputSettings: OutputSettings): Document[ParentState.NoParent] =
    withClone(_.outputSettings(outputSettings.underlying))

  def quirksMode: QuirksMode = underlying.quirksMode

  def withQuirksMode(quirksMode: QuirksMode): Document[ParentState.NoParent] = withClone(_.quirksMode(quirksMode))

  private val withClone = ScalaSoup.withClone(underlying)(_.clone())(Document.fromUnderlying[ParentState.NoParent]) _
}

object Document {

  def apply(baseUri: String): Document[ParentState.NoParent] =
    Document.fromUnderlying(new org.jsoup.nodes.Document(baseUri))

  def createShell(baseUri: String): Document[ParentState.NoParent] =
    Document.fromUnderlying(org.jsoup.nodes.Document.createShell(baseUri))

  private[scalasoup] def fromUnderlying[A <: ParentState](underlying: org.jsoup.nodes.Document): Document[A] =
    new Document(underlying)

  private[scalasoup] implicit class MutableDocument[A <: ParentState](val document: Document[A]) extends AnyVal {

    def setTitle(title: String): Unit = document.underlying.title(title)

    def normalise(): Unit = { document.underlying.normalise(); () }

    def setCharset(charset: Charset): Unit = document.underlying.charset(charset)

    def setUpdateMetaCharsetElement(update: Boolean): Unit = document.underlying.updateMetaCharsetElement(update)

    def setOutputSettings(outputSettings: OutputSettings): Unit = {
      document.underlying.outputSettings(outputSettings.underlying)
      ()
    }

    def setQuirksMode(quirksMode: QuirksMode): Unit = {
      document.underlying.quirksMode(quirksMode)
      ()
    }
  }
}

final case class OutputSettings(
                           override val ownerDocument: Option[Document[ParentState.NoParent]],
                           escapeMode: EscapeMode,
                           charset: Charset,
                           syntax: Syntax,
                           prettyPrint: Boolean,
                           outline: Boolean,
                           indentAmount: Int
                         ) extends HasOwner {

  private[scalasoup] val underlying: org.jsoup.nodes.Document.OutputSettings = {
    new org.jsoup.nodes.Document.OutputSettings()
      .escapeMode(escapeMode)
      .charset(charset)
      .syntax(syntax)
      .prettyPrint(prettyPrint)
      .outline(outline)
      .indentAmount(indentAmount)
  }
}

object OutputSettings {
  private[scalasoup] def fromUnderlying(
                                         ownerDocument: Option[Document[ParentState.NoParent]],
                                         underlying: org.jsoup.nodes.Document.OutputSettings
                                       ): OutputSettings = {
    OutputSettings(
      ownerDocument,
      underlying.escapeMode,
      underlying.charset,
      underlying.syntax,
      underlying.prettyPrint,
      underlying.outline,
      underlying.indentAmount
    )
  }
}
package org.danielnixon.scalasoup

import scala.collection.JavaConverters._
import scala.collection.Iterator
import scala.collection.immutable.{Iterable, Map}

final class Attribute(
                       override val ownerDocument: Option[Document[ParentState.NoParent]],
                       private[scalasoup] val underlying: org.jsoup.nodes.Attribute
                     ) extends HasOwner {

  def key: String = underlying.getKey

  def withKey(key: String): Attribute = withClone(_.setKey(key))

  def value: String = underlying.getValue

  def withValue(value: String): Attribute = withClone(_.setValue(value))

  def html: String = underlying.html

  override def toString: String = underlying.toString

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  override def equals(other: Any): Boolean = underlying.equals(other)

  override def hashCode: Int = underlying.hashCode

  override def clone: Attribute = new Attribute(None, underlying.clone)

  private val withClone = ScalaSoup.withClone(underlying)(_.clone())(new Attribute(None, _)) _
}

object Attribute {
  def createFromEncoded(unencodedKey: String, encodedValue: String): org.jsoup.nodes.Attribute = {
    org.jsoup.nodes.Attribute.createFromEncoded(unencodedKey, encodedValue)
  }

  private[scalasoup] implicit class MutableAttribute(val attribute: Attribute) extends AnyVal {
    def setKey(key: String): Unit = attribute.underlying.setKey(key)

    def setValue(value: String): Unit = attribute.underlying.setValue(value)
  }
}

final class Attributes(
                        override val ownerDocument: Option[Document[ParentState.NoParent]],
                        private[scalasoup] val underlying: org.jsoup.nodes.Attributes
                      ) extends Iterable[Attribute] with HasOwner {
  def get(key: String): String = underlying.get(key)

  def withPut(key: String, value: String): Attributes = withClone(_.put(key, value))

  def withPut(key: String, value: Boolean): Attributes = withClone(_.put(key, value))

  def withPut(attribute: Attribute): Attributes = withClone(_.put(attribute.underlying.clone))

  def withRemove(key: String): Attributes = withClone(_.remove(key))

  def withRemoveIgnoreCase(key: String): Attributes = withClone(_.removeIgnoreCase(key))

  def hasKey(key: String): Boolean = underlying.hasKey(key)

  def hasKeyIgnoreCase(key: String): Boolean = underlying.hasKeyIgnoreCase(key)

  override def size: Int = underlying.size

  def withAddAll(incoming: Attributes): Attributes = withClone(_.addAll(incoming.underlying.clone))

  def dataset: Map[String, String] = underlying.dataset.asScala.toMap

  def html: String = underlying.html

  override def toString: String = underlying.toString

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  override def equals(other: Any): Boolean = underlying.equals(other)

  override def hashCode: Int = underlying.hashCode

  override def clone: Attributes = new Attributes(None, underlying.clone)

  private val withClone = ScalaSoup.withClone(underlying)(_.clone())(new Attributes(None, _)) _

  override val iterator: Iterator[Attribute] = underlying.iterator.asScala.map(new Attribute(ownerDocument, _))
}

object Attributes {
  private[scalasoup] implicit class MutableAttributes(val attributes: Attributes) extends AnyVal {
    def put(key: String, value: String): Unit = attributes.underlying.put(key, value)

    def put(key: String, value: Boolean): Unit = attributes.underlying.put(key, value)

    def put(attribute: Attribute): Unit = attributes.underlying.put(attribute.underlying.clone)

    def remove(key: String): Unit = attributes.underlying.remove(key)

    def removeIgnoreCase(key: String): Unit = attributes.underlying.removeIgnoreCase(key)

    def addAll(incoming: Attributes): Unit = attributes.underlying.addAll(incoming.underlying.clone)
  }
}
package org.danielnixon.scalasoup

import org.danielnixon.scalasoup.Whitelist.WhitelistCall

final case class Whitelist private[scalasoup] (
                                                private [scalasoup] val seed: () => org.jsoup.safety.Whitelist,
                                                private [scalasoup] val calls: List[WhitelistCall]
                                              ) {
  def addTags(tags: String*): Whitelist =
    withCopy(_.addTags(tags: _*))

  def removeTags(tags: String*): Whitelist =
    withCopy(_.removeTags(tags: _*))

  def addAttributes(tag: String, attributes: String*): Whitelist =
    withCopy(_.addAttributes(tag, attributes: _*))

  def removeAttributes(tag: String, attributes: String*): Whitelist =
    withCopy(_.removeAttributes(tag, attributes: _*))

  def addEnforcedAttribute(tag: String, attribute: String, value: String): Whitelist =
    withCopy(_.addEnforcedAttribute(tag, attribute, value))

  def removeEnforcedAttribute(tag: String, attribute: String): Whitelist =
    withCopy(_.removeEnforcedAttribute(tag, attribute))

  def preserveRelativeLinks(preserve: Boolean): Whitelist =
    withCopy(_.preserveRelativeLinks(preserve))

  def addProtocols(tag: String, attribute: String, protocols: String*): Whitelist =
    withCopy(_.addProtocols(tag, attribute, protocols: _*))

  def removeProtocols(tag: String, attribute: String, removeProtocols: String*): Whitelist =
    withCopy(_.removeProtocols(tag, attribute, removeProtocols: _*))

  private[scalasoup] def underlying: org.jsoup.safety.Whitelist = {
    val whitelist = seed()
    calls.foreach(call => call(whitelist))
    whitelist
  }

  private def withCopy(call: WhitelistCall) = copy(calls = calls :+ call)
}

object Whitelist {
  type WhitelistCall = org.jsoup.safety.Whitelist => Unit

  def none: Whitelist = Whitelist(() => org.jsoup.safety.Whitelist.none(), List.empty[WhitelistCall])

  def simpleText: Whitelist = Whitelist(() => org.jsoup.safety.Whitelist.none(), List.empty[WhitelistCall])

  def basic: Whitelist = Whitelist(() => org.jsoup.safety.Whitelist.none(), List.empty[WhitelistCall])

  def basicWithImages: Whitelist = Whitelist(() => org.jsoup.safety.Whitelist.none(), List.empty[WhitelistCall])

  def relaxed: Whitelist = Whitelist(() => org.jsoup.safety.Whitelist.none(), List.empty[WhitelistCall])
}
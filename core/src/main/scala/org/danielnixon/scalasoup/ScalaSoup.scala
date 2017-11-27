package org.danielnixon.scalasoup

object ScalaSoup {
  def parse(html: String, baseUri: String): Document[ParentState.NoParent] =
    new Document(org.jsoup.Jsoup.parse(html, baseUri))

  def parse(html: String, baseUri: String, parser: org.jsoup.parser.Parser): Document[ParentState.NoParent] =
    new Document(org.jsoup.Jsoup.parse(html, baseUri, parser))

  def parse(html: String): Document[ParentState.NoParent] = new Document(org.jsoup.Jsoup.parse(html))

  def parseBodyFragment(bodyHtml: String, baseUri: String): Document[ParentState.NoParent] =
    new Document(org.jsoup.Jsoup.parseBodyFragment(bodyHtml, baseUri))

  def parseBodyFragment(bodyHtml: String): Document[ParentState.NoParent] =
    new Document(org.jsoup.Jsoup.parseBodyFragment(bodyHtml))

  def clean(bodyHtml: String, baseUri: String, whitelist: Whitelist): String =
    org.jsoup.Jsoup.clean(bodyHtml, baseUri, whitelist.underlying)

  def clean(bodyHtml: String, whitelist: Whitelist): String =
    org.jsoup.Jsoup.clean(bodyHtml, whitelist.underlying)

  def clean(bodyHtml: String, baseUri: String, whitelist: Whitelist, outputSettings: OutputSettings): String =
    org.jsoup.Jsoup.clean(bodyHtml, baseUri, whitelist.underlying, outputSettings.underlying)

  def isValid(bodyHtml: String, whitelist: Whitelist): Boolean = org.jsoup.Jsoup.isValid(bodyHtml, whitelist.underlying)

  private[scalasoup] def withClone[A <: java.lang.Cloneable, B](underlying: A)(clone: A => A)(ctor: A => B)(op: A => Unit): B = {
    val c = clone(underlying)
    op(c)
    ctor(c)
  }
}


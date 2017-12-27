ScalaSoup [![Build Status](https://travis-ci.org/danielnixon/scalasoup.svg?branch=master)](https://travis-ci.org/danielnixon/scalasoup)
=========

A pure, typeful, idiomatic Scala wrapper around [JSoup](https://jsoup.org/).

Why ScalaSoup?
--------------

1. We keep the JSoup API basically intact, unless doing so clashes with any of the below.
2. Unlike vanilla JSoup, everything in ScalaSoup is immutable.
3. ScalaSoup endeavours to replace all of JSoup's partial functions (those that might return null or throw an exception) with total functions (those that do neither of these barbaric things).
    1. We've replaced all nullable return types with `Option`s. No null references, no `NullPointerException`s.
    2. We return `Option`s instead of throwing `IndexOutOfBoundsException`s.
    3. We encode constraints in types instead of throwing exceptions at runtime. For example, if you call JSoup's `element.remove()` on an element that doesn't have a parent, it will throw. In ScalaSoup this _won't even compile_ (more on this below).
4. We use Scala collection types, Scala regexes, etc instead of the Java equivalents used by vanilla JSoup.
5. We drop Java-style `get` prefixes and rename identifiers that are reserved in Scala. For example `getElementsByTag()` becomes simply `elementsByTag`, and `val` (which is a keyword) becomes `value`. JSoup uses `get` prefixes inconsistently, e.g. `Element.wholeText` vs `TextNode.getWholeText`. In ScalaSoup these are both `wholeText`.
6. We support simple mutation by replacing setter methods with `withFoo` methods. The `withFoo` methods return a clone of the object with the modification applied. The original is left unchanged (more on this below).
7. We don't expose the `parse` overloads that perform http requests because JSoup's built-in http client is impure and blocking.
8. We support more complex mutation by exposing a Free Monad-based DSL (more on this below).

Why not ScalaSoup?
------------------

1. You want performance at the cost of correctness. A `def parent: Element` method that returns `null` is probably faster than an honest `def parent: Option[Element]` but I don't care.

Usage
-----

Add the dependency to your `build.sbt`:
```scala
libraryDependencies += "org.danielnixon" %% "scalasoup" % "0.1.0-SNAPSHOT"
```

Then import the `scalasoup` package and use the `ScalaSoup` object as your entrypoint everywhere you would have used `Jsoup`.
```scala
import org.danielnixon.scalasoup._

ScalaSoup.parse(...)
```

Example
-------

Let's translate the [Wikipedia example from the JSoup homepage](https://jsoup.org/).
The first thing to note is that JSoup's built-in http client is impure and blocking, so ScalaSoup doesn't expose it. You probably already use a library like [play-ws](https://github.com/playframework/play-ws) or [http4s](http://http4s.org/v0.17/client/). We encourage you to keep using whatever http library you're already using. For this example we'll use http4s. 

```scala
import org.http4s.client.blaze._
import org.danielnixon.scalasoup._

val httpClient = FollowRedirect[IO](maxRedirects = 3)(Http1Client[IO]().unsafeRunSync())
val uri = "https://en.wikipedia.org/"

val task = httpClient.expect[String](uri) map { html =>

  val doc = ScalaSoup.parse(html, uri)

  println(doc.title)
  val newsHeadlines = doc.select("#mp-itn b a")
  for (headline <- newsHeadlines) {
    println(s"${headline.attr("title")} ${headline.absUrl("href")}")
  }
}

task.unsafeRunSync()
httpClient.shutdownNow()
```

Simple Mutation
---------------

JSoup allows you to mutate documents and their constituent parts (elements, nodes, attributes, etc) in-place. ScalaSoup disables this in order to avoid side-effects. In ScalaSoup everything is effectively immutable. For example, JSoup's `addClass` method--which mutates the element on which it is called--is not exposed by ScalaSoup.

So what do we do instead? We could create a copy of an element, make our changes to the copy and leave the original untouched.

This approach is actually possible in vanilla JSoup. Before moving on, let's see what that might look like:

```scala
def withAddClass(element: org.jsoup.nodes.Element, className: String): org.jsoup.nodes.Element = {
  val updatedElement = element.clone
  updatedElement.addClass(className)
  updatedElement
}

val originalElement = new org.jsoup.nodes.Element("div")
val updatedElement = withAddClass(originalElement, "foo")

originalElement.hasClass("foo") // false
updatedElement.hasClass("foo") // true
```

This works but it has a few flaws.

1. We're fighting JSoup and it shows.
2. Perhaps crucially, this doesn't actually prevent us from mutating an existing element.
3. It's verbose, so it's temping to avoid this approach and just mutate existing elements.
4. It's pretty noisy and the interesting part is obscured by machinery. This has implications for readability, maintainability, etc.

Let's see the same approach using ScalaSoup:

```scala
val originalElement = Element("div")
val updatedElement = originalElement.withAddClass("foo")

originalElement.hasClass("foo") // false
updatedElement.hasClass("foo") // true
```

A few observations:

1. In ScalaSoup, it's impossible to call JSoup's `addClass` directly.
2. ScalaSoup provides `withAddClass` for you. It does essentially the same thing as the method we wrote in the example above.
3. ScalaSoup provides `withFoo` alternatives for all of JSoup's mutating methods (none of which are exposed directly).
4. ScalaSoup calls JSoup's `addClass` under the covers, so the mutation is still happening (ScalaSoup is just a wrapper, remember). The crucial point is that the mutation is controlled such that it can only happen on a clone of an existing element and can't be directly observed. Once the modified clone is returned to you it is effectively immutable. Further changes via `withFoo` methods will create additional clones.
5. ScalaSoup reverses JSoup's priorities. In ScalaSoup, it's easy to create modified copies of elements but difficult to mutate existing elements. In JSoup, it's difficult to create modified copies but (too) easy to mutate existing elements.

This `withFoo` approach will get you a fair way. If you need something more powerful, see the next section.

Mutation DSL
------------

One limitation of the `withFoo` approach (above) is that you incur a performance penalty associated with creating clones _every time_ you call a `withFoo` method. For example, `element.withAddClass("foo").withAppendElement("div")` will result in _two_ clones.

It'd be nice if we could _batch_ our modifications and incur the cloning penalty only once per batch of modifications. This is exactly what ScalaSoup's mutation DSL gives us. For the curious, the mutation DSL is implemented using a [Cats Free Monad](https://typelevel.org/cats/datatypes/freemonad.html).

Note that in order to use the DSL you need to add an additional dependency to your `build.sbt`:
```scala
libraryDependencies += "org.danielnixon" %% "scalasoup-dsl" % "0.1.0-SNAPSHOT"
```

Here's an example that makes two changes to a document, incurring the cloning cost only once.

```scala
import org.danielnixon.scalasoup._
import org.danielnixon.scalasoup.dsl._

val modifications = for {
  document <- modifyDocument
  _        <- document.setTitle("New Title")
  _        <- document.setHtml("New HTML")
} yield ()

val originalDocument = ScalaSoup.parse(...)
val updatedDocument = originalDocument.withModifications(modifications) 
```

Some things to observe:

1. We need an additional `dsl` wildcard import.
2. Our entry-point to the DSL is `modifyDocument`.
3. Using a for comprehension, we assemble a _description_ of our modifications.
4. The description of our modifications doesn't actually do anything (yet).
5. We execute our modifications by calling `withModifications` on a document. It is at this point that the document is cloned.
6. The cloned document is modified using the underlying JSoup methods and an immutable wrapper is returned to us.
7. The DSL consistently prefixes the mutating methods with `set` (e.g. `setTitle`, `setHtml`). JSoup _almost_ never prefixes setters with `set`. These are all mutating methods in Jsoup: `setBaseUri`, `setWholeData`, `title`, `html`. In ScalaSoup these are always prefixed with `set` and _only_ appear in the DSL.

Here's an example that removes `target` attributes from _all_ `a` tags. Note the use of Cats's `foldMapM` (and the additional `cats` import).

```scala
import cats.implicits._
import org.danielnixon.scalasoup._
import org.danielnixon.scalasoup.dsl._

val modifications = for {
  document <- modifyDocument
  _        <- document.selectChildren("a").foldMapM(_.removeAttr("target"))
} yield document

val doc = ScalaSoup.parse("<a target=\"_blank\"></a>")

val result = doc.withModifications(modifications)
```

Here's an example that builds one DSL program based on another:

```scala
val selectLinksProgram = for {
  document <- modifyDocument
} yield document.selectChildren("a")

val modifications = for {
  links <- selectLinksProgram
  _     <- links.foldMapM(_.addClass("foo"))
} yield ()

val doc = ScalaSoup.parse("<a></a>")
val updated = doc.withModifications(modifications)
```

ParentState
-----------

A number of methods in JSoup throw an exception if the element on which they are called doesn't have a parent.

Here's an example:

```scala
val doc = org.jsoup.Jsoup.parse("")
// Throws IllegalArgumentException because you can't remove something from its parent if it _has no_ parent.
// This should throw an IllegalStateException instead, but what matters is that it throws at all.
doc.remove()
```

Let's try that in ScalaSoup:

```scala
val doc = ScalaSoup.parse("")
doc.remove
```

This time, the invalid program _won't even compile_:

```
[error] Foo.scala:12:9: Cannot prove that this node has a parent. You can only call this method on a node with a parent.
[error]     doc.remove
[error] 
```

ScalaSoup introduces the concept of a `ParentState` [phantom type](https://blog.codecentric.de/en/2016/02/phantom-types-scala/). All `Nodes` (including `Elements`, `Documents`, etc) have a `ParentState` type parameter, which tells us _at compile time_ whether a node has a parent or not. All the methods that would throw in JSoup (like `remove`) are constrained such that you can _only_ call them on nodes that have a parent. We've eliminated an entire class of runtime exceptions!

Here are some points to keep in mind:
1. Newly constructed `Document`s, including those returned by `ScalaSoup.parse`, etc never have a parent.
2. Clones never have a parent (because one of the things JSoup always does when cloning is clear the clone's parent).
3. Children returned from methods like `Document.head`, `Document.body`, `Element.child`, `Element.children`, `FormElement.elements` and others always have a parent.
4. JSoup does _not_ make elements returned by `Document.createElement` a child of the document, so they never have a parent in ScalaSoup.

This `ParentState` scheme works out reasonably well almost everywhere. There are one or two places where it isn't as simple as we'd like it to be.

The first is the set of methods including `select`, `selectFirst`, `elementsByTag`, `elementsMatchingText`, `allElements`, etc (all sans the `get` prefix from JSoup, of course).

These methods all return lists (or options) of elements. The returned value could include children of the current element _and the current element itself_.

This raises a question. What should the ParentState of the returned list be?

If the current element is known to have a parent, then we can confidently return `List[Element[ParentState.HasParent]]`. And this is actually how ScalaSoup works. For example:

```scala
// Given some element with a parent (a `body` element in this case).
val element: Element[ParentState.HasParent] = ScalaSoup.parse("<div></div>").body.get

// Select all elements using a CSS wildcard. This will include the original body element and its child div.
val results: List[Element[ParentState.HasParent]] = element.select("*")
```

But what if we don't know the parent state of the current element (or if the current element is known to not have a parent)? In that case we cannot know the parent state of the returned list. For example:

```scala
// Given some element without a parent (a document element in this case).
val document: Document[ParentState.NoParent] = ScalaSoup.parse("<div></div>")

// Select all div elements in the document. We (humans) know that the returned list will only include elements with parents, but we haven't persuaded the compiler.
val results: List[Element[_]] = document.select("div")
```

There are a couple of ways we can work around this. One is to ensure we call the method (`select` in these examples) on something we know has a parent. For example:

```scala
val document: Document[ParentState.NoParent] = ScalaSoup.parse("<div></div>")

// We go via the body element, which is known to have a parent.
val results: List[Element[ParentState.HasParent]] = document.body.toList.flatMap(_.select("*"))
```

Another, perhaps nicer, solution is to use one of the new methods introduced in ScalaSoup (i.e. not present in vanilla JSoup) for this purpose.

Instead of `select` we can call `selectChildren`, which is equivalent in all respects except that it will never include the current element, allowing us to know with confidence that the returned elements all have a parent.

```scala
val document: Document[ParentState.NoParent] = ScalaSoup.parse("<div></div>")

// Using `selectChildren`, we know that all the results will have a parent.
val results: List[Element[ParentState.HasParent]] = document.selectChildren("*")
```

For the other methods, take a look at `selectFirstChild` in place of `selectFirst`, `allChildren` in place of `allElements`, `childrenByClass` in place of `elementsByClass` and so on.

The second issue is raised by the `parent`, `parents` and `parentNode` methods. In these cases we _don't know_ at compile time whether or not the parent has a parent of its own.

This will be annoying in cases like this:

```scala
val modifications = for {
  doc        <- modifyDocument
  link       =  doc.selectFirstChild("a")
  linkParent =  link.flatMap(_.parent)
  _          <- linkParent.foldMapM(_.remove)
} yield ()

val document = ScalaSoup.parse("<div><a></a></div>")

val updated = document.withModifications(modifications)
```

In the above example we:
1. select the first `a` tag,
2. get its parent,
3. try to remove the parent from the document.

This fails to compile because we can't prove that the `a` tag's parent has a parent of its own. Recall that we can't remove an element unless it has a parent. Doing so would manifest as an exception in JSoup.

There are a couple of workarounds:

The bluntest (and least safe) solution is to just resort to using `asInstanceOf`:

```scala
val modifications = for {
  doc        <- modifyDocument
  link       =  doc.selectFirstChild("a")
  linkParent =  link.flatMap(_.parent.map(_.asInstanceOf[Element[ParentState.HasParent]]))
  _          <- linkParent.foldMapM(_.remove)
} yield ()
```

A safer solution is to rewrite our program so that it no longer has to work its way back _up_ the tree of elements. In this example let's rewrite using the [`has` pseudo-class](https://developer.mozilla.org/en-US/docs/Web/CSS/:has), which is supported by JSoup:

```scala
val modifications = for {
  doc        <- modifyDocument
  linkParent =  doc.selectFirstChild("div:has(a)")
  _          <- linkParent.foldMapM(_.remove)
} yield ()
```

Pattern matching
----------------

Consider the following (flawed) JSoup program:

```scala
import scala.collection.JavaConverters._

val doc: org.jsoup.nodes.Document = ???

val foo = doc.childNodes.asScala.map {
  case x: org.jsoup.nodes.Element => x.html
  case x: org.jsoup.nodes.DataNode => x.getWholeData
}
```

Can you see the problem? The pattern match is not exhaustive. If any of the child nodes is something other than an element or a data node, we're going to throw a `MatchError` at runtime. Worse, the compiler cannot warn us about it.

Let's re-write our JSoup program using ScalaSoup:

```scala
val doc: Document[_] = ???

val foo = doc.childNodes.map {
  case x: Element[_] => x.html
  case x: DataNode[_] => x.wholeData
}
```

This time, we see the problem clearly:

```
[warn] Foo.scala:24:34: match may not be exhaustive.
[warn] It would fail on the following inputs: Comment(), DocumentType(), TextNode(), XmlDeclaration()
[warn]     val foo = doc.childNodes.map {
[warn]                                  ^
[warn] one warning found
```

In ScalaSoup--unlike in JSoup--the `Node`/`Element`/`Document`/etc class hierarchy is _sealed_. This allows the compiler to determine when a match is not exhaustive.

Compile-time validation of regular expressions and CSS selectors
----------------------------------------------------------------

Consider this JSoup program that finds elements matching a regex (`(foo)` in this case):

```scala
val matchingElements = org.jsoup.Jsoup.parse("<div>foo</div>").getElementsMatchingText("(foo)")
```

But what if we forgot to close that capturing group (`(foo`)?

```scala
val matchingElements = org.jsoup.Jsoup.parse("<div>foo</div>").getElementsMatchingText("(foo")
```

We get a runtime exception: `java.lang.IllegalArgumentException: Pattern syntax error: (foo`.

Let's see the equivalent in ScalaSoup:

```scala
val result = ScalaSoup.parse("<div>foo</div>").elementsMatchingText("(foo")
```

What happens this time? It doesn't even compile.

```
[error] Foo.scala:183:73: Regex predicate failed: Unclosed group near index 4
[error] (foo
[error]     ^
[error]     val result = ScalaSoup.parse("<div>foo</div>").elementsMatchingText("(foo")
[error]                                                                         ^
[error] one error found
[error] (dsl/test:compileIncremental) Compilation failed
```

We've eliminated yet another entire class of runtime exceptions.

What about CSS selectors?

Take this Jsoup example (note the unclosed `[`):

```scala
val result = org.jsoup.Jsoup.parse("<div>foo</div>").select("a[href")
```

What happens? Another runtime exception:

```scala
org.jsoup.select.Selector$SelectorParseException: Did not find balanced marker at 'href'
```

And what about ScalaSoup?

```scala
val result = ScalaSoup.parse("<div>foo</div>").select("a[href")
```

As you guessed, it doesn't even compile:

```
[error] Foo.scala:183:59: CssSelector predicate failed: Did not find balanced marker at 'href'
[error]     val result = ScalaSoup.parse("<div>foo</div>").select("a[href")
[error]                                                           ^
[error] one error found
[error] (dsl/test:compileIncremental) Compilation failed
```

These compile-time checks are courtesy of the excellent [refined](https://github.com/fthomas/refined) library.

There is one big limitation: these checks rely on the regex and CSS selectors being baked in _at compile time_. For example, this won't compile:

```scala
val someSelectorFromTheOutsideWorld: String = ???
val result = ScalaSoup.parse("<div>foo</div>").select(someSelectorFromTheOutsideWorld)
```

Here's the compiler error:

```
[error] Foo.scala:184:59: compile-time refinement only works with literals
[error]     val result = ScalaSoup.parse("<div>foo</div>").select(someSelectorFromTheOutsideWorld)
[error]                                                           ^
[error] one error found
[error] (dsl/test:compileIncremental) Compilation failed
```

ScalaSoup provides a `fromString` method for these cases. It returns an `Either` containing either an error message or a valid selector:

```scala
val someSelectorFromTheOutsideWorld: String = ???
val selectorOrError: Either[String, CssSelectorString] = CssSelectorString.fromString(someSelectorFromTheOutsideWorld)

selectorOrError match {
  case Left(errorMessage) => Nil
  case Right(validSelector) => ScalaSoup.parse("<div>foo</div>").select(validSelector)
}
```

If you're feeling reckless, you can use `fromStringUnsafe`:

```scala
val someSelectorFromTheOutsideWorld: String = ???
// This will throw if the selector doesn't parse.
val validSelector: CssSelectorString = CssSelectorString.fromStringUnsafe(someSelectorFromTheOutsideWorld)

ScalaSoup.parse("<div>foo</div>").select(validSelector)
```

There are equivalent methods for regexes: `RegexString.fromString` and `RegexString.fromStringUnsafe`.

Other Differences Between JSoup and ScalaSoup
---------------------------------------------

### No null references ###
JSoup really likes to return null references. For example:

```scala
val doc = org.jsoup.Jsoup.parse("<div></div>")
val element: org.jsoup.nodes.Element = doc.selectFirst("span") // Returns null
val spanHtml = element.html // Throws java.lang.NullPointerException
```

In ScalaSoup, `selectFirst` is more honest: it returns an `Option[Element]`.

```scala
val doc = ScalaSoup.parse("<div></div>")
val maybeElement: Option[Element[_]] = doc.selectFirst("span")

maybeElement match {
  case Some(element) => element.html
  case None => ""
}
```

ScalaSoup replaces all such nullable return types with `Option`s. 

It is of course possible to call `get` on an `Option` (which will throw if `None`), putting us right back in the primitive world we just escaped. To avoid this, consider using [WartRemover](http://www.wartremover.org/) and its [OptionPartial](http://www.wartremover.org/doc/warts.html#optionpartial) wart.

### No `Elements` class ###

JSoup often returns an instance of its `Elements` class (a subclass of `ArrayList<Element>`). Scala's collection types are much richer than Java's, so in ScalaSoup we opt to use simple `List[Element]` lists. We do not expose (a wrapper around) `Elements`.

Consider this method from JSoup's `Elements` class:

```java
public List<FormElement> forms() {
  ArrayList<FormElement> forms = new ArrayList<>();
  for (Element el: this)
  if (el instanceof FormElement)
    forms.add((FormElement) el);
  return forms;
}
```

Usage might look something like this:

```scala
val document = org.jsoup.Jsoup.parse(...)
val forms = document.getAllElements.forms
```

In ScalaSoup we don't need this, we can simply use `collect`:

```scala
val forms = document.allChildren.collect({case f: FormElement[ParentState.HasParent] => f})
```

### No mutations to arguments ###

Consider this JSoup program:

```scala
val doc1 = org.jsoup.Jsoup.parse("<span></span>")
val doc2 = org.jsoup.Jsoup.parse("<div></div>")

doc2.selectFirst("div").replaceWith(doc1.selectFirst("span"))
```

With one call to `replaceWith`, we've managed to mutate both doc2 _and_ doc1. This is courtesy of JSoup's "reparenting" misfeature.

Let's rewrite this in ScalaSoup:

```scala
val doc1 = ScalaSoup.parse("<span></span>")
val doc2 = ScalaSoup.parse("<div></div>")

val modifications = for {
  doc <- modifyDocument
  div =  doc.selectFirstChild("div").get
  _   <- div.replaceWith(doc1.selectFirstChild("span").get)
} yield ()

val updatedDoc2 = doc2.withModifications(modifications)
```

This time around, neither doc2 _nor_ doc1 are mutated. ScalaSoup achieves this by cloning arguments to `replaceWith` and other similar methods. 

Future Work
-----------

1. Publish to Sonatype.
2. More tests.
3. Improve this readme.
4. Copy JavaDoc from JSoup?
5. Use refined for URLs.
6. Improve the DSL.

License
-------
MIT

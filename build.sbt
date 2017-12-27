
val commonSettings = Seq(
  organization       := "org.danielnixon",
  scalaVersion       := "2.12.4",
  crossScalaVersions := Seq("2.11.12", "2.12.4"),
  version            := "0.1.0-SNAPSHOT",
  // TODO https://tpolecat.github.io/2017/04/25/scalac-flags.html
  scalacOptions      := Seq(
    "-deprecation",
    "-Xfatal-warnings"
  ),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.4" % Test
  ),
  // TODO: WartRemover, scalafmt, etc.
  wartremoverErrors in (Compile, compile) := Seq(
    Wart.Any,
    Wart.AnyVal,
    Wart.ArrayEquals,
    Wart.AsInstanceOf,
    Wart.DefaultArguments,
    Wart.EitherProjectionPartial,
    Wart.Enumeration,
    Wart.Equals,
    Wart.ExplicitImplicitTypes,
    Wart.FinalCaseClass,
    Wart.FinalVal,
    //    Wart.ImplicitConversion,
    //    Wart.ImplicitParameter,
    Wart.IsInstanceOf,
    Wart.JavaConversions,
    Wart.JavaSerializable,
    Wart.LeakingSealed,
    Wart.MutableDataStructures,
    //    Wart.NonUnitStatements,
    //    Wart.Nothing,
    Wart.Null,
    Wart.Option2Iterable,
    Wart.OptionPartial,
    //    Wart.Overloading,
    Wart.Product,
    Wart.PublicInference,
    Wart.Recursion,
    Wart.Return,
    Wart.Serializable,
    Wart.StringPlusAny,
    Wart.Throw,
    Wart.ToString,
    Wart.TraversableOps,
    Wart.TryPartial,
    Wart.Var,
    Wart.While
  )
)

lazy val root = (project in file(".")).
  settings(commonSettings:_*).
  aggregate(core, dsl)

lazy val core = (project in file("core")).
  settings(commonSettings:_*).
  settings(
    name := "scalasoup",
    libraryDependencies ++= Seq(
      "org.jsoup"      %  "jsoup"                 % "1.11.2",
      "eu.timepit"     %% "refined"               % "0.8.5",
      "org.http4s"     %% "http4s-blaze-client"   % "0.18.0-M7" % Test,
      "org.scalacheck" %% "scalacheck"            % "1.13.5"    % Test
    )
  )

lazy val dsl = (project in file("dsl")).
  settings(commonSettings:_*).
  settings(
    name := "scalasoup-dsl",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"    % "1.0.0",
      "org.typelevel" %% "cats-free"    % "1.0.0"
    )
  ).
  dependsOn(core)
enablePlugins(MicrositesPlugin)

ThisBuild / baseVersion := "3.0"

ThisBuild / organization := "io.github.timwspence"
ThisBuild / organizationName := "TimWSpence"
publishGithubUser in ThisBuild := "TimWSpence"
publishFullName in ThisBuild := "Tim Spence"

ThisBuild / developers := List(
  Developer("TimWSpence", "Tim Spence", "@TimWSpence", url("https://github.com/TimWSpence"))
)

val PrimaryOS = "ubuntu-latest"

val Scala213 = "2.13.3"

ThisBuild / crossScalaVersions := Seq("2.12.12", Scala213)

val LTSJava = "adopt@1.11"
val LatestJava = "adopt@1.15"
val GraalVM8 = "graalvm-ce-java8@20.2.0"

ThisBuild / githubWorkflowJavaVersions := Seq(LTSJava, LatestJava, GraalVM8)
ThisBuild / githubWorkflowOSes := Seq(PrimaryOS)

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(List("${{ matrix.ci }}")),

  WorkflowStep.Sbt(
    List("docs/mdoc"),
    cond = Some(s"matrix.scala == '$Scala213' && matrix.ci == 'ciJVM'")),
)

ThisBuild / githubWorkflowBuildMatrixAdditions += "ci" -> List("ciJVM")

// ThisBuild / githubWorkflowBuildMatrixExclusions +=
//   MatrixExclude(Map("java" -> LatestJava, "scala" -> "3.0.0-M1"))

ThisBuild / homepage := Some(url("https://github.com/TimWSpence/cats-stm"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/TimWSpence/cats-stm"),
    "git@github.com:TimWSpence/cats-stm.git"))

addCommandAlias("ciJVM", "; project cats-stm; headerCheck; scalafmtCheck; clean; test; mimaReportBinaryIssues")

addCommandAlias("prePR", "; project `cats-stm`; clean; scalafmtAll; headerCreate")

val CatsVersion = "2.2.0"
val CatsEffectVersion = "2.2.0"
val DisciplineVersion = "1.0.3"
val ScalaCheckVersion = "1.15.1"
val MunitVersion = "0.7.19"
val MunitCatsEffectVersion = "1.0.3"
val ScalacheckEffectVersion = "0.3.0"

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.4.4"
inThisBuild(
  List(
    scalaVersion := Scala213,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val `cats-stm` = project.in(file("."))
  .settings(commonSettings)
  .aggregate(
    core,
    laws,
    docs,
    examples
  )
  .settings(noPublishSettings)

lazy val core = project.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "cats-stm",
  )
  .settings(testFrameworks += new TestFramework("munit.Framework"))
  .settings(initialCommands in console := """
    import cats._
    import cats.implicits._
    import cats.effect._
    import cats.effect.implicits._
    implicit val CS: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
    implicit val T: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)
    """
  )

lazy val laws = project.in(file("laws"))
  .settings(commonSettings)
  .settings(
    name := "cats-stm",
  )
  .settings(testFrameworks += new TestFramework("munit.Framework"))
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel"              %% "cats-laws"                 % CatsVersion % Test,
      "org.typelevel"              %% "discipline-munit"          % DisciplineVersion % Test,
      "org.scalacheck"             %% "scalacheck"                % ScalaCheckVersion % Test,
    )
  )
  .dependsOn(core)


lazy val docs = project.in(file("docs"))
  .settings(commonSettings, skipOnPublishSettings, micrositeSettings)
  .dependsOn(core)
  .enablePlugins(MicrositesPlugin)

lazy val examples = project.in(file("examples"))
  .settings(commonSettings, skipOnPublishSettings)
  .dependsOn(core)

lazy val commonSettings = Seq(
  organizationHomepage := Some(url("https://github.com/TimWSpence")),
  libraryDependencies ++= Seq(
    "org.typelevel"              %% "cats-effect"               % CatsEffectVersion,
    "org.typelevel"              %% "cats-core"                 % CatsVersion,
    "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % "1.2.5" % Test,
    "org.scalacheck"             %% "scalacheck"                % ScalaCheckVersion % Test,
    "org.scalameta"              %% "munit"                     % MunitVersion % Test,
    "org.scalameta"              %% "munit-scalacheck"          % MunitVersion % Test,
    "org.typelevel"              %% "scalacheck-effect-munit"   % ScalacheckEffectVersion % Test,
    "org.typelevel"              %% "munit-cats-effect"         % MunitCatsEffectVersion % Test
  ),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.1" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
)

lazy val micrositeSettings = {
  import microsites._
  Seq(
    micrositeName := "Cats STM",
    micrositeDescription := "Software Transactional Memory for Cats Effect",
    micrositeAuthor := "Tim Spence",
    micrositeGithubOwner := "TimWSpence",
    micrositeGithubRepo := "cats-stm",
    micrositeBaseUrl := "/cats-stm",
    micrositeFooterText := None,
    micrositeDocumentationUrl := "https://www.javadoc.io/doc/io.github.timwspence/cats-stm_2.12",
    micrositeHighlightTheme := "atom-one-light",
    micrositeCompilingDocsTool := WithMdoc,
    micrositePushSiteWith := GitHub4s,
    micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
    micrositeGitterChannelUrl := "cats-stm/community",
    micrositeExtraMdFiles := Map(
      file("CHANGELOG.md")        -> ExtraMdFileConfig("changelog.md", "page", Map("title" -> "changelog", "section" -> "changelog", "position" -> "100")),
      file("CODE_OF_CONDUCT.md")  -> ExtraMdFileConfig("code-of-conduct.md",   "page", Map("title" -> "code of conduct",   "section" -> "code of conduct",   "position" -> "101")),
      file("LICENSE.txt")             -> ExtraMdFileConfig("license.md",   "page", Map("title" -> "license",   "section" -> "license",   "position" -> "102"))
    )
  )
}

lazy val skipOnPublishSettings = Seq(
  skip in publish := true,
  publish := (()),
  publishLocal := (()),
  publishArtifact := false,
  publishTo := None
)

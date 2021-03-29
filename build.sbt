ThisBuild / baseVersion := "0.10.0"

ThisBuild / organization := "io.github.timwspence"
ThisBuild / organizationName := "TimWSpence"
ThisBuild / startYear := Some(2017)
ThisBuild / endYear := Some(2021)
publishGithubUser in ThisBuild := "TimWSpence"
publishFullName in ThisBuild := "Tim Spence"

ThisBuild / developers := List(
  Developer("TimWSpence", "Tim Spence", "@TimWSpence", url("https://github.com/TimWSpence"))
)

val PrimaryOS = "ubuntu-latest"

val Scala213 = "2.13.5"

ThisBuild / crossScalaVersions := Seq("3.0.0-M3", "3.0.0-RC1", "2.12.13", Scala213)

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

ThisBuild / homepage := Some(url("https://github.com/TimWSpence/cats-stm"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/TimWSpence/cats-stm"),
    "git@github.com:TimWSpence/cats-stm.git"))

addCommandAlias("ciJVM", "; project cats-stm; headerCheck; scalafmtCheck; clean; test; mimaReportBinaryIssues")

addCommandAlias("prePR", "; project `cats-stm`; clean; scalafmtAll; headerCreate")

val CatsVersion = "2.4.2"
val CatsEffectVersion = "3.0.0"
val DisciplineVersion = "1.0.6"
val ScalaCheckVersion = "1.15.3"
val MunitVersion = "0.7.22"
val MunitCatsEffectVersion = "0.13.1"
val ScalacheckEffectVersion = "0.7.1"

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
    benchmarks,
    docs,
    examples,
    laws,
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
    import cats.effect.unsafe.implicits.global
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

lazy val benchmarks = project.in(file("benchmarks"))
  .settings(commonSettings)
  .settings(
    name := "cats-stm",
  )
  .dependsOn(core)
  .enablePlugins(NoPublishPlugin, JmhPlugin)


lazy val docs = project.in(file("cats-stm-docs"))
  .settings(
    moduleName := "cats-stm-docs",
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(core),
    target in (ScalaUnidoc, unidoc) := (baseDirectory in LocalRootProject).value / "website" / "static" / "api",
    cleanFiles += (target in (ScalaUnidoc, unidoc)).value,
    docusaurusCreateSite := docusaurusCreateSite.dependsOn(unidoc in Compile).value,
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(unidoc in Compile).value,
  )
  .settings(commonSettings, skipOnPublishSettings)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
  .dependsOn(core)


lazy val examples = project.in(file("examples"))
  .settings(commonSettings, skipOnPublishSettings)
  .dependsOn(core)
  .enablePlugins(NoPublishPlugin)

lazy val commonSettings = Seq(
  organizationHomepage := Some(url("https://github.com/TimWSpence")),
  libraryDependencies ++= Seq(
    "org.typelevel"              %% "cats-effect"               % CatsEffectVersion,
    "org.typelevel"              %% "cats-core"                 % CatsVersion,
    "org.scalacheck"             %% "scalacheck"                % ScalaCheckVersion % Test,
    "org.scalameta"              %% "munit"                     % MunitVersion % Test,
    "org.scalameta"              %% "munit-scalacheck"          % MunitVersion % Test,
    "org.typelevel"              %% "scalacheck-effect-munit"   % ScalacheckEffectVersion % Test,
    "org.typelevel"              %% "munit-cats-effect-3"       % MunitCatsEffectVersion % Test
  ),
)

lazy val skipOnPublishSettings = Seq(
  skip in publish := true,
  publish := (()),
  publishLocal := (()),
  publishArtifact := false,
  publishTo := None
)

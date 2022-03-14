ThisBuild / tlBaseVersion := "0.12" // your current series x.y

ThisBuild / organization := "io.github.timwspence"
ThisBuild / organizationName := "TimWSpence"
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  tlGitHubDev("TimWSpence", "Tim Spence")
)
ThisBuild / startYear := Some(2017)

// true by default, set to false to publish to s01.oss.sonatype.org
ThisBuild / tlSonatypeUseLegacyHost := true

val Scala213 = "2.13.8"
ThisBuild / crossScalaVersions := Seq(Scala213, "2.12.15", "3.0.2")
ThisBuild / scalaVersion := Scala213 // the default Scala

ThisBuild / homepage := Some(url("https://github.com/TimWSpence/cats-stm"))

ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/TimWSpence/cats-stm"), "git@github.com:TimWSpence/cats-stm.git")
)

addCommandAlias("ciJVM", "; project cats-stm; headerCheck; scalafmtCheck; clean; test; core/mimaReportBinaryIssues")

addCommandAlias("prePR", "; project `cats-stm`; clean; scalafmtAll; headerCreate")

val CatsVersion             = "2.7.0"
val CatsEffectVersion       = "3.3.7"
val DisciplineVersion       = "1.0.9"
val ScalaCheckVersion       = "1.15.4"
val MunitVersion            = "0.7.29"
val MunitCatsEffectVersion  = "1.0.7"
val ScalacheckEffectVersion = "1.0.3"

lazy val `cats-stm` = tlCrossRootProject
  .aggregate(
    core,
    benchmarks,
    docs,
    examples,
    laws
  )

lazy val core = project
  .in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "cats-stm"
  )
  .settings(testFrameworks += new TestFramework("munit.Framework"))
  .settings(console / initialCommands := """
    import cats._
    import cats.implicits._
    import cats.effect._
    import cats.effect.implicits._
    import cats.effect.unsafe.implicits.global
    """)
  .settings(
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
  )

lazy val laws = project
  .in(file("laws"))
  .settings(commonSettings)
  .settings(
    name := "cats-stm"
  )
  .settings(testFrameworks += new TestFramework("munit.Framework"))
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel"  %% "cats-laws"        % CatsVersion       % Test,
      "org.typelevel"  %% "discipline-munit" % DisciplineVersion % Test,
      "org.scalacheck" %% "scalacheck"       % ScalaCheckVersion % Test
    )
  )
  .dependsOn(core)
  .enablePlugins(NoPublishPlugin)

lazy val benchmarks = project
  .in(file("benchmarks"))
  .settings(commonSettings)
  .settings(
    name := "cats-stm"
  )
  .dependsOn(core)
  .enablePlugins(NoPublishPlugin, JmhPlugin)

lazy val docs = project
  .in(file("site"))
  .settings(commonSettings)
  .settings(
    tlSiteRelatedProjects := Seq(
      "cats" -> url("https://typelevel.org/cats/"),
      "cats effect" -> url("https://typelevel.org/cats-effect/"),
    )
  )
  .enablePlugins(TypelevelSitePlugin)
  .dependsOn(core)

lazy val unidoc = project
  .in(file("unidoc"))
  .enablePlugins(TypelevelUnidocPlugin) // also enables the ScalaUnidocPlugin
  .settings(
    name := "cats-stm-docs"
  )

lazy val examples = project
  .in(file("examples"))
  .settings(commonSettings)
  .dependsOn(core)
  .enablePlugins(NoPublishPlugin)

lazy val commonSettings = Seq(
  organizationHomepage := Some(url("https://github.com/TimWSpence")),
  libraryDependencies ++= Seq(
    "org.typelevel"  %% "cats-effect"             % CatsEffectVersion,
    "org.typelevel"  %% "cats-core"               % CatsVersion,
    "org.scalacheck" %% "scalacheck"              % ScalaCheckVersion       % Test,
    "org.scalameta"  %% "munit"                   % MunitVersion            % Test,
    "org.scalameta"  %% "munit-scalacheck"        % MunitVersion            % Test,
    "org.typelevel"  %% "scalacheck-effect-munit" % ScalacheckEffectVersion % Test,
    "org.typelevel"  %% "munit-cats-effect-3"     % MunitCatsEffectVersion  % Test
  )
)

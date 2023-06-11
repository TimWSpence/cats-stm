ThisBuild / tlBaseVersion := "0.13" // your current series x.y

ThisBuild / organization := "io.github.timwspence"
ThisBuild / organizationName := "TimWSpence"
ThisBuild / organizationHomepage := Some(url("https://github.com/TimWSpence"))
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  tlGitHubDev("TimWSpence", "Tim Spence")
)
ThisBuild / startYear := Some(2017)

ThisBuild / tlSonatypeUseLegacyHost := false

val Scala213 = "2.13.10"
ThisBuild / crossScalaVersions := Seq(Scala213, "2.12.18", "3.2.2")
ThisBuild / scalaVersion := Scala213 // the default Scala
ThisBuild / tlJdkRelease := Some(8)

val CatsVersion             = "2.9.0"
val CatsEffectVersion       = "3.4.10"
val DisciplineVersion       = "2.0.0-M3"
val ScalaCheckVersion       = "1.17.0"
val MunitVersion            = "1.0.0-M7"
val MunitCatsEffectVersion  = "2.0.0-M3"
val ScalacheckEffectVersion = "2.0.0-M2"

lazy val `cats-stm` = tlCrossRootProject
  .aggregate(
    core,
    benchmarks,
    docs,
    examples,
    laws,
    unidocs
  )

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("core"))
  .settings(
    name := "cats-stm",
    libraryDependencies ++= Seq(
      "org.typelevel"  %%% "cats-effect"             % CatsEffectVersion,
      "org.typelevel"  %%% "cats-core"               % CatsVersion,
      "org.scalacheck" %%% "scalacheck"              % ScalaCheckVersion       % Test,
      "org.scalameta"  %%% "munit"                   % MunitVersion            % Test,
      "org.scalameta"  %%% "munit-scalacheck"        % MunitVersion            % Test,
      "org.typelevel"  %%% "scalacheck-effect-munit" % ScalacheckEffectVersion % Test,
      "org.typelevel"  %%% "munit-cats-effect"       % MunitCatsEffectVersion  % Test
    )
  )
  .nativeSettings(
    tlVersionIntroduced := List("2.12", "2.13", "3").map(_ -> "0.13.2").toMap
  )
  .settings(console / initialCommands := """
    import cats._
    import cats.implicits._
    import cats.effect._
    import cats.effect.implicits._
    import cats.effect.unsafe.implicits.global
    """)

lazy val laws = project
  .in(file("laws"))
  .settings(
    libraryDependencies ++= Seq(
      "org.scalacheck" %%% "scalacheck"              % ScalaCheckVersion       % Test,
      "org.scalameta"  %%% "munit"                   % MunitVersion            % Test,
      "org.scalameta"  %%% "munit-scalacheck"        % MunitVersion            % Test,
      "org.typelevel"  %%% "scalacheck-effect-munit" % ScalacheckEffectVersion % Test,
      "org.typelevel"  %%% "munit-cats-effect"       % MunitCatsEffectVersion  % Test,
      "org.typelevel"  %%% "cats-laws"               % CatsVersion             % Test,
      "org.typelevel"  %%% "discipline-munit"        % DisciplineVersion       % Test
    )
  )
  //TODO cross-build this
  .dependsOn(core.jvm)
  .enablePlugins(NoPublishPlugin)

lazy val benchmarks = project
  .in(file("benchmarks"))
  .dependsOn(core.jvm)
  .enablePlugins(NoPublishPlugin, JmhPlugin)

lazy val docs = project
  .in(file("site"))
  .settings(
    laikaConfig ~= { _.withRawContent },
    tlSiteRelatedProjects := Seq(
      TypelevelProject.Cats,
      TypelevelProject.CatsEffect
    )
  )
  .enablePlugins(TypelevelSitePlugin)
  //TODO cross-build this
  .dependsOn(core.jvm)

lazy val unidocs = project
  .in(file("unidoc"))
  .enablePlugins(TypelevelUnidocPlugin) // also enables the ScalaUnidocPlugin
  .settings(
    name := "cats-stm-docs",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core.jvm)
  )

lazy val examples = crossProject(JVMPlatform, JSPlatform)
  .in(file("examples"))
  .dependsOn(core)
  .enablePlugins(NoPublishPlugin)

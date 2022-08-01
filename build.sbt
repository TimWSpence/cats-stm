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

val Scala213 = "2.13.8"
ThisBuild / crossScalaVersions := Seq(Scala213, "2.12.16", "3.1.3")
ThisBuild / scalaVersion := Scala213 // the default Scala
ThisBuild / tlJdkRelease := Some(8)

val CatsVersion             = "2.7.0"
val CatsEffectVersion       = "3.3.14"
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
    laws,
    unidocs
  )

lazy val core = crossProject(JVMPlatform, JSPlatform)
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
      "org.typelevel"  %%% "munit-cats-effect-3"     % MunitCatsEffectVersion  % Test
    )
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
      "org.typelevel"  %%% "munit-cats-effect-3"     % MunitCatsEffectVersion  % Test,
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

val CatsVersion = "1.6.0"
val CatsEffectVersion = "1.2.0"
val ScalaTestVersion = "3.0.5"
val ScalaCheckVersion = "1.14.0"

lazy val root = (project in file("."))
  .settings(
    organization := "com.github.timwspence",
    name := "cats-stm",
    version := "0.0.2-SNAPSHOT",

    organizationName := "timwspence",
    organizationHomepage := Some(url("https://github.com/TimWSpence")),

    homepage := Some(url("https://github.com/TimWSpence/cats-stm")),
    description := "An STM implementation for Cats Effect",
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/TimWSpence/cats-stm"),
        "scm:git@github.com:TimWSpence/cats-stm.git"
      )
    ),
    developers := List(
      Developer(
        "TimWSpence",
        "Tim Spence",
        "timothywspence@gmail.com",
        url("https://github.com/TimWSpence"))),
    licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),

    isSnapshot := version.value endsWith "SNAPSHOT", // so… sonatype doesn't like git hash snapshots
    publishTo := Some(
      if (isSnapshot.value)
        Opts.resolver.sonatypeSnapshots
      else
        Opts.resolver.sonatypeStaging),
    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
    sonatypeProfileName := organization.value,

    scalaVersion := "2.12.8",
    scalacOptions ++= Seq("-Ypartial-unification"),
    libraryDependencies ++= Seq(
      "org.typelevel"  %% "cats-effect" % CatsEffectVersion,
      "org.typelevel"  %% "cats-core"   % CatsVersion,
      "org.scalatest"  %% "scalatest"   % ScalaTestVersion  % "test",
      "org.scalacheck" %% "scalacheck"  % ScalaCheckVersion % "test",
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.0"),
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Ypartial-unification",
  "-Xfatal-warnings",
)

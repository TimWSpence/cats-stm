import ReleaseTransformations._

enablePlugins(MicrositesPlugin)

val CatsVersion = "1.6.0"
val CatsEffectVersion = "1.2.0"
val ScalaTestVersion = "3.0.5"
val ScalaCheckVersion = "1.14.0"

lazy val root = (project in file("."))
  .settings(
    organization := "io.github.timwspence",
    name := "cats-stm",

    organizationName := "timwspence",
    organizationHomepage := Some(url("https://github.com/TimWSpence")),

    homepage := Some(url("https://timwspence.github.io/cats-stm")),
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

    releaseCrossBuild := true,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      // For non cross-build projects, use releaseStepCommand("publishSigned")
      releaseStepCommandAndRemaining("+publishSigned"),
      setNextVersion,
      commitNextVersion,
      releaseStepCommand("sonatypeReleaseAll"),
      pushChanges
    ),
    crossScalaVersions := Seq("2.12.8"),

    micrositeName := "Cats STM",
    micrositeDescription := "Software Transactional Memory for Cats Effect",
    micrositeAuthor := "Tim Spence",
    micrositeGithubOwner := "TimWSpence",
    micrositeGithubRepo := "cats-stm",
    micrositeBaseUrl := "/cats-stm",
    micrositeFooterText := None,
    micrositeHighlightTheme := "atom-one-light",

    scalaVersion := "2.12.8",
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-language:higherKinds",
      "-language:postfixOps",
      "-feature",
      "-Ypartial-unification",
      "-Xfatal-warnings",
    ),
    libraryDependencies ++= Seq(
      "org.typelevel"  %% "cats-effect" % CatsEffectVersion,
      "org.typelevel"  %% "cats-core"   % CatsVersion,
      "org.scalatest"  %% "scalatest"   % ScalaTestVersion  % "test",
      "org.scalacheck" %% "scalacheck"  % ScalaCheckVersion % "test",
    ),
    addCompilerPlugin("org.typelevel" % "kind-projector" % "0.10.0" cross CrossVersion.binary),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.0"),
  )


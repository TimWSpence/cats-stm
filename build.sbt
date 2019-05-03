import ReleaseTransformations._

enablePlugins(MicrositesPlugin)

val CatsVersion = "1.6.0"
val CatsEffectVersion = "1.3.0"
val ScalaTestVersion = "3.0.7"
val ScalaCheckVersion = "1.14.0"
val ScalaJava8CompatVersion = "0.9.0"

lazy val `cats-stm` = project.in(file("."))
  .settings(commonSettings, releaseSettings, skipOnPublishSettings)
  .aggregate(
    core,
    docs,
    examples
  )

lazy val core = project.in(file("core"))
  .settings(commonSettings, releaseSettings, mimaSettings)
  .settings(
    name := "cats-stm",
  )

lazy val docs = project.in(file("docs"))
  .settings(commonSettings, skipOnPublishSettings, micrositeSettings)
  .dependsOn(core)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(TutPlugin)

lazy val examples = project.in(file("examples"))
  .settings(commonSettings, skipOnPublishSettings)
  .dependsOn(core)

lazy val commonSettings = Seq(
  organization := "io.github.timwspence",
  organizationName := "timwspence",
  scalaVersion := "2.12.8",
  crossScalaVersions := Seq("2.11.12", scalaVersion.value),
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
    "org.typelevel"          %% "cats-effect"        % CatsEffectVersion,
    "org.typelevel"          %% "cats-core"          % CatsVersion,
    "org.scalatest"          %% "scalatest"          % ScalaTestVersion  % "test",
    "org.scalacheck"         %% "scalacheck"         % ScalaCheckVersion % "test",
    "org.scala-lang.modules" %% "scala-java8-compat" % ScalaJava8CompatVersion,
  ),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.10.0" cross CrossVersion.binary),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.0"),
)

lazy val releaseSettings = Seq(
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
  )
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
    fork in tut := true,
    scalacOptions in Tut --= Seq(
      "-Xfatal-warnings",
      "-Ywarn-unused-import",
      "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Ywarn-unused:imports",
      "-Xlint:-missing-interpolator,_"
    ),
    libraryDependencies += "com.47deg" %% "github4s" % "0.20.1",
    micrositePushSiteWith := GitHub4s,
    micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
    micrositeExtraMdFiles := Map(
      file("CHANGELOG.md")        -> ExtraMdFileConfig("changelog.md", "page", Map("title" -> "changelog", "section" -> "changelog", "position" -> "100")),
      file("CODE_OF_CONDUCT.md")  -> ExtraMdFileConfig("code-of-conduct.md",   "page", Map("title" -> "code of conduct",   "section" -> "code of conduct",   "position" -> "101")),
      file("LICENSE.txt")             -> ExtraMdFileConfig("license.md",   "page", Map("title" -> "license",   "section" -> "license",   "position" -> "102"))
    )
  )
}

lazy val mimaSettings = {
  import sbtrelease.Version

  def semverBinCompatVersions(major: Int, minor: Int, patch: Int): Set[(Int, Int, Int)] = {
    val majorVersions: List[Int] =
      if (major == 0 && minor == 0) List.empty[Int] // If 0.0.x do not check MiMa
      else List(major)
    val minorVersions : List[Int] =
      if (major >= 1) Range(0, minor).inclusive.toList
      else List(minor)
    def patchVersions(currentMinVersion: Int): List[Int] = 
      if (minor == 0 && patch == 0) List.empty[Int]
      else if (currentMinVersion != minor) List(0)
      else Range(0, patch - 1).inclusive.toList

    val versions = for {
      maj <- majorVersions
      min <- minorVersions
      pat <- patchVersions(min)
    } yield (maj, min, pat)
    versions.toSet
  }

  def mimaVersions(version: String): Set[String] = {
    Version(version) match {
      case Some(Version(major, Seq(minor, patch), _)) =>
        semverBinCompatVersions(major.toInt, minor.toInt, patch.toInt)
          .map{case (maj, min, pat) => maj.toString + "." + min.toString + "." + pat.toString}
      case _ =>
        Set.empty[String]
    }
  }
  // Safety Net For Exclusions
  lazy val excludedVersions: Set[String] = Set()

  // Safety Net for Inclusions
  lazy val extraVersions: Set[String] = Set()

  Seq(
    mimaFailOnProblem := mimaVersions(version.value).toList.headOption.isDefined,
    mimaPreviousArtifacts := (mimaVersions(version.value) ++ extraVersions)
      .filterNot(excludedVersions.contains(_))
      .map{v => 
        val moduleN = moduleName.value + "_" + scalaBinaryVersion.value.toString
        organization.value % moduleN % v
      },
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      Seq()
    }
  )
}

lazy val skipOnPublishSettings = Seq(
  skip in publish := true,
  publish := (()),
  publishLocal := (()),
  publishArtifact := false,
  publishTo := None
)

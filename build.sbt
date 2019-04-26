val CatsVersion = "1.6.0"
val CatsEffectVersion = "1.2.0"
val ScalaTestVersion = "3.0.5"
val ScalaCheckVersion = "1.14.0"

lazy val root = (project in file("."))
  .settings(
    organization := "com.github.timwspence",
    name := "cats-stm",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.12.8",
    scalacOptions ++= Seq("-Ypartial-unification"),
    libraryDependencies ++= Seq(
      "org.typelevel"  %%  "cats-effect" % CatsEffectVersion,
      "org.typelevel"  %% "cats-core"    % CatsVersion,
      "org.scalatest"  %% "scalatest"    % ScalaTestVersion  % "test",
      "org.scalacheck" %% "scalacheck"   % ScalaCheckVersion % "test",
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector"     % "0.9.6"),
    addCompilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.3.0")
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

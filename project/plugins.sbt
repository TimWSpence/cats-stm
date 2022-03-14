resolvers +=
  "Sonatype S01 OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"

addSbtPlugin("org.scalameta"      % "sbt-scalafmt"       % "2.4.6")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"       % "0.9.34")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"            % "0.4.3")
addSbtPlugin("org.typelevel"      % "sbt-typelevel"      % "0.4.6-18-a4282ae-SNAPSHOT")
addSbtPlugin("org.typelevel"      % "sbt-typelevel-site" % "0.4.6-18-a4282ae-SNAPSHOT")

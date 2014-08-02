organization := "org.databrary"

name := "iteratees"

description := "Utilities based on play's iteratees, including a ZipFile Enumeratee"

homepage := Some(url("http://github.com/databrary/iteratees"))

licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

version := "0.1-SNAPSHOT"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % "2.3.2",
  "org.specs2" %% "specs2" % "2.3.13" % "test"
)

scalaVersion := "2.11.2"

crossScalaVersions ++= Seq("2.10.4")

scalacOptions ++= Seq("-feature","-deprecation")

scalaSource in Compile := baseDirectory.value / "src"

scalaSource in Test := baseDirectory.value / "test"

publishMavenStyle := true

publishArtifact in Test := false

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomIncludeRepository := { _ => false }

pomExtra := (
  <scm>
    <url>git@github.com:databrary/iteratees.git</url>
    <connection>scm:git:git@github.com:databrary/iteratees.git</connection>
  </scm>
  <developers>
    <developer>
      <id>dylex</id>
      <name>Dylan Simon</name>
    </developer>
  </developers>)

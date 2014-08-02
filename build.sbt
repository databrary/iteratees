organization := "org.databrary"

name := "iteratee"

licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

version := "0.1-SNAPSHOT"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % "2.3.2",
  "org.specs2" %% "specs2" % "2.3.13" % "test"
)

scalaVersion := "2.11.1"

crossScalaVersions ++= Seq("2.10.4")

scalacOptions ++= Seq("-feature","-deprecation")

scalaSource in Compile := baseDirectory.value / "src"

scalaSource in Test := baseDirectory.value / "test"

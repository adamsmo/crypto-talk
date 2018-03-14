name := "demo-coin"

version := "0.1"

scalaVersion := "2.12.4"

val akkaVersion = "2.5.11"
val akkaHttpVersion = "10.1.0"

libraryDependencies ++= Seq(
  "org.bouncycastle" % "bcprov-ext-jdk15on" % "1.59",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
)
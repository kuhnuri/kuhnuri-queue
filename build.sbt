organization := """com.elovirta.kuhnuri"""

name := """queue"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.6"

libraryDependencies += jdbc
libraryDependencies += ehcache
libraryDependencies += ws
libraryDependencies += guice
libraryDependencies += filters
libraryDependencies += "org.postgresql" % "postgresql" % "42.2.1"
libraryDependencies += "org.jooq" % "jooq" % "3.10.5"
libraryDependencies += "org.jooq" % "jooq-codegen-maven" % "3.10.5"
libraryDependencies += "org.jooq" % "jooq-meta" % "3.10.5"
libraryDependencies += "javax.annotation" % "javax.annotation-api" % "1.3.2"
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % "test"
libraryDependencies += "org.mindrot" % "jbcrypt" % "0.4"

val generateJOOQ = taskKey[Seq[File]]("Generate JooQ classes")
generateJOOQ := {
  (runner in Compile).value.run("org.jooq.util.GenerationTool", (fullClasspath in Compile).value.files, Array("conf/queue.xml"), streams.value.log).failed foreach (sys error _.getMessage)
  ((sourceManaged.value / "main/generated") ** "*.java").get
}

//unmanagedSourceDirectories in Compile += sourceManaged.value / "main"
unmanagedSourceDirectories in Compile += baseDirectory.value / "src/main/java"

sources in (Compile, doc) := Seq()

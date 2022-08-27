def crossSources = Def.settings(
  unmanagedSourceDirectories ++= unmanagedSourceDirectories.value.flatMap { dir =>
    val path = dir.getPath
    val sv = scalaVersion.value
    val suffixes = CrossVersion.partialVersion(sv) match {
      case Some((2, 11)) => Seq("2.11", "2.11-12")
      case Some((2, 12)) => Seq("2.11-12", "2.12", "2.12-13")
      case Some((2, 13)) => Seq("2.12-13", "2.13")
      case _ => throw new IllegalArgumentException("unsupported scala version")
    }
    suffixes.map(s => file(s"$path-$s"))
  }
)

inThisBuild(Seq(
  version := "0.1",
  organization := "com.github.ghik",
  scalaVersion := "2.13.8",
  
  githubWorkflowTargetTags ++= Seq("v*"),
  githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17.0.4.1")),
  githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v"))),

  githubWorkflowPublish := Seq(WorkflowStep.Sbt(
    List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )),
))

lazy val purifier = project.in(file("."))
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scalatest" %% "scalatest-funsuite" % "3.2.13" % Test,
    ),

    Test / fork := true,

    projectInfo := ModuleInfo(
      nameFormal = "Purifier",
      description = "Scala 2 compiler plugin that disallows discarding of non-Unit expressions",
      homepage = Some(url("https://github.com/ghik/purifier")),
      startYear = Some(2022),
      licenses = Vector(
        "The Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")
      ),
      organizationName = "ghik",
      organizationHomepage = Some(url("https://github.com/ghik")),
      scmInfo = Some(ScmInfo(
        browseUrl = url("https://github.com/ghik/purifier.git"),
        connection = "scm:git:git@github.com:ghik/purifier.git",
        devConnection = Some("scm:git:git@github.com:ghik/purifier.git")
      )),
      developers = Vector(
        Developer("ghik", "Roman Janusz", "romeqjanoosh@gmail.com", url("https://github.com/ghik"))
      ),
    ),

    crossVersion := CrossVersion.full,
    inConfig(Compile)(crossSources),
    inConfig(Test)(crossSources),

    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
    publishTo := sonatypePublishToBundle.value,
  )

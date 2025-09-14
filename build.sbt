inThisBuild(Seq(
  organization := "com.github.ghik",
  scalaVersion := crossScalaVersions.value.head,
  crossScalaVersions := Seq(
    "3.7.3", "3.7.2", "3.7.1", "3.7.0", "3.6.4", "3.5.2", "3.4.3", "3.3.4", "3.3.5", "3.3.6", "3.2.2", "2.13.16", "2.12.20",
  ),

  githubWorkflowTargetTags ++= Seq("v*"),
  githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17")),
  githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v"))),

  githubWorkflowPublish := Seq(WorkflowStep.Sbt(
    List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}",
    ),
  )),
))

lazy val zerowaste = project.in(file("."))
  .settings(
    libraryDependencies ++= Seq(
      if (scalaBinaryVersion.value == "3")
        "org.scala-lang" %% "scala3-compiler" % scalaVersion.value
      else
        "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.scalatest" %% "scalatest-funsuite" % "3.2.13" % Test,
      "org.typelevel" %% "cats-effect" % "3.4.1" % Test,
    ),

    Compile / resourceDirectory := {
      val base = (Compile / resourceDirectory).value
      if (scalaBinaryVersion.value == "3")
        base.getParentFile / s"${base.name}-3"
      else
        base.getParentFile / s"${base.name}-2"
    },

    Test / fork := true,

    projectInfo := ModuleInfo(
      nameFormal = "Purifier",
      description = "Scala compiler plugin that disallows discarding of non-Unit expressions",
      homepage = Some(url("https://github.com/ghik/zerowaste")),
      startYear = Some(2022),
      licenses = Vector(
        "The MIT License" -> url("https://opensource.org/licenses/MIT"),
      ),
      organizationName = "ghik",
      organizationHomepage = Some(url("https://github.com/ghik")),
      scmInfo = Some(ScmInfo(
        browseUrl = url("https://github.com/ghik/zerowaste.git"),
        connection = "scm:git:git@github.com:ghik/zerowaste.git",
        devConnection = Some("scm:git:git@github.com:ghik/zerowaste.git"),
      )),
      developers = Vector(
        Developer("ghik", "Roman Janusz", "romeqjanoosh@gmail.com", url("https://github.com/ghik")),
      ),
    ),

    crossVersion := CrossVersion.full,

    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
  )

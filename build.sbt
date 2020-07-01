import ProjectInfo._
import kevinlee.sbt.SbtCommon.crossVersionProps
import just.semver.SemVer
import SemVer.{Major, Minor}

val ProjectScalaVersion: String = "2.13.2"
val CrossScalaVersions: Seq[String] = Seq("2.11.12", "2.12.11", ProjectScalaVersion)
val IncludeTest: String = "compile->compile;test->test"

lazy val hedgehogVersion = "97854199ef795a5dfba15478fd9abe66035ddea2"
lazy val hedgehogRepo: MavenRepository =
  "bintray-scala-hedgehog" at "https://dl.bintray.com/hedgehogqa/scala-hedgehog"

lazy val hedgehogLibs: Seq[ModuleID] = Seq(
    "qa.hedgehog" %% "hedgehog-core" % hedgehogVersion % Test
  , "qa.hedgehog" %% "hedgehog-runner" % hedgehogVersion % Test
  , "qa.hedgehog" %% "hedgehog-sbt" % hedgehogVersion % Test
)

val GitHubUsername = "Kevin-Lee"
val TheProjectName = "just-sysprocess"

ThisBuild / scalaVersion     := ProjectScalaVersion
ThisBuild / version          := ProjectVersion
ThisBuild / organization     := "io.kevinlee"
ThisBuild / organizationName := "Kevin's Code"
ThisBuild / crossScalaVersions := CrossScalaVersions

ThisBuild / developers   := List(
    Developer(GitHubUsername, "Kevin Lee", "kevin.code@kevinlee.io", url(s"https://github.com/$GitHubUsername"))
  )
ThisBuild / homepage := Some(url(s"https://github.com/$GitHubUsername/$TheProjectName"))
ThisBuild / scmInfo :=
  Some(ScmInfo(
    url(s"https://github.com/$GitHubUsername/$TheProjectName"),
    s"git@github.com:$GitHubUsername/$TheProjectName.git"
  ))

def prefixedProjectName(name: String) = s"$TheProjectName${if (name.isEmpty) "" else s"-$name"}"

lazy val noPublish: SettingsDefinition = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  skip in sbt.Keys.`package` := true,
  skip in packagedArtifacts := true,
  skip in publish := true
)

def projectCommonSettings(id: String, projectName: ProjectName, file: File): Project =
  Project(id, file)
    .settings(
      name := prefixedProjectName(projectName.projectName),
      addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full),
      addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
      resolvers ++= Seq(
          hedgehogRepo
        ),
      libraryDependencies ++= hedgehogLibs,
      /* WartRemover and scalacOptions { */
//      wartremoverErrors in (Compile, compile) ++= commonWarts((scalaBinaryVersion in update).value),
//      wartremoverErrors in (Test, compile) ++= commonWarts((scalaBinaryVersion in update).value),
      wartremoverErrors ++= commonWarts((scalaBinaryVersion in update).value),
//      wartremoverErrors ++= Warts.all,
      Compile / console / wartremoverErrors := List.empty,
      Compile / console / wartremoverWarnings := List.empty,
      Compile / console / scalacOptions :=
          (console / scalacOptions).value
            .filterNot(option =>
              option.contains("wartremover") || option.contains("import")
            ),
      Test / console / wartremoverErrors := List.empty,
      Test / console / wartremoverWarnings := List.empty,
      Test / console / scalacOptions :=
          (console / scalacOptions).value
            .filterNot( option =>
              option.contains("wartremover") || option.contains("import")
            ),
      /* } WartRemover and scalacOptions */
      testFrameworks ++= Seq(TestFramework("hedgehog.sbt.Framework")),

      /* Ammonite-REPL { */
      libraryDependencies ++=
        (scalaBinaryVersion.value match {
          case "2.10" =>
            Seq.empty[ModuleID]
          case "2.11" =>
            Seq("com.lihaoyi" % "ammonite" % "1.6.7" % Test cross CrossVersion.full)
          case "2.12" =>
            Seq.empty[ModuleID] // TODO: add ammonite when it supports Scala 2.12.11
          case _ =>
            Seq("com.lihaoyi" % "ammonite" % "2.1.4" % Test cross CrossVersion.full)
        }),
      sourceGenerators in Test +=
        (scalaBinaryVersion.value match {
          case "2.10" =>
            task(Seq.empty[File])
          case "2.12" =>
            task(Seq.empty[File]) // TODO: add ammonite when it supports Scala 2.12.11
          case _ =>
            task {
              val file = (sourceManaged in Test).value / "amm.scala"
              IO.write(file, """object amm extends App { ammonite.Main.main(args) }""")
              Seq(file)
            }
        }),
      /* } Ammonite-REPL */
      /* Bintray { */
      bintrayPackageLabels := Seq("Scala", "Sys", "Sys Process"),
      bintrayVcsUrl := Some(s"""https://github.com/$GitHubUsername/$TheProjectName"""),
      licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
      /* } Bintray */

    )

lazy val justSysprocess = projectCommonSettings("justSysprocess", ProjectName(""), file("."))
  .enablePlugins(DevOopsGitReleasePlugin)
  .settings(
    description := "Sys Process Util",
  /* GitHub Release { */
    gitTagFrom := "main",
  /* } GitHub Release */
    unmanagedSourceDirectories in Compile ++= {
      val sharedSourceDir = baseDirectory.value / "src/main"
      if (scalaVersion.value.startsWith("2.13") || scalaVersion.value.startsWith("2.12"))
        Seq(sharedSourceDir / "scala-2.12_2.13")
      else
        Seq.empty
    },
    libraryDependencies :=
    crossVersionProps(
        List.empty
      , SemVer.parseUnsafe(scalaVersion.value)
    ) {
        case (Major(2), Minor(10)) =>
          libraryDependencies.value.filterNot(m => m.organization == "org.wartremover" && m.name == "wartremover")
        case x =>
          libraryDependencies.value
      },
    initialCommands in console :=
      """import just.sysprocess._"""

  )

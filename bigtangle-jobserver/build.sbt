import Dependencies._
import Versions._

val javaTargetVersion = sys.props.getOrElse("JAVATARGET", default = "1.8") 
lazy val commonSettings: Seq[Def.Setting[_]] = Defaults.coreDefaultSettings ++ Seq(
  organization := "net.bigtangle",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.11",
  resolvers ++= Dependencies.repos,
  credentials += Credentials(Path.userHome / ".sbt" / ".credentials"),
  dependencyOverrides += "org.scala-lang" % "scala-library" % scalaVersion.value,
  parallelExecution in Test := false,

javacOptions ++= Seq("-source", javaTargetVersion, "-target", javaTargetVersion),
scalacOptions := Seq(s"-target:jvm-$javaTargetVersion"),
  scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature"),
  // We need to exclude jms/jmxtools/etc because it causes undecipherable SBT errors  :(
  ivyXML :=
    <dependencies>
    <exclude module="jms"/>
    <exclude module="jmxtools"/>
    <exclude module="jmxri"/>
    </dependencies>
)

lazy val rootSettings = Seq(
  // Must run Spark tests sequentially because they compete for port 4040!
  parallelExecution in Test := false,

  // disable test for root project
  test := {}
)

lazy val root = (project in file("."))
  .settings(
    commonSettings,
    rootSettings,
    name := "bigtangle-jobserver",
    libraryDependencies ++= sparkDeps ++ typeSafeConfigDeps ++ sparkExtraDeps ++ coreTestDeps
      ++ jobserverDeps,
    test in assembly := {},
    fork in Test := true,
     EclipseKeys.withSource := true
)

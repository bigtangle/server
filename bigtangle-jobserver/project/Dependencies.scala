import sbt._

object Dependencies {

  import Versions._

  //NettyIo is very bad one, the organization name is different from the jar name for older versions
  val excludeNettyIo = ExclusionRule(organization = "org.jboss.netty")

  lazy val sparkDeps = Seq(
    "org.apache.spark" %% "spark-core" % spark % "provided" withSources () excludeAll (excludeNettyIo),
    // Force netty version.  This avoids some Spark netty dependency problem.
    "io.netty" % "netty-all" % netty)

  lazy val sparkExtraDeps = Seq(
    //        "org.apache.hadoop" % "hadoop-client" % hadoop % Provided excludeAll(excludeNettyIo),
    "org.apache.spark" %% "spark-mllib" % spark % Provided excludeAll (excludeNettyIo),
    "org.apache.spark" %% "spark-graphx" % spark % Provided excludeAll (excludeNettyIo),
    "org.apache.spark" %% "spark-sql" % spark % Provided excludeAll (excludeNettyIo),
    "org.apache.spark" %% "spark-streaming" % spark % Provided excludeAll (excludeNettyIo),
    "org.apache.spark" %% "spark-streaming-kafka-0-10" % spark % Provided excludeAll (excludeNettyIo),

    //  "org.apache.spark" %% "spark-sql" % spark % "provided" withSources () withJavadoc () excludeAll (excludeNettyIo),
    //  "org.apache.spark" %% "spark-hive" % spark % "provided" withSources () withJavadoc () excludeAll (excludeNettyIo),
    //  "org.apache.spark" %% "spark-streaming" % spark % "provided" withSources () withJavadoc () excludeAll (excludeNettyIo),
    "com.databricks" %% "spark-csv" % "1.5.0" withSources () withJavadoc (),
    //  "com.databricks" %% "spark-avro" % "1.0.0" withSources () withJavadoc (),
    //  "org.apache.hbase" % "hbase-client" % "1.0.0" % "provided" excludeAll ExclusionRule(organization = "org.mortbay.jetty"),
    //  "org.apache.hbase" % "hbase-common" % "1.0.0" % "provided" excludeAll ExclusionRule(organization = "org.mortbay.jetty"),
    //  "org.apache.hbase" % "hbase-server" % "1.0.0" % "provided" excludeAll ExclusionRule(organization = "org.mortbay.jetty"),
    "com.google.code.gson" % "gson" % "2.2.4",
    "net.bigtangle" % "bigtangle-core" % "0.3.0",
    // "graphframes" %  "graphframes" % "0.5.0-spark2.1-s_2.11",
    "org.mongodb.spark" %% "mongo-spark-connector" % "1.0.0" excludeAll ExclusionRule(organization = "org.apache.hadoop"))

  lazy val jobserverDeps = Seq(
    "spark.jobserver" %% "job-server-api" % jobServer % "provided",
    "spark.jobserver" %% "job-server-extras" % jobServer % "provided")

  // This is needed or else some dependency will resolve to 1.3.1 which is in jdk-8
  lazy val typeSafeConfigDeps = Seq("com.typesafe" % "config" % typesafeConfig force ())

  lazy val coreTestDeps = Seq()

  lazy val gdDeps = Seq(
    "com.google.code.gson" % "gson" % "2.2.4")

  val repos = Seq(
    "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/",
    "Job Server Bintray" at "https://dl.bintray.com/spark-jobserver/maven",
    "Local Maven" at "file:///" + Path.userHome + "/.m2/repository",
    "jitpack" at "https://jitpack.io")
}

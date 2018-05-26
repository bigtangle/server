object Versions {
  lazy val netty = "4.0.33.Final"
  lazy val jobServer = "0.8.0"
  lazy val spark = sys.env.getOrElse("SPARK_VERSION", "2.3.0")
  lazy val typesafeConfig = "1.2.1"
}

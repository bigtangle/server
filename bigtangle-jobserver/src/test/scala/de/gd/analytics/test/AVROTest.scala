package de.gd.analytics.test

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext

import com.typesafe.config.ConfigFactory

import de.gd.analytics.sparkjob.SparkSQLData
import org.apache.spark.sql.SaveMode

object AVROTest {
  def main(args: Array[String]) {
    val conf = new SparkConf().setMaster("local[4]").setAppName("aJob")
    val sc = new SparkContext(conf)
    val config = ConfigFactory.parseString("")
    System.setProperty("HADOOP_USER_NAME", "hdfs");
   
    val sqlContext = new SQLContext(sc)
    val hiveContext = new org.apache.spark.sql.SQLContext(sc)
    val banknote = SparkSQLData.registerAVRO("hdfs://quickstart.cloudera:8020/tmp/Banknote", "banknote", sqlContext)
    val p = sqlContext.table("banknote")
    p.printSchema()
    //  SparkSQLData.writeTable(, "banknote", sqlContext, "csv")
    p.write.format("com.databricks.spark.csv")
      .option("header", "true") //
      .option("inferSchema", "true") //
      .save("hdfs://localhost:8020/tmp/Banknote_csv")
  }
}
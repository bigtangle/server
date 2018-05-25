package de.gd.analytics.test

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext

import com.typesafe.config.ConfigFactory

import de.gd.analytics.sparkjob.SparkSQLData
import de.gd.analytics.sparkjob.udf.GeometricMean

object SparkUDFTest {
  def main(args: Array[String]) {
    val conf = new SparkConf().setMaster("local[4]").setAppName("FirstJob")
    val sc = new SparkContext(conf)
    val config = ConfigFactory.parseString("")
    System.setProperty("HADOOP_USER_NAME", "hdfs");
    val sqlContext = new SQLContext(sc)

    val ids = sqlContext.range(1, 20)
    ids.registerTempTable("ids")
    val df = sqlContext.sql("select id, id % 3 as group_id from ids")
    df.registerTempTable("simple")
    sqlContext.udf.register("gm", new GeometricMean)
    val j = sqlContext.sql("select group_id, gm(id) from simple group by group_id")
    j.show
  }

}

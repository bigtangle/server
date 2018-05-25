package de.gd.analytics.test

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext

import com.typesafe.config.ConfigFactory

object UpdateTest {
  def main(args: Array[String]) {
    val conf = new SparkConf().setMaster("local[4]").setAppName("FirstJob")
    val sc = new SparkContext(conf)
    val config = ConfigFactory.parseString("")
    System.setProperty("HADOOP_USER_NAME", "hdfs");
    val sql = new SQLContext(sc)

    val tabledata = "{\"financial_year\":\"2004-05\",\"state\":\"TAS\",\"area_of_expenditure\":\"Community health\",\"broad_source_of_funding\":\"Government\",\"detailed_source_of_funding\":\"Australian Government\",\"real_expenditure_millions\":\"13\"}"

    val rdd = sql.sparkContext parallelize (Seq(tabledata))
    val dataframe = sql.read.json(rdd)

    // this is used to implicitly convert an RDD to a DataFrame.

    dataframe.schema
    // sqlContext.applySchema

   // dataframe.foreach(println)

//    val s = dataframe.map(row => {
//      val col1 = row.getAs[String](1)
//      val make = if (col1.toLowerCase == "Government".toLowerCase) "S" else col1
//      Row(row(0), make, row(2))
//    }).collect()
//    s.foreach(println)

   

    // dataframe.withColumn("newstate", org.apache.spark.sql.functions.lit(1))

    val coldata = "{\"financial_year\":\"2004-05\",\"newstate\":\"NEWTAS\"}"

    val rddcol = sql.sparkContext parallelize (Seq(coldata))
    val dataframecol = sql.read.json(rddcol)
    dataframecol.printSchema()
    dataframecol.registerTempTable("dataframecol")
    dataframe.registerTempTable("dataframe")
    val join = sql.sql("select * from dataframe, dataframecol where dataframe.financial_year= dataframecol.financial_year")
    join.collect().foreach(println)
  }
  import scala.reflect.runtime.universe.TypeTag
  def createTuple2[Type_x: TypeTag, Type_y: TypeTag] = org.apache.spark.sql.functions.udf[(Type_x, Type_y), Type_x, Type_y]((x: Type_x, y: Type_y) => (x, y))
}

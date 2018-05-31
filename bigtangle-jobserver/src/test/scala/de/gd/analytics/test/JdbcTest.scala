package de.gd.analytics.test

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext

import com.typesafe.config.ConfigFactory
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.Graph
import net.bigtangle.core.Sha256Hash
import net.bigtangle.core.NetworkParameters
import net.bigtangle.params.UnitTestParams
import net.bigtangle.core.BlockWrap

object JdbcTest {
  def main(args: Array[String]) {
    val conf = new SparkConf().setMaster("local[4]").setAppName("FirstJob")
    val sc = new SparkContext(conf)
    val config = ConfigFactory.parseString("")
    System.setProperty("HADOOP_USER_NAME", "hdfs");
    val sql = new SQLContext(sc)

    // val tabledata = "{\"financial_year\":\"2004-05\",\"state\":\"TAS\",\"area_of_expenditure\":\"Community health\",\"broad_source_of_funding\":\"Government\",\"detailed_source_of_funding\":\"Australian Government\",\"real_expenditure_millions\":\"13\"}"

    Class.forName("com.mysql.jdbc.Driver").newInstance

    val sqlContext = new SQLContext(sc)

    val data = sqlContext.read
      .format("jdbc")
      .option("url", "jdbc:mysql://localhost:3306/info")
      .option("user", "root")
      .option("password", "test1234")
      .option("dbtable", "headers")
      .load()
    data.printSchema()

    val df = data.select("hash", "prevblockhash", "prevbranchblockhash", "header")

    //as  Followers prevblockhash follows hash
    //            prevbranchblockhash  follows hash
    val rows: RDD[Row] = df.rdd
    val bytestoLong = (payload: Array[Byte]) => {
      Sha256Hash.of(payload).toBigInteger().longValue()
    }

    val bytestoBlock = (data: Array[Byte]) =>
      {  new BlockWrap( data, UnitTestParams.get()) };

    val myVertices = rows.map(
        row => (bytestoLong(row.getAs[Array[Byte]](0)), bytestoBlock(row.getAs[Array[Byte]](3))))
    val myEdges = rows.map(row =>
      (Edge(bytestoLong(row.getAs[Array[Byte]](1)), bytestoLong(row.getAs[Array[Byte]](0)), "")))
    val myGraph = Graph(myVertices, myEdges)

    myGraph.vertices.collect

    // Run PageRank
    val ranks = myGraph.pageRank(0.0001).vertices
    println(ranks.collect().mkString("\n"))

    //  df.foreach(  attributes => "Name: " + attributes(0))
    //val myVertices = df.map( _.getByte(0).toLong )

    // val edgeRDD = edgeDF.map { row => Edge(row.getByte(1).toLong, row.getByte(0).toLong, "") }

    //
    //    val graph = Graph.fromEdges[Int, Double](edgesRDD, 0)

  }

}
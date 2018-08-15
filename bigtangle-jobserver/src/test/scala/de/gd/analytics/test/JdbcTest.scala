package de.gd.analytics.test

import org.apache.spark.SparkConf
import com.google.common.base.Stopwatch;
import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext
import org.apache.spark.graphx.Pregel
import org.apache.spark.graphx
import org.apache.spark.graphx._

import com.typesafe.config.ConfigFactory
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.Graph
import net.bigtangle.core.Sha256Hash
import net.bigtangle.core.NetworkParameters
import net.bigtangle.params.UnitTestParams
import net.bigtangle.core.BlockWrap
import net.bigtangle.core.BlockEvaluation
import java.util.concurrent.TimeUnit

// Run a test from MilestoneServiceTest, then this to validate our algorithm in scala
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

    val blocks = sqlContext.read
      .format("jdbc")
      .option("url", "jdbc:mysql://localhost:3306/info")
      .option("user", "root")
      .option("password", "test1234")
      .option("dbtable", "blocks")
      .load()
    blocks.printSchema()

    blocks.createOrReplaceTempView("blocks")

    val SELECT_SQL = "SELECT  hash, rating, depth, cumulativeweight, height, milestone, milestonelastupdate, milestonedepth, inserttime, maintained ,  prevblockhash ,  prevbranchblockhash ,  block FROM blocks "

    val df = sqlContext.sql(SELECT_SQL)

    //as  Followers prevblockhash follows hash
    //            prevbranchblockhash  follows hash
    val rows: RDD[Row] = df.rdd
    val bytestoLong = (payload: Array[Byte]) => {
      Sha256Hash.of(payload).toBigInteger().longValue()
    }

    val bytestoBlock = (data: Array[Byte], eval: BlockEvaluation) =>
      { new BlockWrap(data, eval, UnitTestParams.get()) };
    val toBlockEvaluation = (x$1: Sha256Hash, x$2: Long, x$3: Long, x$4: Long, x$5: Long, x$6: Boolean, x$7: Long, x$8: Long, x$9: Long, x$10: Boolean) =>
      { BlockEvaluation.build(x$1, x$2, x$3, x$4, x$5, x$6, x$7, x$8, x$9, x$10) };

    val myVertices = rows.map(
      row => (bytestoLong(row.getAs[Array[Byte]](0)), bytestoBlock(
        row.getAs[Array[Byte]](12),
        toBlockEvaluation(
          Sha256Hash.wrap(row.getAs[Array[Byte]](0)),
          row.getLong(1),
          row.getLong(2),
          row.getLong(3),
          row.getLong(4),
          row.getBoolean(5),
          row.getLong(6),
          row.getLong(7),
          row.getLong(8),
          row.getBoolean(9)))))

    // TODO use byte arrays for vertex ids
    val myEdges = rows.filter(row => !Sha256Hash.wrap(row.getAs[Array[Byte]](0)).equals(UnitTestParams.get.getGenesisBlock.getHash)).map(row =>
      (Edge(bytestoLong(row.getAs[Array[Byte]](0)), bytestoLong(row.getAs[Array[Byte]](10)), "")))
    val myEdges2 = rows.filter(row => !Sha256Hash.wrap(row.getAs[Array[Byte]](0)).equals(UnitTestParams.get.getGenesisBlock.getHash)).map(row =>
      (Edge(bytestoLong(row.getAs[Array[Byte]](0)), bytestoLong(row.getAs[Array[Byte]](11)), "")))
    val myGraph = Graph(myVertices, myEdges.union(myEdges2))
    val originalBlocks = myGraph.vertices.collect
    
    // Run test for update depth
    val watch = Stopwatch.createStarted();
    val depthUpdatedBlocks = updateDepth(myGraph).vertices.collect
    print("Update time " + watch.elapsed(TimeUnit.MILLISECONDS));
    assert(originalBlocks.map(e => e._2.getBlockEvaluation.getDepth).deep == depthUpdatedBlocks.map(e => e._2.getBlockEvaluation.getDepth).deep)
    
    // TODO rating select tips, then (weight depth milestonedepth rating), then milestone, then maintained separately
    // TODO both in normal pregel (good since allows for max iterations) and custom pregel
  }

  /**
   * Test spark implementation for depth updates
   */
  def updateDepth(targetGraph: Graph[BlockWrap, String]): Graph[BlockWrap, String] = {
    
    targetGraph.cache()
    val maxHeight = targetGraph.vertices.map(_._2.getBlockEvaluation.getHeight).reduce(Math.max(_, _))

    // Define the three functions needed to implement depth updates in the GraphX
    // version of Pregel
    def vertexProgram(id: VertexId, attr: BlockWrap, msgSum: Long): BlockWrap = {
      val eval = new BlockEvaluation(attr.getBlockEvaluation)
      eval.setDepth(msgSum)
      new BlockWrap(attr.getBlock.bitcoinSerialize(), eval, UnitTestParams.get)
    }

    def sendMessage(edge: EdgeTriplet[BlockWrap, String]) = {
      val src = edge.srcAttr.getBlockEvaluation
      val dst = edge.dstAttr.getBlockEvaluation
      if (src.isMaintained()) {
        Iterator((edge.dstId, src.getDepth + 1L))
      } else {
        Iterator.empty
      }
    }

    def messageCombiner(a: Long, b: Long): Long = {
      Math.max(a, b)
    }

    // The initial message received by all vertices in the update
    val initialMessage = 0L

    // Execute a dynamic version of Pregel.
    Pregel(targetGraph, initialMessage, Int.MaxValue, EdgeDirection.Out)(
      vertexProgram, sendMessage, messageCombiner)
//    HeightDescendingPregel(targetGraph, initialMessage, Int.MaxValue, EdgeDirection.Out, maxHeight)(
//      vertexProgram, sendMessage, messageCombiner)
  }
}

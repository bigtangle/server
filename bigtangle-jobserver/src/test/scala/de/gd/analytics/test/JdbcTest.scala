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
import java.util.HashSet
import java.io.ObjectOutputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ByteArrayInputStream


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
    val myGraph = Graph(myVertices, myEdges.union(myEdges2)).cache
    val originalBlocks = myGraph.vertices.collect

    // Run test for update depth
    val watch = Stopwatch.createStarted();
    val updatedGraph = update(myGraph).cache
    val updatedBlocks = updatedGraph.vertices.collect
    print("Update time " + watch.elapsed(TimeUnit.MILLISECONDS));
    
    // Debug output
//    updatedBlocks.map(_._2).sortBy(b => b.getBlock.getHashAsString).foreach(b => {
//      println(b.getBlock.getHashAsString)
//      print(" depth:")
//      println(b.getBlockEvaluation.getDepth)
//      print(" mdepth:")
//      println(b.getBlockEvaluation.getMilestoneDepth)
//      print(" weight:")
//      println(b.getBlockEvaluation.getCumulativeWeight)
//      println(" ;")
//    })
    
    

    assert(originalBlocks.map(e => e._2.getBlockEvaluation.getDepth).deep == updatedBlocks.map(e => e._2.getBlockEvaluation.getDepth).deep)
    assert(originalBlocks.map(e => e._2.getBlockEvaluation.getMilestoneDepth).deep == updatedBlocks.map(e => e._2.getBlockEvaluation.getMilestoneDepth).deep)
    assert(originalBlocks.map(e => e._2.getBlockEvaluation.getCumulativeWeight).deep == updatedBlocks.map(e => e._2.getBlockEvaluation.getCumulativeWeight).deep)

    // TODO rating select tips, then (weight depth milestonedepth rating), then milestone, then maintained separately
  }

  /**
   * Test spark implementation for depth updates
   */
  def update(targetGraph: Graph[BlockWrap, String]): Graph[BlockWrap, String] = {
    targetGraph.cache()
    val maxHeight = targetGraph.vertices.map(_._2.getBlockEvaluation.getHeight).reduce(Math.max(_, _))

    // Define the three functions needed to implement depth updates in the GraphX
    // version of Pregel
    def vertexProgram(id: VertexId, attr: BlockWrap, msgSum: (HashSet[Sha256Hash], Long, Long)): BlockWrap = {
      val eval = new BlockEvaluation(attr.getBlockEvaluation)

      // do nothing if not maintained
      if (!eval.isMaintained())
        attr

      // initial msg handling (initial msg always has depth == 0)
      if (msgSum._2 == 0L) {
        //eval.setWeightHashes(new HashSet[Sha256Hash]())
        eval.getWeightHashes.add(eval.getBlockHash)
      }

      eval.getWeightHashes.addAll(msgSum._1)

      eval.setCumulativeWeight(eval.getWeightHashes.size())
      eval.setDepth(msgSum._2)
      eval.setMilestoneDepth(msgSum._3)

      new BlockWrap(attr.getBlock.bitcoinSerialize(), eval, UnitTestParams.get)
    }

    def sendMessage(edge: EdgeTriplet[BlockWrap, String]) = {
      val src = edge.srcAttr.getBlockEvaluation
      val dst = edge.dstAttr.getBlockEvaluation
      if (src.isMaintained()) {
        Iterator((edge.dstId, (src.getWeightHashes, src.getDepth + 1L, if (dst.isMilestone) src.getMilestoneDepth + 1L else src.getMilestoneDepth)))
      } else {
        Iterator.empty
      }
    }

    def messageCombiner(a: (HashSet[Sha256Hash], Long, Long), b: (HashSet[Sha256Hash], Long, Long)): (HashSet[Sha256Hash], Long, Long) = {
      val approvingHashes = new HashSet[Sha256Hash]()
      approvingHashes.addAll(a._1)
      approvingHashes.addAll(b._1)
      (approvingHashes, Math.max(a._2, b._2), Math.max(a._3, b._3))
    }

    // The initial message received by all vertices in the update
    val initialMessage = (new HashSet[Sha256Hash](), 0L, -1L)

    // Execute a dynamic version of Pregel.
    Pregel(targetGraph, initialMessage, Int.MaxValue, EdgeDirection.Out)(
      vertexProgram, sendMessage, messageCombiner)
    //    HeightDescendingPregel(targetGraph, initialMessage, Int.MaxValue, EdgeDirection.Out, maxHeight)(
    //      vertexProgram, sendMessage, messageCombiner)
  }
}

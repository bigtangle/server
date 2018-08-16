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
import net.bigtangle.core.BlockWrapSpark
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
      { new BlockWrapSpark(data, eval, UnitTestParams.get()) };
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

    assert(originalBlocks.map(e => e._2.getBlock).deep == updatedBlocks.map(e => e._2.getBlock).deep)
    assert(originalBlocks.map(e => e._2.getBlockEvaluation.getDepth).deep == updatedBlocks.map(e => e._2.getBlockEvaluation.getDepth).deep)
    assert(originalBlocks.map(e => e._2.getBlockEvaluation.getMilestoneDepth).deep == updatedBlocks.map(e => e._2.getBlockEvaluation.getMilestoneDepth).deep)
    assert(originalBlocks.map(e => e._2.getBlockEvaluation.getCumulativeWeight).deep == updatedBlocks.map(e => e._2.getBlockEvaluation.getCumulativeWeight).deep)

    // TODO use dynamic MAX_STEPS
    // TODO rating select tips, then (weight depth milestonedepth rating), then milestone, then maintained separately

    // MCMC Scheme for askTransaction
    // as found in reference implementation?
    // or microbatching blockaddition+pregelselection as with rating tips

    // to fix the problem of long candlesticks eating computational resources during MCMC, we shall
    // use pregel to precompute conflictpoint sets of maintained blocks (which is all blocks reachable by MCMC)
    // then batch select via pregel MCMC:
    //Up to MAX_STEPS steps far
    //If reaching MAX_STEPS and still not a tip, we can:
    // Simply fail -> Deadlock potential if everything always fails
    // Simply take the current block as tip -> adds weight to the path to encourage milestoning it, but also loses hashpower and mostly creates orphans
    //this would enable a candlestick attack, since building the heaviest candlestick makes everyone else waste hashpower
    //however, this is fine. we can calculate the lost hashpower to be very low if alpha is very low.
    //it suffices to set alpha<=3ln2/(tps*confirmationdelay) to keep divergence probability approximately over 1/3
    //this does, however, allow for more rewards for the candlestick attacker, since the lost hashpower is usually going to orphan
    //also, this allows for skipping whole height intervals, which is why 1. we need non-fixed reward intervals and 2.
    //but there will be an equilibrium ??? TODO

    // Update Scheme (MAX_STEPS = MAXVALUE FOR NOW SO IT WILL WORK ALWAYS, ALTHOUGH INFINITELY SLOW IN CASE OF SUCCESSFUL ATTACK)

    // PHASE 2: run MCMC for rating tips (needs conflictpoints since we don't want invalid blocks to hijack rating)
    //Assume constant graph, then use Pregel and some kind of pair synchronization
    // PHASE 4: new milestone blocks are evaluated: get subgraph of approved non-milestone of new milestone blocks and mapreduce to conflictpoint sets
    //resolveconflicts sequentially? (can be parallelized), add to milestone, then persist if wanted (old graph.join)
    // PHASE 5: set maintain = reachable by MCMC in MAX_STEPS, then persist (old graph.join)
    //can also additionally unmaintain unconfirmed blocks where conflicting with confirmed unmaintained milestone

    // Maintained has three states: confirmed unmaintained (ideally prunable after a while), unconfirmed maintained and unconfirmed unmaintained (newest blocks out of reach)
    // this will prevent the problem of needing to maintain infinitely many blocks in ddos
  }

  /**
   * Test spark implementation for phase 1
   */
  // PHASE 1: batch precompute (maintained only) MAX_STEPS @vertex: conflictpoint sets + milestone validity, outgoing weight unnormalized, transient dicerolls
  //@edges: applicable diceroll interval for sendmsg
  def phase1(targetGraph: Graph[BlockWrapSpark, String]): Graph[BlockWrapSpark, String] = {
    // TODO dynamic step amount
    val MAX_STEPS = 500

    // For maintained part of the graph only
    val maintainedGraph = targetGraph.subgraph(
      vpred = (vid, vdata: BlockWrapSpark) => vdata.getBlockEvaluation.isMaintained())

    // The initial message received by all vertices in the update
    val initialMessage = (new HashSet[Sha256Hash](), 0L, -1L)

    // Define the three functions needed to implement updates in the GraphX
    // version of Pregel
    def vertexProgram(id: VertexId, prevBlockWrap: BlockWrapSpark, msgSum: (HashSet[Sha256Hash], Long, Long)): BlockWrapSpark = {
      // Skip updating if not maintained

      // Initialization (initial msg always has depth == 0)

      // Calculate new statistics

      // Build new updated BlockWrap instance
    }

    def sendMessage(edge: EdgeTriplet[BlockWrapSpark, String]) = {
      // Skip sending to and from unmaintained blocks

      // Calculate messages
    }

    def messageCombiner(a: (HashSet[Sha256Hash], Long, Long), b: (HashSet[Sha256Hash], Long, Long)): (HashSet[Sha256Hash], Long, Long) = {
      // Combine multiple incoming messages into one

    }

    // Execute a dynamic version of Pregel.
    Pregel(targetGraph, initialMessage, Int.MaxValue, EdgeDirection.Out)(
      vertexProgram, sendMessage, messageCombiner)
  }

  /**
   * Test spark implementation for depth updates
   */
  // PHASE 3: update for (unconfirmed), TODO add rating and MAX_STEPS, then persist if wanted (old graph.join)
  def update(targetGraph: Graph[BlockWrapSpark, String]): Graph[BlockWrapSpark, String] = {

    // The initial message received by all vertices in the update
    val initialMessage = (new HashSet[Sha256Hash](), 0L, -1L)

    // Define the three functions needed to implement updates in the GraphX
    // version of Pregel
    def vertexProgram(id: VertexId, prevBlockWrap: BlockWrapSpark, msgSum: (HashSet[Sha256Hash], Long, Long)): BlockWrapSpark = {
      // Skip updating if not maintained
      if (!prevBlockWrap.getBlockEvaluation.isMaintained())
        prevBlockWrap

      // Initialization (initial msg always has depth == 0)
      if (msgSum._2 == 0L) {
        prevBlockWrap.setWeightHashes(new HashSet[Sha256Hash]())
        prevBlockWrap.getWeightHashes.add(prevBlockWrap.getBlockHash)
      }

      // Calculate new statistics
      val statistics = new BlockEvaluation(prevBlockWrap.getBlockEvaluation)
      prevBlockWrap.getWeightHashes.addAll(msgSum._1)
      statistics.setCumulativeWeight(prevBlockWrap.getWeightHashes.size())
      statistics.setDepth(msgSum._2)
      statistics.setMilestoneDepth(msgSum._3)

      // Build new updated BlockWrap instance
      val updatedBlockWrap = new BlockWrapSpark(prevBlockWrap.getBlock, statistics, UnitTestParams.get)
      updatedBlockWrap.setWeightHashes(prevBlockWrap.getWeightHashes)
      updatedBlockWrap
    }

    def sendMessage(edge: EdgeTriplet[BlockWrapSpark, String]) = {
      // Skip sending to and from unmaintained blocks
      if (!edge.srcAttr.getBlockEvaluation.isMaintained() || !edge.dstAttr.getBlockEvaluation.isMaintained()) {
        Iterator.empty
      }

      val srcEval = edge.srcAttr.getBlockEvaluation
      val dstEval = edge.dstAttr.getBlockEvaluation

      // Calculate messages
      val sentWeightHashes = edge.srcAttr.getWeightHashes
      val sentDepth = srcEval.getDepth + 1L;
      val sentMilestoneDepth = if (dstEval.isMilestone) srcEval.getMilestoneDepth + 1L else srcEval.getMilestoneDepth;

      Iterator((edge.dstId, (sentWeightHashes, sentDepth, sentMilestoneDepth)))
    }

    def messageCombiner(a: (HashSet[Sha256Hash], Long, Long), b: (HashSet[Sha256Hash], Long, Long)): (HashSet[Sha256Hash], Long, Long) = {
      // Combine multiple incoming messages into one
      val mergedWeightHashes = new HashSet[Sha256Hash]()
      mergedWeightHashes.addAll(a._1)
      mergedWeightHashes.addAll(b._1)
      val mergedDepth = Math.max(a._2, b._2);
      val mergedMilestoneDepth = Math.max(a._3, b._3);

      (mergedWeightHashes, mergedDepth, mergedMilestoneDepth)
    }

    // Execute a dynamic version of Pregel.
    Pregel(targetGraph, initialMessage, Int.MaxValue, EdgeDirection.Out)(
      vertexProgram, sendMessage, messageCombiner)

    //val maxHeight = targetGraph.vertices.map(_._2.getBlockEvaluation.getHeight).reduce(Math.max(_, _))
    //HeightDescendingPregel(targetGraph, initialMessage, Int.MaxValue, EdgeDirection.Out, maxHeight)(
    //  vertexProgram, sendMessage, messageCombiner)
  }
}

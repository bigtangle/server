
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
import net.bigtangle.spark.BlockWrapSpark
import net.bigtangle.core.BlockEvaluation
import java.util.concurrent.TimeUnit
import java.util.HashSet
import java.io.ObjectOutputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ByteArrayInputStream
import net.bigtangle.core.ConflictCandidate
import scala.collection.immutable.Nil
import java.util.ArrayList
import scala.collection.mutable.ListMap
import net.bigtangle.spark.BlockWrapSpark

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
      (Edge(bytestoLong(row.getAs[Array[Byte]](0)), bytestoLong(row.getAs[Array[Byte]](10)), (0.0, 0.0))))
    val myEdges2 = rows.filter(row => !Sha256Hash.wrap(row.getAs[Array[Byte]](0)).equals(UnitTestParams.get.getGenesisBlock.getHash)).map(row =>
      (Edge(bytestoLong(row.getAs[Array[Byte]](0)), bytestoLong(row.getAs[Array[Byte]](11)), (0.0, 0.0))))
    val myGraph = Graph(myVertices, myEdges.union(myEdges2)).cache
    val originalBlocks = myGraph.vertices.collect

    // Run test for update depth
    val watch = Stopwatch.createStarted();
    val updatedGraph = phase3(myGraph).cache
    val updatedBlocks = updatedGraph.vertices.collect
    print("Update time " + watch.elapsed(TimeUnit.MILLISECONDS));

    //Debug output
    //    updatedBlocks.map(_._2).sortBy(b => b.getBlock.getHashAsString).foreach(b => {
    //      println(b.getBlock.getHashAsString)
    //      print(" weight:")
    //      println(b.getBlockEvaluation.getCumulativeWeight)
    //    })

    assert(originalBlocks.map(e => e._2.getBlock).deep == updatedBlocks.map(e => e._2.getBlock).deep)
    assert(originalBlocks.map(e => e._2.getBlockEvaluation.getDepth).deep == updatedBlocks.map(e => e._2.getBlockEvaluation.getDepth).deep)
    assert(originalBlocks.map(e => e._2.getBlockEvaluation.getMilestoneDepth).deep == updatedBlocks.map(e => e._2.getBlockEvaluation.getMilestoneDepth).deep)
    assert(originalBlocks.map(e => e._2.getBlockEvaluation.getCumulativeWeight).deep == updatedBlocks.map(e => e._2.getBlockEvaluation.getCumulativeWeight).deep)

    print(sc.getCheckpointDir)

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
    //- fail
    //-> Deadlock if everything always fails
    //- take the current block as tip
    //-> adds weight to the path to encourage milestoning it, but also loses hashpower and mostly creates orphans
    //this would enable a candlestick attack, since building the heaviest candlestick makes everyone else waste hashpower
    //however, this is fine. we can calculate the lost hashpower to be very low if alpha is very low.
    //it suffices to set alpha<=3ln2/(tps*confirmationdelay) to keep divergence probability approximately over 1/3
    //this does, however, allow for more rewards for the candlestick attacker, since the lost hashpower is usually going to orphan
    //also, this allows for skipping whole height intervals, which is why 1. we need non-fixed reward intervals and
    //2. (if we make blocks conflict with unapproved blocks of height lower than own height - x it would fix itself since candlesticks become invalid)
    //there must be an equilibrium (only exists if candlesticks invalid as above) ??? TODO

    // Update Scheme (MAX_STEPS = MAXVALUE FOR NOW SO IT WILL WORK ALWAYS, ALTHOUGH INFINITELY SLOW IN CASE OF SUCCESSFUL ATTACK)

    // PHASE 2: run MCMC for rating tips (needs conflictpoints since we don't want invalid blocks to hijack rating)
    //Assume constant graph, then use Pregel and some kind of pair synchronization
    // PHASE 4: new milestone blocks are evaluated: get subgraph of approved non-milestone of new milestone blocks and mapreduce to conflictpoint sets
    //resolveconflicts sequentially? (can be parallelized), add to milestone, then persist if wanted (old graph.join)
    // PHASE 5: set maintain = reachable by MCMC in MAX_STEPS, then persist (old graph.join)
    //can also additionally unmaintain unconfirmed blocks where conflicting with confirmed unmaintained milestone

    //CANNOT set unmaintained in future, would allow deadlock!
    // eligible has three states: computed ok, no and uncomputed

    // Maintained has three states: confirmed unmaintained (ideally prunable after a while), unconfirmed maintained and unconfirmed unmaintained (newest blocks out of reach)
    // this will prevent the problem of needing to maintain infinitely many blocks in ddos
  }

  def transitionWeight(deltaWeight: Long): Double = {
    // TODO
    val alpha = 0.1

    // Calculate transition weight
    Math.exp(-alpha * deltaWeight)
  }

  // TODO try out random walk library
  /**
   * Test spark implementation for phase 1
   */
  // PHASE 1: batch precompute (maintained only) MAX_STEPS @vertex: conflictpoint sets + milestone validity, outgoing weight unnormalized, transient dicerolls
  //@edges: applicable diceroll interval for sendmsg
  def phase1(targetGraph: Graph[BlockWrapSpark, (Double, Double)]): Graph[BlockWrapSpark, (Double, Double)] = {
    // dynamic step count less than infinity may lead to problems, since it could be possible to not catch up anymore.
    //On the other hand, infinity can make it take infinitely long if there is some kind of long snake that is referenced.
    //Since this can happen either way, we need to prevent this by:
    //- setting blocks to conflicting with all blocks lower than e.g. their height - 1000 that are not approved by them
    //-> unscalable
    //-just set any unconfirmed unmaintained approvers also unconfirmed unmaintained?? should work most of the time
    //-> deadlock?
    //- iteratively build the required statistics over multiple iterations of phase 1-5
     

    // For maintained part of the graph only
    var maintainedGraph = targetGraph.subgraph(
      vpred = (vid, vdata: BlockWrapSpark) => vdata.getBlockEvaluation.isMaintained())

    // The initial message received by all vertices in the update
    val initialMessage = new HashSet[ConflictCandidate]

    // Define functions for Pregel
    def vertexProgram(id: VertexId, blockWrap: BlockWrapSpark, msgSum: HashSet[ConflictCandidate]): BlockWrapSpark = {
      // Build new updated BlockWrap instance
      val updatedBlockWrap = new BlockWrapSpark(blockWrap)

      if (msgSum.isEmpty()) {
        // Initialization (initial msg always empty)
        updatedBlockWrap.setApprovedNonMilestoneConflicts(updatedBlockWrap.toConflictCandidates)
        updatedBlockWrap.setReceivedConflictPoints(updatedBlockWrap.toConflictCandidates)
      } else {
        // Else from new message
        updatedBlockWrap.getApprovedNonMilestoneConflicts.addAll(msgSum)
        updatedBlockWrap.setReceivedConflictPoints(msgSum)
      }

      // Return new updated BlockWrap instance
      updatedBlockWrap
    }

    def sendMessage(edge: EdgeTriplet[BlockWrapSpark, (Double, Double)]) = {
      // Calculate messages
      val sentConflicts = edge.srcAttr.getReceivedConflictPoints

      Iterator((edge.dstId, sentConflicts))
    }

    def messageCombiner(a: HashSet[ConflictCandidate], b: HashSet[ConflictCandidate]): HashSet[ConflictCandidate] = {
      // Combine multiple incoming messages into one
      val mergedConflicts = new HashSet[ConflictCandidate]()
      mergedConflicts.addAll(a)
      mergedConflicts.addAll(b)
      mergedConflicts
    }

    //(TODO perhaps maintained as well here)
    // Execute a dynamic version of Pregel to compute conflict candidate sets
    maintainedGraph = Pregel(maintainedGraph, initialMessage, Int.MaxValue, EdgeDirection.Out)(
      vertexProgram, sendMessage, messageCombiner)

    // Calculate milestone validity of blocks
    maintainedGraph = maintainedGraph.mapVertices((vid, vdata) => {
      vdata.setMilestoneConsistent(true)
      vdata
      // TODO port validatorservice to spark?
    })

    // Aggregate outgoing weight unnormalized
    val outgoingWeights = maintainedGraph.aggregateMessages[ListMap[Sha256Hash, Double]](
      triplet => {
        val msg = new ListMap[Sha256Hash, Double]
        msg += (triplet.srcAttr.getBlockHash -> transitionWeight(triplet.dstAttr.getBlockEvaluation.getCumulativeWeight - triplet.srcAttr.getBlockEvaluation.getCumulativeWeight))
        triplet.sendToDst(msg)
      },
      (a, b) => {
        a ++ b
      })
    maintainedGraph = maintainedGraph.joinVertices(outgoingWeights) { (vid, vdata, weights) =>
      {
        val sum = weights.map(_._2).reduce((a, b) => a + b)
        vdata.setIncomingTransitionWeightSum(sum)
        vdata.setIncomingTransitionWeights(weights)
        vdata
      }
    }

    // Give edges their applicable diceroll interval
    maintainedGraph = maintainedGraph.mapTriplets(triplet => {
      val indexedList = triplet.dstAttr.getIncomingTransitionWeights.zipWithIndex
      val index = indexedList.find(p => p._1._1.equals(triplet.srcAttr.getBlockHash)).get._2
      val intervalStart = indexedList.filter(p => p._2 < index).map(p => p._1._2).reduce((a, b) => a + b) / triplet.dstAttr.getIncomingTransitionWeightSum
      val intervalEnd = indexedList.filter(p => p._2 <= index).map(p => p._1._2).reduce((a, b) => a + b) / triplet.dstAttr.getIncomingTransitionWeightSum
      (intervalStart, intervalEnd)
    })

    // Perform dicerolls and MCMC in Pregel
    val walkerInits = maintainedGraph.vertices.take(5)
    // pregel random transitions

    maintainedGraph
  }

  // TODO height difference scaler ameliorates prebuilding (candlestick) a bit at least
  //also, by punishing candlesticks (set flag ineligible if too far) and stopping where we are at it will be fixed?
  /**
   * Test spark implementation for depth updates
   */
  // PHASE 3: update for (maintained), TODO add rating and MAX_STEPS, then persist if wanted (old graph.join)
  def phase3(targetGraph: Graph[BlockWrapSpark, (Double, Double)]): Graph[BlockWrapSpark, (Double, Double)] = {
    // For maintained part of the graph only
    val maintainedGraph = targetGraph.subgraph(
      vpred = (vid, vdata: BlockWrapSpark) => vdata.getBlockEvaluation.isMaintained())

    // The initial message received by all vertices in the update
    val initialMessage = (new HashSet[Sha256Hash](), 0L, -1L)

    // Define functions for Pregel
    def vertexProgram(id: VertexId, blockWrap: BlockWrapSpark, msgSum: (HashSet[Sha256Hash], Long, Long)): BlockWrapSpark = {
      // Build new updated BlockWrap instance
      val updatedBlockWrap = new BlockWrapSpark(blockWrap)

      if (msgSum._2 == 0L) {
        // Initialization (initial msg always has depth == 0)
        updatedBlockWrap.getWeightHashes.add(updatedBlockWrap.getBlockHash)
        updatedBlockWrap.getReceivedWeightHashes.add(updatedBlockWrap.getBlockHash)
      } else {
        // Else from new message
        updatedBlockWrap.getWeightHashes.addAll(msgSum._1)
        updatedBlockWrap.setReceivedWeightHashes(msgSum._1)
      }

      // Calculate new statistics
      updatedBlockWrap.getBlockEvaluation.setCumulativeWeight(updatedBlockWrap.getWeightHashes.size())
      updatedBlockWrap.getBlockEvaluation.setDepth(msgSum._2)
      updatedBlockWrap.getBlockEvaluation.setMilestoneDepth(msgSum._3)

      // Return new updated BlockWrap instance
      updatedBlockWrap
    }

    def sendMessage(edge: EdgeTriplet[BlockWrapSpark, (Double, Double)]) = {
      // Calculate messages
      val sentWeightHashes = edge.srcAttr.getReceivedWeightHashes
      val sentDepth = edge.srcAttr.getBlockEvaluation.getDepth + 1L;
      val sentMilestoneDepth = if (edge.dstAttr.getBlockEvaluation.isMilestone) edge.srcAttr.getBlockEvaluation.getMilestoneDepth + 1L else edge.srcAttr.getBlockEvaluation.getMilestoneDepth;

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
    Pregel(maintainedGraph, initialMessage, Int.MaxValue, EdgeDirection.Out)(
      vertexProgram, sendMessage, messageCombiner)
  }
}

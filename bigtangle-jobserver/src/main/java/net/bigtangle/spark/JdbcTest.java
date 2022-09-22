
package net.bigtangle.spark;

import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockMCMC;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.server.core.ConflictCandidate;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.graphx.*;
import org.apache.spark.rdd.RDD;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SQLContext;
import scala.Array;
import scala.Int;
import scala.Tuple3;
import scala.Tuple3$;
import scala.collection.mutable.ListMap;

import java.util.HashSet;

;

// Run a test from MilestoneServiceTest, then this to validate our algorithm in scala
public class JdbcTest {
    public static void main(String[] args) throws Exception {
     SparkConf  conf = new SparkConf().setMaster("local[4]").setAppName("FirstJob");
        SparkContext sc = new SparkContext(conf);

        String s = System.setProperty("HADOOP_USER_NAME", "hdfs");
        SQLContext    sqlContext = new SQLContext(sc);

    // val tabledata = "{\"financial_year\":\"2004-05\",\"state\":\"TAS\",\"area_of_expenditure\":\"Community health\",\"broad_source_of_funding\":\"Government\",\"detailed_source_of_funding\":\"Australian Government\",\"real_expenditure_millions\":\"13\"}"

    Class.forName("com.mysql.jdbc.Driver").newInstance();


        Dataset<Row> blocks = sqlContext.read()
                .format("jdbc")
                .option("url", "jdbc:mysql://localhost:3306/info")
                .option("user", "root")
                .option("password", "test1234")
                .option("dbtable", "blocks")
                .load();
    blocks.printSchema();

    blocks.createOrReplaceTempView("blocks");

      String SELECT_SQL = "SELECT  hash, rating, depth, cumulativeweight, height, milestone," +
      " milestonelastupdate, milestonedepth, inserttime," +
      "   prevblockhash ,  prevbranchblockhash ,  block FROM blocks ";

        Dataset<Row>   df = sqlContext.sql(SELECT_SQL);

    //as  Followers prevblockhash follows hash
    //            prevbranchblockhash  follows hash
        RDD<Row> rows = df.rdd();
    val bytestoLong = (payload: Array[Byte]) => {
      Sha256Hash.of(payload).toBigInteger().longValue()
    }

    val bytestoBlock = (data: Array[Byte], eval: BlockEvaluation, mcmc: BlockMCMC) =>
      { new BlockWrapSpark(data, eval,mcmc, MainNetParams.get()) };

      rows.map(
      row -> {
           return
                 row.getByte(0)), bytestoBlock(
        row.getAs[Array[Byte]](12),
        BlockEvaluation.build(
          Sha256Hash.wrap(row.getAs[Array[Byte]](0)),
          row.getLong(4),
          row.getLong(2),
          row.getLong(3),
          row.getLong(8),
          row.getLong(8),
          row.getBoolean(9)),
        new   BlockMCMC(  Sha256Hash.wrap(row.getAs[Array[Byte]](0)),
          row.getLong(1),   row.getLong(2),   row.getLong(3))
       )});

    // TODO use byte arrays for vertex ids
    val myEdges = rows.filter(row => !Sha256Hash.wrap(row.getAs[Array[Byte]](0)).equals(MainNetParams.get.getGenesisBlock.getHash)).map(row =>
      (Edge(bytestoLong(row.getAs[Array[Byte]](0)), bytestoLong(row.getAs[Array[Byte]](10)), (0.0, 0.0))))
    val myEdges2 = rows.filter(row => !Sha256Hash.wrap(row.getAs[Array[Byte]](0)).equals(MainNetParams.get.getGenesisBlock.getHash)).map(row =>
      (Edge(bytestoLong(row.getAs[Array[Byte]](0)), bytestoLong(row.getAs[Array[Byte]](11)), (0.0, 0.0))))
    val myGraph = Graph(myVertices, myEdges.union(myEdges2)).cache
    val originalBlocks = myGraph.vertices.collect

    // Run test for update depth

    val updatedGraph = phase3(myGraph).cache
    val updatedBlocks = updatedGraph.vertices.collect
  //  println("Update time " + watch.elapsed(TimeUnit.MILLISECONDS));

    //Debug output
    //    updatedBlocks.map(_._2).sortBy(b => b.getBlock.getHashAsString).foreach(b => {
    //      println(b.getBlock.getHashAsString)
    //      print(" weight:")
    //      println(b.getBlockEvaluation.getCumulativeWeight)
    //    })

    assert(originalBlocks.map(e => e._2).deep == updatedBlocks.map(e => e._2.getBlock).deep)

    println(sc.getCheckpointDir)
    
    println("All ok");

  }
  
  // TODO define max steps forward from non-milestone -> computational complexity stays low enough in the relevant range, anything outside of max range is currently not viable for milestone
  
  // "we cannot precache #blocks approved per block because it is too big" 
  // -> for backwards propagation, it is possible to purge approverhashes that have been passed through after x iterations, where 
  // x is the difference in height between the approver and the block. after that, no duplicates of this approverhash can reach again, hence just drop it.
  // -> for forwards propagation of conflictpoints, it is impossible to purge conflictpoints due to new blocks appearing potentially anywhere, hence max range definition.
  // -> for forwards propagation of hashes for purpose of reward eligibility, it is impossible to purge hashes since else it is not efficiently computable.
  // it is possible however to define max range as max_reward_interval before which's height the reward must exist, but this will explode memory-wise.
  // 
  // THE SOLUTION: the other solution is to backpropagate the reward approval hash and after y iterations, where y is the difference between reward block height and its fromheight.
  // all blocks in the rewarded interval have been touched and can be summed up to determine eligibility of reward block.
  // then we could purge, i.e. very low memory requirements, and we could introduce the sperrzeit until eligibility is determined.
  // this would then require active voting by servers, i.e. starting one tip selection @ the reward block. VOTE FOR MAX RATING
  // then the original eligibility sperrzeit begins from the time the eligibility is determined and is overrided as known by rating etc.
  
  // Garbage cleanup:
  // there exist multiple types of garbage on the tangle:
  // unsolid blocks: purge after a while and blacklist/punish the sending node
  // unmaintained milestone blocks: simply purge after timeout of e.g. 3 days etc. if not "history node"
  // garbage tips that approve some very old milestone blocks can either be in conflict with unmaintained milestone stuff
  // -> drop, or they could be valid -> someone ("janitor nodes") can simply repush them until they are in. 
  
  // Node types:
  // FullPruned nodes: prune unmaintained blocks
  // History nodes: no pruning
  // Janitor nodes: history nodes that help by pushing old orphan tips that are valid (but only those of heights that are already rewarded
  
  
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

  // PHASE 1: batch precompute (maintained only) MAX_STEPS @vertex: conflictpoint sets + milestone validity, outgoing weight unnormalized, transient dicerolls
  //@edges: applicable diceroll interval for sendmsg
  // PHASE 2: run MCMC for rating tips (needs conflictpoints since we don't want invalid blocks to hijack rating)
  //Assume constant graph, then use Pregel and some kind of pair synchronization
  // PHASE 3: update for (maintained), TODO add rating and MAX_STEPS, then persist if wanted (old graph.join)
  // PHASE 4: new milestone blocks are evaluated: get subgraph of approved non-milestone of new milestone blocks and mapreduce to conflictpoint sets
  //resolveconflicts sequentially? (can be parallelized), add to milestone, then persist if wanted (old graph.join)
  // PHASE 5: set maintain = reachable by MCMC in MAX_STEPS, then persist (old graph.join)
  //can also additionally unmaintain unconfirmed blocks where conflicting with confirmed unmaintained milestone

  //CANNOT set unmaintained in future, would allow deadlock!
  // eligible has three states: computed ok, no and uncomputed

  // Maintained has three states: confirmed unmaintained (ideally prunable after a while), unconfirmed maintained and unconfirmed unmaintained (newest blocks out of reach)
  // this will prevent the problem of needing to maintain infinitely many blocks in ddos

  // TODO unmaintained cannot happen if not rewarded yet! also not if still used for milestoning
  
  public Double transitionWeight( Long deltaWeight )  {
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
  public   Graph[BlockWrapSpark, Double, Double] phase1(  Graph[BlockWrapSpark, Double, Double] targetGraph ) {
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
      vpred = (vid, vdata: BlockWrapSpark) => vdata.getBlockEvaluation.isConfirmed)

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

    public Tulpe2<> sendMessage(edge: EdgeTriplet[BlockWrapSpark, (Double, Double)]) = {
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
        msg += (triplet.srcAttr.getBlockHash -> transitionWeight(triplet.dstAttr.getMcmc().getCumulativeWeight
          - triplet.srcAttr.getMcmc.getCumulativeWeight))
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

  // TODO by punishing candlesticks (set flag ineligible if too far) and stopping where we are at it will be fixed?
  /**
   * Test spark implementation for depth updates
   */
  // PHASE 3: update for (maintained), TODO add rating and MAX_STEPS, then persist if wanted (old graph.join)
  def phase3(targetGraph: Graph[BlockWrapSpark, (Double, Double)]): Graph[BlockWrapSpark, (Double, Double)] = {
    // For maintained part of the graph only
    val maintainedGraph = targetGraph.subgraph(
      vpred = (vid, vdata: BlockWrapSpark) => vdata.getBlockEvaluation.isConfirmed())

    // The initial message received by all vertices in the update
    val initialMessage = (new HashSet[Sha256Hash](), 0L, -1L)

    // Define functions for Pregel
      public  BlockWrapSpark vertexProgram(VertexId id, BlockWrapSpark blockWrap, Tuple3<HashSet<Sha256Hash>, Long, Long > msgSum) {
      // Build new updated BlockWrap instance
      val updatedBlockWrap = new BlockWrapSpark(blockWrap);

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
      updatedBlockWrap.getMcmc().setCumulativeWeight(updatedBlockWrap.getWeightHashes.size())
      updatedBlockWrap.getMcmc.setDepth(msgSum._2)
    //  updatedBlockWrap.getMcmc.setMilestoneDepth(msgSum._3)

      // Return new updated BlockWrap instance
      updatedBlockWrap
    }

    public  sendMessage(EdgeTriplet<BlockWrapSpark, Double, Double> edge )   {
      // Calculate messages
      val sentWeightHashes = edge.srcAttr.getReceivedWeightHashes
      val sentDepth = edge.srcAttr.getMcmc().getDepth + 1L;
      val sentMilestoneDepth =  edge.srcAttr.getBlock.getLastMiningRewardBlock ;

      Iterator((edge.dstId, (sentWeightHashes, sentDepth, sentMilestoneDepth)))
    }

    public  Tuple3<HashSet<Sha256Hash>, Long, Long>
        messageCombiner(Tuple3< HashSet<Sha256Hash>, Long, Long> a , Tuple3<HashSet<Sha256Hash>, Long, Long>  b)  {
      // Combine multiple incoming messages into one
            HashSet<Sha256Hash> mergedWeightHashes = new HashSet<Sha256Hash>();
      mergedWeightHashes.addAll(a._1)
      mergedWeightHashes.addAll(b._1)
        mergedDepth = Math.max(a._2, b._2);
        mergedMilestoneDepth = Math.max(a._3, b._3);

       return   new Tuple3<>(mergedWeightHashes, mergedDepth, mergedMilestoneDepth);
    }

    // Execute a dynamic version of Pregel.
    Pregel(maintainedGraph, initialMessage, Integer.MAX_VALUE, EdgeDirection.Out())(
      vertexProgram, sendMessage, messageCombiner);
  }
}

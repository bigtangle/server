/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutionException;

import org.mortbay.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.resp.GetBlockEvaluationsResponse;
import net.bigtangle.kafka.KafkaConfiguration;
import net.bigtangle.kafka.KafkaMessageProducer;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.wallet.CoinSelector;
import net.bigtangle.wallet.DefaultCoinSelector;

/**
 * <p>
 * A Block provides service for blocks with store.
 * </p>
 */
@Service
public class BlockService {

    @Autowired
    protected FullPrunedBlockStore store;

    protected CoinSelector coinSelector = new DefaultCoinSelector();

    @Autowired
    private MilestoneService milestoneService;
    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    FullPrunedBlockGraph blockgraph;
    @Autowired
    private KafkaConfiguration kafkaConfiguration;

    private static final Logger logger = LoggerFactory.getLogger(BlockService.class);

    public Block getBlock(Sha256Hash blockhash) throws BlockStoreException {
        return store.get(blockhash).getHeader();
    }

    public List<Block> getBlocks(List<Sha256Hash> hashes) throws BlockStoreException {
        List<Block> blocks = new ArrayList<Block>();
        for (Sha256Hash hash : hashes) {
            blocks.add(getBlock(hash));
        }
        return blocks;
    }

    @Cacheable(cacheNames = "BlockEvaluations")
    public BlockEvaluation getBlockEvaluation(Sha256Hash hash) throws BlockStoreException {
        return store.getBlockEvaluation(hash);
    }

    public List<BlockEvaluation> getBlockEvaluations(Collection<Sha256Hash> hashes) throws BlockStoreException {
        List<BlockEvaluation> blocks = new ArrayList<BlockEvaluation>();
        for (Sha256Hash hash : hashes) {
            blocks.add(getBlockEvaluation(hash));
        }
        return blocks;
    }

    public List<BlockWrap> getSolidApproverBlocks(Sha256Hash blockhash) throws BlockStoreException {
        return store.getSolidApproverBlocks(blockhash);
    }

    public List<Sha256Hash> getSolidApproverBlockHashes(Sha256Hash blockhash) throws BlockStoreException {
        return store.getSolidApproverBlockHashes(blockhash);
    }

    public long getMaxSolidHeight() throws BlockStoreException {
        return store.getMaxSolidHeight();
    }

    public List<Block> getNonSolidBlocks() throws BlockStoreException {
        return store.getNonSolidBlocks();
    }

    public List<BlockEvaluation> getSolidBlocksOfHeight(long currentHeight) throws BlockStoreException {
        return store.getSolidBlocksOfHeight(currentHeight);
    }

    public PriorityQueue<BlockWrap> getSolidTipsDescending() throws BlockStoreException {
        return store.getSolidTipsDescending();
    }

    public List<BlockEvaluation> getAllBlockEvaluations() throws BlockStoreException {
        return store.getAllBlockEvaluations();
    }

    public HashSet<BlockEvaluation> getBlocksToRemoveFromMilestone() throws BlockStoreException {
        return store.getBlocksToRemoveFromMilestone();
    }

    public HashSet<BlockWrap> getBlocksToAddToMilestone() throws BlockStoreException {
        return store.getBlocksToAddToMilestone(0);
    }

    public void updateSolid(BlockEvaluation blockEvaluation, boolean b) throws BlockStoreException {
        blockEvaluation.setSolid(b);
        store.updateBlockEvaluationSolid(blockEvaluation.getBlockHash(), b);
    }

    public void updateWeight(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
        blockEvaluation.setCumulativeWeight(i);
        store.updateBlockEvaluationCumulativeWeight(blockEvaluation.getBlockHash(), i);
    }

    public void updateWeightAndDepth(BlockEvaluation blockEvaluation, long weight, long depth) throws BlockStoreException {
        blockEvaluation.setCumulativeWeight(weight);
        blockEvaluation.setDepth(depth);
        store.updateBlockEvaluationWeightAndDepth(blockEvaluation.getBlockHash(), weight, depth);
    }

    public void updateDepth(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
        blockEvaluation.setDepth(i);
        store.updateBlockEvaluationDepth(blockEvaluation.getBlockHash(), i);
    }

    public void updateMilestoneDepth(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
        blockEvaluation.setMilestoneDepth(i);
        store.updateBlockEvaluationMilestoneDepth(blockEvaluation.getBlockHash(), i);
    }

    public void updateRating(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
        blockEvaluation.setRating(i);
        store.updateBlockEvaluationRating(blockEvaluation.getBlockHash(), i);
    }

    public void saveBinaryArrayToBlock(byte[] bytes) throws Exception {
        Block block = (Block) networkParameters.getDefaultSerializer().makeBlock(bytes);
        saveBlock(block);
    }

    public void saveBlock(Block block) throws Exception {
        boolean added = blockgraph.add(block, false);
        if (added) {
            try {

                broadcastBlock(block.bitcoinSerialize());
                // FIXME remove this later, this is needed for testnet, to get
                // sync real time confirmation for client

                milestoneService.update();
            } catch (Exception e) {
                // TODO: handle exception
                logger.warn(" saveBlock problem after save milestoneService  ", e);
            }
        }
    }

    /*
     * unsolid blocks can be solid, if previous can be found in network etc.
     * read data from table oder by insert time, use add Block to check again,
     * if missing previous, it may request network for the blocks
     * 
     * BOOT_STRAP_SERVERS de.kafka.bigtangle.net:9092
     * 
     * CONSUMERIDSUFFIX 12324
     */
    public void reCheckUnsolidBlock() throws Exception {
        List<Block> blocklist = getNonSolidBlocks();
        for (Block block : blocklist) {
            boolean added = blockgraph.add(block, true);
            if (added) {
                this.store.deleteUnsolid(block.getHash());
                logger.debug("addConnected from reCheckUnsolidBlock " + block);
                continue;
            }
            try {
                StoredBlock storedBlock0 = this.store.get(block.getPrevBlockHash());
                if (storedBlock0 == null) {
                    byte[] re = blockRequester.requestBlock(block.getPrevBlockHash());
                    if (re != null) {
                        blockgraph.add((Block) networkParameters.getDefaultSerializer().makeBlock(re), true);

                    }
                }
                StoredBlock storedBlock1 = this.store.get(block.getPrevBranchBlockHash());
                if (storedBlock1 == null) {
                    byte[] re = blockRequester.requestBlock(block.getPrevBranchBlockHash());
                    if (re != null) {
                        blockgraph.add((Block) networkParameters.getDefaultSerializer().makeBlock(re), true);

                    }
                }
            } catch (Exception e) {
                logger.debug("", e );
            }
        }
    }

    /*
     * all very old unsolid blocks are deleted
     */
    public void deleteOldUnsolidBlock() throws Exception {

        this.store.deleteOldUnsolid(getTimeSeconds(1));
    }

    public long getTimeSeconds(int days) throws Exception {
        return System.currentTimeMillis() / 1000 - days * 60 * 24 * 60 * 60;
    }

    @Autowired
    private BlockRequester blockRequester;

    public int getNextTokenId() throws BlockStoreException {
        int maxTokenId = store.getMaxTokenId();
        return maxTokenId + 1;
    }

    /**
     * Adds the specified block and all approved blocks to the milestone. This
     * will connect all transactions of the block by marking used UTXOs spent
     * and adding new UTXOs to the db.
     * 
     * @param blockEvaluation
     * @throws BlockStoreException
     */
    public void confirm(BlockEvaluation blockEvaluation) throws BlockStoreException {
        blockgraph.addBlockToMilestone(blockEvaluation.getBlockHash());
    }

    /**
     * Removes the specified block and all its output spenders and approvers
     * from the milestone. This will disconnect all transactions of the block by
     * marking used UTXOs unspent and removing UTXOs of the block from the db.
     * 
     * @param blockEvaluation
     * @throws BlockStoreException
     */
    public void unconfirm(BlockEvaluation blockEvaluation) throws BlockStoreException {
        blockgraph.removeBlockFromMilestone(blockEvaluation.getBlockHash());
    }

    /**
     * Recursively removes the specified block and its approvers from the
     * collection if this block is contained in the collection.
     * 
     * @param evaluations
     * @param block
     * @throws BlockStoreException
     */
    public void removeBlockAndApproversFrom(Collection<BlockWrap> evaluations, BlockWrap block)
            throws BlockStoreException {
        if (!evaluations.contains(block))
            return;

        // Remove this block and remove its approvers
        evaluations.remove(block);
        for (Sha256Hash approver : getSolidApproverBlockHashes(block.getBlock().getHash())) {
            removeBlockAndApproversFrom(evaluations, store.getBlockWrap(approver));
        }
    }

    /**
     * Recursively adds the specified block and its approvers to the collection
     * if the blocks are in the current milestone and not in the collection.
     * 
     * @param evaluations
     * @param evaluation
     * @throws BlockStoreException
     */
    public void addMilestoneApproversTo(Collection<BlockWrap> evaluations, BlockWrap evaluation)
            throws BlockStoreException {
        if (!evaluation.getBlockEvaluation().isMilestone() || evaluations.contains(evaluation))
            return;

        // Add this block and add all of its milestone approvers
        evaluations.add(evaluation);
        for (Sha256Hash approverHash : getSolidApproverBlockHashes(evaluation.getBlockEvaluation().getBlockHash())) {
            addMilestoneApproversTo(evaluations, store.getBlockWrap(approverHash));
        }
    }

    /**
     * Recursively adds the specified block and its approved blocks to the
     * collection if the blocks are not in the current milestone and not in the
     * collection.
     * 
     * @param evaluations
     * @param milestoneEvaluation
     * @throws BlockStoreException
     */
    public void addApprovedNonMilestoneBlocksTo(Collection<BlockWrap> evaluations, BlockWrap block)
            throws BlockStoreException {
        addApprovedNonMilestoneBlocksTo(evaluations, block, null);
    }
    
    public void addApprovedNonMilestoneBlocksTo(Collection<BlockWrap> evaluations, BlockWrap block, Collection<BlockWrap> exclusions)
            throws BlockStoreException {
    	if (block == null)
    		return; 
    	
        if (block.getBlockEvaluation().isMilestone() || evaluations.contains(block) || (exclusions != null && exclusions.contains(block)))
            return;

        // Add this block and add all of its approved non-milestone blocks
        evaluations.add(block);

        BlockWrap prevTrunk = store.getBlockWrap(block.getBlock().getPrevBlockHash());
        BlockWrap prevBranch = store.getBlockWrap(block.getBlock().getPrevBranchBlockHash());

        if (prevTrunk != null)
            addApprovedNonMilestoneBlocksTo(evaluations, prevTrunk);
        if (prevBranch != null)
            addApprovedNonMilestoneBlocksTo(evaluations, prevBranch);
    }

    @SuppressWarnings("unchecked")
    public AbstractResponse searchBlock(Map<String, Object> request) throws BlockStoreException {
        List<String> address = (List<String>) request.get("address");
        String lastestAmount = request.get("lastestAmount") == null ? "0" : request.get("lastestAmount").toString();
        List<BlockEvaluation> evaluations = this.store.getSearchBlockEvaluations(address, lastestAmount);
        HashSet<String> hashSet = new HashSet<String>();
        // filter
        for (Iterator<BlockEvaluation> iterator = evaluations.iterator(); iterator.hasNext();) {
            BlockEvaluation blockEvaluation = iterator.next();
            if (hashSet.contains(blockEvaluation.getBlockHexStr())) {
                iterator.remove();
            } else {
                hashSet.add(blockEvaluation.getBlockHexStr());
            }
        }
        return GetBlockEvaluationsResponse.create(evaluations);
    }

    public List<BlockWrap> getRatingEntryPointCandidates() throws BlockStoreException {
        return store.getBlocksInMilestoneDepthInterval((long) 0,
                NetworkParameters.ENTRYPOINT_RATING_UPPER_DEPTH_CUTOFF);
    }

    public List<BlockWrap> getValidationEntryPointCandidates() throws BlockStoreException {
        return store.getBlocksInMilestoneDepthInterval(0, NetworkParameters.ENTRYPOINT_TIPSELECTION_DEPTH_CUTOFF);
    }

    public void broadcastBlock(byte[] data) {
        try {
            if ("".equalsIgnoreCase(kafkaConfiguration.getBootstrapServers()))
                return;
            KafkaMessageProducer kafkaMessageProducer = new KafkaMessageProducer(kafkaConfiguration);
            kafkaMessageProducer.sendMessage(data);
        } catch (InterruptedException | ExecutionException e) {
            Log.warn("", e);
        }
    }

}

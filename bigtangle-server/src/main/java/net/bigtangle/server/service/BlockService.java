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
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Context;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.resp.GetBlockEvaluationsResponse;
import net.bigtangle.kafka.KafkaConfiguration;
import net.bigtangle.kafka.KafkaMessageProducer;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;

/**
 * <p>
 * Provides services for blocks.
 * </p>
 */
@Service
public class BlockService {

    @Autowired
    protected FullPrunedBlockStore store;

    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    FullPrunedBlockGraph blockgraph;
    @Autowired
    private KafkaConfiguration kafkaConfiguration;

    @Autowired
    private ServerConfiguration serverConfiguration;

    private static final Logger logger = LoggerFactory.getLogger(BlockService.class);

    @Cacheable("blocks")
    public Block getBlock(Sha256Hash blockhash) throws BlockStoreException, NoBlockException {
        return store.get(blockhash);
    }

    public BlockWrap getBlockWrap(Sha256Hash blockhash) throws BlockStoreException {
        return store.getBlockWrap(blockhash);
    }

    public List<Block> getBlocks(List<Sha256Hash> hashes) throws BlockStoreException, NoBlockException {
        List<Block> blocks = new ArrayList<Block>();
        for (Sha256Hash hash : hashes) {
            blocks.add(getBlock(hash));
        }
        return blocks;
    }

    public List<BlockWrap> getBlockWraps(List<Sha256Hash> hashes) throws BlockStoreException {
        List<BlockWrap> blocks = new ArrayList<BlockWrap>();
        for (Sha256Hash hash : hashes) {
            blocks.add(getBlockWrap(hash));
        }
        return blocks;
    }

    public BlockEvaluation getBlockEvaluation(Sha256Hash hash) throws BlockStoreException {
        return store.getBlockEvaluation(hash);
    }

    public List<BlockEvaluation> getAllBlockEvaluations() throws BlockStoreException {
        return store.getAllBlockEvaluations();
    }

    public void saveBlock(Block block) throws Exception {
        Context context = new Context(networkParameters);
        Context.propagate(context);
        boolean added = blockgraph.add(block, false);
        if (added  ) {
                broadcastBlock(block);
        }
    }

    public long getTimeSeconds(int days) throws Exception {
        return System.currentTimeMillis() / 1000 - days * 60 * 24 * 60;
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
        for (Sha256Hash approver : store.getSolidApproverBlockHashes(block.getBlock().getHash())) {
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
        for (Sha256Hash approverHash : store
                .getSolidApproverBlockHashes(evaluation.getBlockEvaluation().getBlockHash())) {
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
    public void addApprovedNonMilestoneBlocksTo(Collection<BlockWrap> evaluations, BlockWrap block) {
        if (block == null)
            return;

        if (block.getBlockEvaluation().isMilestone() || evaluations.contains(block))
            return;

        // Add this block and add all of its approved non-milestone blocks
        evaluations.add(block);

        BlockWrap prevTrunk, prevBranch;
        try {
            prevTrunk = store.getBlockWrap(block.getBlock().getPrevBlockHash());
            prevBranch = store.getBlockWrap(block.getBlock().getPrevBranchBlockHash());
        } catch (BlockStoreException e) {
            // Cannot happen
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        if (prevTrunk != null)
            addApprovedNonMilestoneBlocksTo(evaluations, prevTrunk);
        if (prevBranch != null)
            addApprovedNonMilestoneBlocksTo(evaluations, prevBranch);
    }

    @SuppressWarnings("unchecked")
    public AbstractResponse searchBlock(Map<String, Object> request) throws BlockStoreException {
        List<String> address = (List<String>) request.get("address");
        String lastestAmount = request.get("lastestAmount") == null ? "0" : request.get("lastestAmount").toString();
        List<BlockEvaluationDisplay> evaluations = this.store.getSearchBlockEvaluations(address, lastestAmount);
        HashSet<String> hashSet = new HashSet<String>();
        // filter
        for (Iterator<BlockEvaluationDisplay> iterator = evaluations.iterator(); iterator.hasNext();) {
            BlockEvaluation blockEvaluation = iterator.next();
            if (hashSet.contains(blockEvaluation.getBlockHexStr())) {
                iterator.remove();
            } else {
                hashSet.add(blockEvaluation.getBlockHexStr());
            }
        }
        return GetBlockEvaluationsResponse.create(evaluations);
    }

    public AbstractResponse searchBlockByBlockHash(Map<String, Object> request) throws BlockStoreException {
        String blockhash = request.get("blockhash") == null ? "" : request.get("blockhash").toString();
        String lastestAmount = request.get("lastestAmount") == null ? "0" : request.get("lastestAmount").toString();
        List<BlockEvaluationDisplay> evaluations = this.store.getSearchBlockEvaluations(blockhash, lastestAmount);
        HashSet<String> hashSet = new HashSet<String>();
        // filter
        for (Iterator<BlockEvaluationDisplay> iterator = evaluations.iterator(); iterator.hasNext();) {
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

    public void broadcastBlock(Block block) {
        try {
            if ("".equalsIgnoreCase(kafkaConfiguration.getBootstrapServers()))
                return;
            KafkaMessageProducer kafkaMessageProducer = new KafkaMessageProducer(kafkaConfiguration);

            kafkaMessageProducer.sendMessage(block.bitcoinSerialize(),
                    serverConfiguration.getMineraddress());
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("", e);
        }
    }

    public void batchBlock(Block block) throws BlockStoreException {

        this.store.insertBatchBlock(block);
    }

    public void insertMyserverblocks(Sha256Hash prevhash, Sha256Hash hash, Long inserttime) throws BlockStoreException {

        this.store.insertMyserverblocks(prevhash, hash, inserttime);
    }

    public boolean existMyserverblocks(Sha256Hash prevhash) throws BlockStoreException {

        return this.store.existMyserverblocks(prevhash);
    }

    public void deleteMyserverblocks(Sha256Hash prevhash) throws BlockStoreException {

        this.store.deleteMyserverblocks(prevhash);
    }
    
    public List<byte[]> blocksFromHeight(Long heightstart, Long heightend) throws BlockStoreException {

        return store.blocksFromHeight(heightstart);
    }
}

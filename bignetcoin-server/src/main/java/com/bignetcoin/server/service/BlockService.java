/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.BlockStoreException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.store.FullPrunedBlockGraph;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    protected NetworkParameters networkParameters;
    @Autowired
    BlockGraphService blockGraphService;

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

    public BlockEvaluation getBlockEvaluation(Sha256Hash hash) throws BlockStoreException {
        return store.getBlockEvaluation(hash);
    }

    public List<BlockEvaluation> getBlockEvaluations(List<Sha256Hash> hashes) throws BlockStoreException {
        List<BlockEvaluation> blocks = new ArrayList<BlockEvaluation>();
        for (Sha256Hash hash : hashes) {
            blocks.add(getBlockEvaluation(hash));
        }
        return blocks;
    }

    public List<StoredBlock> getSolidApproverBlocks(Sha256Hash blockhash) throws BlockStoreException {
        return store.getSolidApproverBlocks(blockhash);
    }

    public List<Sha256Hash> getSolidApproverBlockHashes(Sha256Hash blockhash) throws BlockStoreException {
        return store.getSolidApproverBlockHashes(blockhash);
    }

    public long getMaxSolidHeight() throws BlockStoreException {
        return store.getMaxSolidHeight();
    }

    public List<Sha256Hash> getNonSolidBlocks() throws BlockStoreException {
        return store.getNonSolidBlocks();
    }

    public List<BlockEvaluation> getSolidBlocksOfHeight(long currentHeight) throws BlockStoreException {
        return store.getSolidBlocksOfHeight(currentHeight);
    }

    public List<BlockEvaluation> getSolidTips() throws BlockStoreException {
        return store.getSolidTips();
    }

    public List<BlockEvaluation> getAllBlockEvaluations() throws BlockStoreException {
        return store.getAllBlockEvaluations();
    }

    public HashSet<BlockEvaluation> getBlocksToRemoveFromMilestone() throws BlockStoreException {
        return store.getBlocksToRemoveFromMilestone();
    }

    public HashSet<BlockEvaluation> getBlocksToAddToMilestone() throws BlockStoreException {
        return store.getBlocksToAddToMilestone(0);
    }

    public void updateSolidBlocks(Set<Sha256Hash> analyzedHashes) throws BlockStoreException {
        // unnecessary
    }

    public void updateSolid(BlockEvaluation blockEvaluation, boolean b) throws BlockStoreException {
        blockEvaluation.setSolid(b);
        store.updateBlockEvaluationSolid(blockEvaluation.getBlockhash(), b);
    }

    public void updateHeight(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
        blockEvaluation.setHeight(i);
        store.updateBlockEvaluationHeight(blockEvaluation.getBlockhash(), i);
    }

    public void updateCumulativeWeight(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
        blockEvaluation.setCumulativeWeight(i);
        store.updateBlockEvaluationCumulativeweight(blockEvaluation.getBlockhash(), i);
    }

    public void updateDepth(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
        blockEvaluation.setDepth(i);
        store.updateBlockEvaluationDepth(blockEvaluation.getBlockhash(), i);
    }

    public void updateRating(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
        blockEvaluation.setRating(i);
        store.updateBlockEvaluationRating(blockEvaluation.getBlockhash(), i);
    }

    public void updateMilestone(BlockEvaluation blockEvaluation, boolean b) throws BlockStoreException {
        blockEvaluation.setMilestone(b);
        store.updateBlockEvaluationMilestone(blockEvaluation.getBlockhash(), b);

        long now = System.currentTimeMillis();
        blockEvaluation.setMilestoneLastUpdateTime(now);
        store.updateBlockEvaluationMilestoneLastUpdateTime(blockEvaluation.getBlockhash(), now);
    }

    public void saveBinaryArrayToBlock(byte[] bytes) throws Exception {
        Block block = (Block) networkParameters.getDefaultSerializer().makeBlock(bytes);
        FullPrunedBlockGraph blockgraph = new FullPrunedBlockGraph(networkParameters, store);
        blockgraph.add(block);
        milestoneService.update();
    }

    public int getNextTokenId() throws BlockStoreException {
        int maxTokenId = store.getMaxTokenId();
        return maxTokenId + 1;
    }

    @Autowired
    private MilestoneService milestoneService;

    /**
     * Adds the specified block and all approved blocks to the milestone. This
     * will connect all transactions of the block by marking used UTXOs spent
     * and adding new UTXOs to the db.
     * 
     * @param blockEvaluation
     * @throws BlockStoreException
     */
    public void connect(BlockEvaluation blockEvaluation) throws BlockStoreException {
        // TODO validate ?some static validity? here and repropagate
        blockGraphService.addBlockToMilestone(blockEvaluation);
    }

    /**
     * Removes the specified block and all its output spenders and approvers
     * from the milestone. This will disconnect all transactions of the block by
     * marking used UTXOs unspent and removing UTXOs of the block from the db.
     * 
     * @param blockEvaluation
     * @throws BlockStoreException
     */
    public void disconnect(BlockEvaluation blockEvaluation) throws BlockStoreException {
        blockGraphService.removeBlockFromMilestone(blockEvaluation);
    }

    public List<BlockEvaluation> getSolidBlockEvaluations() throws BlockStoreException {
        return store.getSolidBlockEvaluations();
    }
}

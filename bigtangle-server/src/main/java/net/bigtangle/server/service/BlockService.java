/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.Utils;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.GenesisBlockLRResponse;
import net.bigtangle.server.response.GetBlockEvaluationsResponse;
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
    protected NetworkParameters networkParameters;
    @Autowired
    FullPrunedBlockGraph blockgraph;

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

    public void updateSolid(BlockEvaluation blockEvaluation, boolean b) throws BlockStoreException {
        blockEvaluation.setSolid(b);
        store.updateBlockEvaluationSolid(blockEvaluation.getBlockhash(), b);
    }

 
    public void updateCumulativeWeight(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
        blockEvaluation.setCumulativeWeight(i);
        store.updateBlockEvaluationCumulativeweight(blockEvaluation.getBlockhash(), i);
    }

    public void updateDepth(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
        blockEvaluation.setDepth(i);
        store.updateBlockEvaluationDepth(blockEvaluation.getBlockhash(), i);
    }

    public void updateMilestoneDepth(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
        blockEvaluation.setMilestoneDepth(i);
        store.updateBlockEvaluationMilestoneDepth(blockEvaluation.getBlockhash(), i);
    }

    public void updateRating(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
        blockEvaluation.setRating(i);
        store.updateBlockEvaluationRating(blockEvaluation.getBlockhash(), i);
    }

    public void saveBinaryArrayToBlock(byte[] bytes) throws Exception {
        Block block = (Block) networkParameters.getDefaultSerializer().makeBlock(bytes);
        saveBlock(block);
    }

    public void saveBlock(Block block) throws Exception {
        blockgraph.add(block);
        try {
            milestoneService.update();
      
        } catch (Exception e) {
            // TODO: handle exception
            logger.warn(" saveBlock problem after save milestoneService  ", e);
        }

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
    public void confirm(BlockEvaluation blockEvaluation) throws BlockStoreException {
        blockgraph.addBlockToMilestone(blockEvaluation);
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
        blockgraph.removeBlockFromMilestone(blockEvaluation);
    }

    public List<BlockEvaluation> getSolidBlockEvaluations() throws BlockStoreException {
        return store.getSolidBlockEvaluations();
    }

    /**
     * Returns all solid tips ordered by descending height
     * 
     * @return solid tips by ordered by descending height
     * @throws BlockStoreException
     */
    public TreeSet<BlockEvaluation> getSolidTipsDescending() throws BlockStoreException {
        List<BlockEvaluation> solidTips = getSolidTips();
        TreeSet<BlockEvaluation> blocksByDescendingHeight = new TreeSet<BlockEvaluation>(
                Comparator.comparingLong(BlockEvaluation::getHeight).reversed());
        blocksByDescendingHeight.addAll(solidTips);
        return blocksByDescendingHeight;
    }

    /**
     * Recursively removes the specified block and its approvers from the
     * collection if this block is contained in the collection.
     * 
     * @param evaluations
     * @param blockEvaluation
     * @throws BlockStoreException
     */
    public void removeBlockAndApproversFrom(Collection<BlockEvaluation> evaluations, BlockEvaluation blockEvaluation)
            throws BlockStoreException {
        if (!evaluations.contains(blockEvaluation))
            return;

        // Remove this block and remove its approvers
        evaluations.remove(blockEvaluation);
        for (Sha256Hash approver : getSolidApproverBlockHashes(blockEvaluation.getBlockhash())) {
            removeBlockAndApproversFrom(evaluations, getBlockEvaluation(approver));
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
    public void addMilestoneApproversTo(Collection<BlockEvaluation> evaluations, BlockEvaluation evaluation)
            throws BlockStoreException {
        if (!evaluation.isMilestone() || evaluations.contains(evaluation))
            return;

        // Add this block and add all of its milestone approvers
        evaluations.add(evaluation);
        for (Sha256Hash approverHash : getSolidApproverBlockHashes(evaluation.getBlockhash())) {
            addMilestoneApproversTo(evaluations, getBlockEvaluation(approverHash));
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
    public void addApprovedNonMilestoneBlocksTo(Collection<BlockEvaluation> evaluations, BlockEvaluation evaluation)
            throws BlockStoreException {
        if (evaluation.isMilestone() || evaluations.contains(evaluation))
            return;

        // Add this block and add all of its approved non-milestone blocks
        evaluations.add(evaluation);

        Block block = getBlock(evaluation.getBlockhash());
        BlockEvaluation prevBlockEvaluation = getBlockEvaluation(block.getPrevBlockHash());
        BlockEvaluation prevBranchBlockEvaluation = getBlockEvaluation(block.getPrevBranchBlockHash());

        if (prevBlockEvaluation != null)
            addApprovedNonMilestoneBlocksTo(evaluations, prevBlockEvaluation);
        if (prevBranchBlockEvaluation != null)
            addApprovedNonMilestoneBlocksTo(evaluations, prevBranchBlockEvaluation);
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

    public List<BlockEvaluation> getRatingEntryPointCandidates() throws BlockStoreException {
        return store.getBlocksInMilestoneDepthInterval(NetworkParameters.ENTRYPOINT_RATING_LOWER_DEPTH_CUTOFF,
                NetworkParameters.ENTRYPOINT_RATING_UPPER_DEPTH_CUTOFF);
    }

    public List<BlockEvaluation> getValidationEntryPointCandidates() throws BlockStoreException {
        return store.getBlocksInMilestoneDepthInterval(0, NetworkParameters.ENTRYPOINT_TIPSELECTION_DEPTH_CUTOFF);
    }
    
    @Autowired
    private TipsService tipService;

    public AbstractResponse getGenesisBlockLR() throws Exception {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair();
        Block r1 = getBlock(tipsToApprove.getLeft());
        Block r2 = getBlock(tipsToApprove.getRight());
        return GenesisBlockLRResponse.create(Utils.HEX.encode(r1.bitcoinSerialize()), Utils.HEX.encode(r2.bitcoinSerialize()));
    }
}

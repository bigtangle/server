/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Context;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderCancelInfo;
import net.bigtangle.core.OrderReclaimInfo;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.ProtocolException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.DifficultyTargetException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.GetBlockEvaluationsResponse;
import net.bigtangle.core.response.GetBlockListResponse;
import net.bigtangle.kafka.KafkaConfiguration;
import net.bigtangle.kafka.KafkaMessageProducer;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.utils.Gzip;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.wallet.CoinSelector;
import net.bigtangle.wallet.DefaultCoinSelector;

/**
 * <p>
 * Provides services for blocks.
 * </p>
 */
@Service
public class BlockService {

    @Autowired
    protected FullPrunedBlockStore store;
    // @Autowired
    // private ValidatorService validatorService;

    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    FullPrunedBlockGraph blockgraph;
    @Autowired
    private KafkaConfiguration kafkaConfiguration;

    @Autowired
    private ServerConfiguration serverConfiguration;

    @Autowired
    protected TipsService tipService;

    private static final Logger logger = LoggerFactory.getLogger(BlockService.class);

    // cache only binary block only
    @Cacheable("blocks")
    // nullable
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
        blockgraph.add(block, false);
        broadcastBlock(block);

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
    public void addConfirmedApproversTo(Collection<BlockWrap> evaluations, BlockWrap evaluation)
            throws BlockStoreException {
        if (!evaluation.getBlockEvaluation().isConfirmed() || evaluations.contains(evaluation))
            return;

        // Add this block and add all of its milestone approvers
        evaluations.add(evaluation);
        for (Sha256Hash approverHash : store
                .getSolidApproverBlockHashes(evaluation.getBlockEvaluation().getBlockHash())) {
            addConfirmedApproversTo(evaluations, store.getBlockWrap(approverHash));
        }
    }

    /**
     * Recursively adds the specified block and its approved blocks to the
     * collection if the blocks are not in the collection. if a block is missing
     * somewhere, returns false.
     * 
     * @param blocks
     * @param milestoneEvaluation
     * @throws BlockStoreException
     */
    public boolean addRequiredNonContainedBlockHashesTo(Collection<Sha256Hash> blocks, BlockWrap block,
            Collection<Sha256Hash> otherBlocks, long cutoffHeight) throws BlockStoreException {
        if (block == null)
            return false;

        if (otherBlocks.contains(block.getBlockHash()) || blocks.contains(block.getBlockHash()))
            return true;

        if (block.getBlock().getHeight() <= cutoffHeight) {
            throw new VerificationException(
                    "Block is cut off at " + getCutoffHeight() + " for block: " + block.getBlock().toString());
        }

        // Add this block and add all of its required blocks.
        blocks.add(block.getBlockHash());

        Set<Sha256Hash> allRequiredBlockHashes = getAllRequiredBlockHashes(block.getBlock());
        for (Sha256Hash req : allRequiredBlockHashes) {
            BlockWrap pred = store.getBlockWrap(req);
            if (pred == null)
                return false;
            if (!addRequiredNonContainedBlockHashesTo(blocks, pred, otherBlocks, cutoffHeight))
                return false;
        }
        return true;
    }

    /**
     * Recursively adds the specified block and its approved blocks to the
     * collection if the blocks are not in the current milestone and not in the
     * collection. if a block is missing somewhere, returns false.
     * 
     * @param blocks
     * @param cutoffHeight
     * @param milestoneEvaluation
     * @throws BlockStoreException
     */
    public boolean addRequiredUnconfirmedBlocksTo(Collection<BlockWrap> blocks, BlockWrap block, long cutoffHeight)
            throws BlockStoreException {
        if (block == null)
            return false;

        if (block.getBlockEvaluation().isConfirmed() || blocks.contains(block))
            return true;

        // Cutoff
        if (block.getBlockEvaluation().getHeight() <= cutoffHeight){
            throw new VerificationException(
                    "Block is cut off at " + getCutoffHeight() + " for block: " + block.getBlock().toString());
        }
        // Add this block and add all of its required unconfirmed blocks
        blocks.add(block);

        Set<Sha256Hash> allRequiredBlockHashes = getAllRequiredBlockHashes(block.getBlock());
        for (Sha256Hash req : allRequiredBlockHashes) {
            BlockWrap pred = store.getBlockWrap(req);
            if (pred == null)
                return false;
            if (!addRequiredUnconfirmedBlocksTo(blocks, pred, cutoffHeight))
                return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    // TODO cache last blocks, but add evict @Cacheable("searchBlock")
    public AbstractResponse searchBlock(Map<String, Object> request) throws BlockStoreException {
        List<String> address = (List<String>) request.get("address");
        String lastestAmount = request.get("lastestAmount") == null ? "0" : request.get("lastestAmount").toString();
        long height = request.get("height") == null ? 0l : Long.valueOf(request.get("height").toString());
        List<BlockEvaluationDisplay> evaluations = this.store.getSearchBlockEvaluations(address, lastestAmount, height);
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

    // @Cacheable("searchBlockByBlockHash")
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
        return store.getRatingEntryPoints();
    }

    public List<BlockWrap> getValidationEntryPointCandidates() throws BlockStoreException {
        return store.getRatingEntryPoints();
    }

    public void broadcastBlock(Block block) {
        try {
            if ("".equalsIgnoreCase(kafkaConfiguration.getBootstrapServers()))
                return;
            KafkaMessageProducer kafkaMessageProducer = new KafkaMessageProducer(kafkaConfiguration);

            kafkaMessageProducer.sendMessage(block.bitcoinSerialize(), serverConfiguration.getMineraddress());
        } catch (InterruptedException | ExecutionException | IOException e) {
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

    // TODO @Cacheable("blocksFromChainLength")
    public GetBlockListResponse blocksFromChainLength(Long start, Long end) throws BlockStoreException {

        return GetBlockListResponse.create(store.blocksFromChainLength(start, end));
    }

    protected CoinSelector coinSelector = new DefaultCoinSelector();

    public Block askTransactionBlock() throws Exception {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair();
        Block r1 = getBlock(tipsToApprove.getLeft());
        Block r2 = getBlock(tipsToApprove.getRight());

        return r1.createNextBlock(r2,
                Address.fromBase58(networkParameters, serverConfiguration.getMineraddress()).getHash160());
    }

    public boolean getUTXOSpent(TransactionOutPoint txout) throws BlockStoreException {
        return store.getTransactionOutput(txout.getBlockHash(), txout.getTxHash(), txout.getIndex()).isSpent();
    }

    public boolean getUTXOConfirmed(TransactionOutPoint txout) throws BlockStoreException {
        return store.getTransactionOutput(txout.getBlockHash(), txout.getTxHash(), txout.getIndex()).isConfirmed();
    }

    public BlockEvaluation getUTXOSpender(TransactionOutPoint txout) throws BlockStoreException {
        return store.getTransactionOutputSpender(txout.getBlockHash(), txout.getTxHash(), txout.getIndex());
    }

    public UTXO getUTXO(TransactionOutPoint out) throws BlockStoreException {
        return store.getTransactionOutput(out.getBlockHash(), out.getTxHash(), out.getIndex());
    }

    /*
     * Block byte[] bytes
     */
    public Optional<Block> addConnectedFromKafka(byte[] key, byte[] bytes) {

        try {
            // logger.debug(" bytes " +bytes.length);
            return addConnected(Gzip.decompress(bytes), true);
        } catch (DifficultyTargetException e) {

            return null;
        } catch (Exception e) {
            logger.warn("addConnectedFromKafka with sendkey:" + key.toString(), e);
            return null;
        }

    }

    /*
     * Block byte[] bytes
     */
    public Optional<Block> addConnected(byte[] bytes, boolean allowUnsolid)
            throws ProtocolException, BlockStoreException, NoBlockException {
        if (bytes == null)
            return null;

        return addConnectedBlock((Block) networkParameters.getDefaultSerializer().makeBlock(bytes), allowUnsolid);
    }

    public Optional<Block> addConnectedBlock(Block block, boolean allowUnsolid) throws BlockStoreException {
        if (store.getBlockEvaluation(block.getHash()) == null) {

            try {
                if (!blockgraph.add(block, allowUnsolid)) {
                    // if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
                    // blockRequester.requestBlocks(block);
                    // }
                }
                return Optional.of(block);
            } catch (Exception e) {
                logger.debug(" can not added block  Blockhash=" + block.getHashAsString() + " height ="
                        + block.getHeight() + " block: " + block.toString(), e);
                return Optional.empty();

            }
        }
        return Optional.empty();
    }

    public void streamBlocks(Long heightstart, String kafka) throws BlockStoreException {
        KafkaMessageProducer kafkaMessageProducer;
        if (kafka == null || "".equals(kafka)) {
            kafkaMessageProducer = new KafkaMessageProducer(kafkaConfiguration);
        } else {
            kafkaMessageProducer = new KafkaMessageProducer(kafka, kafkaConfiguration.getTopicOutName(), true);
        }
        store.streamBlocks(heightstart, kafkaMessageProducer, serverConfiguration.getMineraddress());
    }

    public void adjustHeightRequiredBlocks(Block block) throws BlockStoreException {
        long h = calcHeightRequiredBlocks(block);
        if (h > block.getHeight()) {
            logger.debug("adjustHeightRequiredBlocks" + block + " to " + h);
            block.setHeight(h);
        }
    }

    public long calcHeightRequiredBlocks(Block block) throws BlockStoreException {
        List<BlockWrap> requires = getAllRequirements(block);
        long height = 0;
        for (BlockWrap b : requires) {
            height = Math.max(height, b.getBlock().getHeight());
        }
        return height + 1;
    }

    public List<BlockWrap> getAllRequirements(Block block) throws BlockStoreException {
        Set<Sha256Hash> allPredecessorBlockHashes = getAllRequiredBlockHashes(block);
        List<BlockWrap> result = new ArrayList<>();
        for (Sha256Hash pred : allPredecessorBlockHashes)
            result.add(store.getBlockWrap(pred));
        return result;
    }

    public long getCutoffHeight() throws BlockStoreException {
        TXReward maxConfirmedReward = store.getMaxConfirmedReward();
        long chainlength = Math.max(0, maxConfirmedReward.getChainLength() - NetworkParameters.MILESTONE_CUTOFF);
        TXReward confirmedAtHeightReward = store.getConfirmedAtHeightReward(chainlength);
        return store.get(confirmedAtHeightReward.getBlockHash()).getHeight();
    }

    public long getCutoffHeight(Sha256Hash prevRewardHash) throws BlockStoreException {
        Set<Sha256Hash> prevMilestoneBlocks = new HashSet<Sha256Hash>();
        Sha256Hash currPrevRewardHash = prevRewardHash;
        for (int i = 0; i < NetworkParameters.MILESTONE_CUTOFF; i++) {
            BlockWrap currRewardBlock = store.getBlockWrap(currPrevRewardHash);
            RewardInfo currRewardInfo;
            try {
                currRewardInfo = RewardInfo.parse(currRewardBlock.getBlock().getTransactions().get(0).getData());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            prevMilestoneBlocks.addAll(currRewardInfo.getBlocks());
            prevMilestoneBlocks.add(currPrevRewardHash);

            if (currPrevRewardHash.equals(networkParameters.getGenesisBlock().getHash()))
                return 0;

            currPrevRewardHash = currRewardInfo.getPrevRewardHash();
        }
        return store.get(currPrevRewardHash).getHeight();
    }

    Set<Sha256Hash> getPastMilestoneBlocks(Sha256Hash prevRewardHash) throws BlockStoreException {
        Set<Sha256Hash> prevMilestoneBlocks = new HashSet<Sha256Hash>();
        Sha256Hash currPrevRewardHash = prevRewardHash;
        for (int i = 0; i < NetworkParameters.MILESTONE_CUTOFF; i++) {
            BlockWrap currRewardBlock = store.getBlockWrap(currPrevRewardHash);
            RewardInfo currRewardInfo;
            try {
                currRewardInfo = RewardInfo.parse(currRewardBlock.getBlock().getTransactions().get(0).getData());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            prevMilestoneBlocks.addAll(currRewardInfo.getBlocks());
            prevMilestoneBlocks.add(currPrevRewardHash);

            if (currPrevRewardHash.equals(networkParameters.getGenesisBlock().getHash()))
                break;

            currPrevRewardHash = currRewardInfo.getPrevRewardHash();
        }
        return prevMilestoneBlocks;
    }

    /**
     * Returns all blocks that must be confirmed if this block is confirmed.
     * 
     * @param block
     * @return
     */
    public Set<Sha256Hash> getAllRequiredBlockHashes(Block block) {
        Set<Sha256Hash> predecessors = new HashSet<>();
        predecessors.add(block.getPrevBlockHash());
        predecessors.add(block.getPrevBranchBlockHash());

        // All used transaction outputs
        final List<Transaction> transactions = block.getTransactions();
        for (final Transaction tx : transactions) {
            if (!tx.isCoinBase()) {
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    TransactionInput in = tx.getInputs().get(index);
                    // due to virtual txs from order/reward
                    predecessors.add(in.getOutpoint().getBlockHash());
                }
            }
        }

        switch (block.getBlockType()) {
        case BLOCKTYPE_CROSSTANGLE:
            break;
        case BLOCKTYPE_FILE:
            break;
        case BLOCKTYPE_GOVERNANCE:
            break;
        case BLOCKTYPE_INITIAL:
            break;
        case BLOCKTYPE_REWARD:
            RewardInfo rewardInfo = null;
            try {
                rewardInfo = RewardInfo.parse(transactions.get(0).getData());
            } catch (IOException e) {
                logger.error("Block was not checked: " + e.getLocalizedMessage());
            }
            predecessors.add(rewardInfo.getPrevRewardHash());
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            TokenInfo currentToken = null;
            try {
                currentToken = TokenInfo.parse(transactions.get(0).getData());
            } catch (IOException e) {
                logger.error("Block was not checked: " + e.getLocalizedMessage());
            }
            predecessors.add(Sha256Hash.wrap(currentToken.getToken().getDomainNameBlockHash()));
            if (currentToken.getToken().getPrevblockhash() != null)
                predecessors.add(currentToken.getToken().getPrevblockhash());
            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_VOS:
            break;
        case BLOCKTYPE_VOS_EXECUTE:
            break;
        case BLOCKTYPE_ORDER_OPEN:
            break;
        case BLOCKTYPE_ORDER_CANCEL:
            OrderCancelInfo opInfo = null;
            try {
                opInfo = OrderCancelInfo.parse(transactions.get(0).getData());
            } catch (IOException e) {
                logger.error("Block was not checked: " + e.getLocalizedMessage());
            }
            predecessors.add(opInfo.getBlockHash());
            break;
        case BLOCKTYPE_ORDER_RECLAIM:
            OrderReclaimInfo reclaimInfo = null;
            try {
                reclaimInfo = OrderReclaimInfo.parse(transactions.get(0).getData());
            } catch (IOException e) {
                logger.error("Block was not checked: " + e.getLocalizedMessage());
            }
            predecessors.add(reclaimInfo.getOrderBlockHash());
            predecessors.add(reclaimInfo.getNonConfirmingMatcherBlockHash());
            break;
        default:
            throw new RuntimeException("No Implementation");
        }

        return predecessors;
    }
}

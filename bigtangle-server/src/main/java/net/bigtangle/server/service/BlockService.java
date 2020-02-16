/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

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
import net.bigtangle.core.exception.VerificationException.CutoffException;
import net.bigtangle.core.exception.VerificationException.ProofOfWorkException;
import net.bigtangle.core.exception.VerificationException.UnsolidException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.GetBlockEvaluationsResponse;
import net.bigtangle.core.response.GetBlockListResponse;
import net.bigtangle.kafka.KafkaConfiguration;
import net.bigtangle.kafka.KafkaMessageProducer;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.Gzip;
import net.bigtangle.utils.Threading;
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
    private static final int PROTOTYPE_CACHE_SIZE = 10;

    private List<Block> prototypeCache = new ArrayList<>();
    private long lastPrototypeCacheUpdate = 0;

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
    public void removeBlockAndApproversFrom(Collection<BlockWrap> blocks, BlockWrap startingBlock)
            throws BlockStoreException {

        PriorityQueue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()));
        Set<Sha256Hash> blockQueueSet = new HashSet<>();
        blockQueue.add(startingBlock);
        blockQueueSet.add(startingBlock.getBlockHash());

        while (!blockQueue.isEmpty()) {
            BlockWrap block = blockQueue.poll();
            blockQueueSet.remove(block.getBlockHash());

            // Nothing to remove further if not in set
            if (!blocks.contains(block))
                continue;

            // Remove this block.
            blocks.remove(block);

            // Queue all of its approver blocks if not already queued.
            for (Sha256Hash req : store.getSolidApproverBlockHashes(block.getBlockHash())) {
                if (!blockQueueSet.contains(req)) {
                    BlockWrap pred = store.getBlockWrap(req);
                    blockQueueSet.add(req);
                    blockQueue.add(pred);
                }
            }
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
    public void addConfirmedApproversTo(Collection<BlockWrap> blocks, BlockWrap startingBlock)
            throws BlockStoreException {

        PriorityQueue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()));
        Set<Sha256Hash> blockQueueSet = new HashSet<>();
        blockQueue.add(startingBlock);
        blockQueueSet.add(startingBlock.getBlockHash());

        while (!blockQueue.isEmpty()) {
            BlockWrap block = blockQueue.poll();
            blockQueueSet.remove(block.getBlockHash());

            // Nothing added if already in set or not confirmed
            if (!block.getBlockEvaluation().isConfirmed() || blocks.contains(block))
                continue;

            // Add this block.
            blocks.add(block);

            // Queue all of its confirmed approver blocks if not already queued.
            for (Sha256Hash req : store.getSolidApproverBlockHashes(block.getBlockHash())) {
                if (!blockQueueSet.contains(req)) {
                    BlockWrap pred = store.getBlockWrap(req);
                    blockQueueSet.add(req);
                    blockQueue.add(pred);
                }
            }
        }
    }

    /**
     * Recursively adds the specified block and its approved blocks to the
     * collection if the blocks are not in the collection. if a block is missing
     * somewhere, returns false. throwException will be true, if it required the
     * validation for consensus. Otherwise, it does ignore the cutoff blocks.
     * 
     * @param blocks
     * @param prevMilestoneNumber
     * @param milestoneEvaluation
     * @param throwException
     * @throws BlockStoreException
     */
    public boolean addRequiredNonContainedBlockHashesTo(Collection<Sha256Hash> blocks, BlockWrap startingBlock,
            long cutoffHeight, long prevMilestoneNumber, boolean throwException) throws BlockStoreException {

        PriorityQueue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());
        Set<Sha256Hash> blockQueueSet = new HashSet<>();
        blockQueue.add(startingBlock);
        blockQueueSet.add(startingBlock.getBlockHash());
        boolean notMissingAnything = true;

        while (!blockQueue.isEmpty()) {
            BlockWrap block = blockQueue.poll();
            blockQueueSet.remove(block.getBlockHash());

            // Nothing added if already in set
            if (blocks.contains(block.getBlockHash()))
                continue;

            // Nothing added if already in milestone
            if (block.getBlockEvaluation().getMilestone() >= 0
                    && block.getBlockEvaluation().getMilestone() <= prevMilestoneNumber)
                continue;

            // Check if the block is in cutoff and not in chain
            if (block.getBlock().getHeight() <= cutoffHeight && block.getBlockEvaluation().getMilestone() < 0) {
                if (throwException) {
                    throw new CutoffException(
                            "Block is cut off at " + cutoffHeight + " for block: " + block.getBlock().toString());
                } else {
                    notMissingAnything = false;
                    continue;
                }
            }

            // Add this block.
            blocks.add(block.getBlockHash());

            // Queue all of its required blocks if not already queued.
            for (Sha256Hash req : getAllRequiredBlockHashes(block.getBlock())) {
                if (!blockQueueSet.contains(req)) {
                    BlockWrap pred = store.getBlockWrap(req);
                    if (pred == null) {
                        notMissingAnything = false;
                        continue;
                    } else {
                        blockQueueSet.add(req);
                        blockQueue.add(pred);
                    }
                }
            }
        }

        return notMissingAnything;
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
    public boolean addRequiredUnconfirmedBlocksTo(Collection<BlockWrap> blocks, BlockWrap startingBlock,
            long cutoffHeight) throws BlockStoreException {

        PriorityQueue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());
        Set<Sha256Hash> blockQueueSet = new HashSet<>();
        blockQueue.add(startingBlock);
        blockQueueSet.add(startingBlock.getBlockHash());
        boolean notMissingAnything = true;

        while (!blockQueue.isEmpty()) {
            BlockWrap block = blockQueue.poll();
            blockQueueSet.remove(block.getBlockHash());

            // Nothing added if already in set or confirmed
            if (block.getBlockEvaluation().getMilestone() >= 0 || block.getBlockEvaluation().isConfirmed()
                    || blocks.contains(block))
                continue;

            // Check if the block is in cutoff and not in chain
            if (block.getBlock().getHeight() <= cutoffHeight && block.getBlockEvaluation().getMilestone() < 0) {
                throw new CutoffException(
                        "Block is cut off at " + cutoffHeight + " for block: " + block.getBlock().toString());
            }

            // Add this block.
            blocks.add(block);

            // Queue all of its required blocks if not already queued.
            for (Sha256Hash req : getAllRequiredBlockHashes(block.getBlock())) {
                if (!blockQueueSet.contains(req)) {
                    BlockWrap pred = store.getBlockWrap(req);
                    if (pred == null) {
                        notMissingAnything = false;
                        continue;
                    } else {
                        blockQueueSet.add(req);
                        blockQueue.add(pred);
                    }
                }
            }
        }

        return notMissingAnything;
    }

    public AbstractResponse searchBlock(Map<String, Object> request) throws BlockStoreException {
        @SuppressWarnings("unchecked")
        List<String> address = (List<String>) request.get("address");
        String lastestAmount = request.get("lastestAmount") == null ? "0" : request.get("lastestAmount").toString();
        long height = request.get("height") == null ? 0l : Long.valueOf(request.get("height").toString());
        List<BlockEvaluationDisplay> evaluations = this.store.getSearchBlockEvaluations(address, lastestAmount, height,
                serverConfiguration.getMaxserachblocks());
        return GetBlockEvaluationsResponse.create(evaluations);
    }

    public AbstractResponse searchBlockByBlockHashs(Map<String, Object> request) throws BlockStoreException {
        @SuppressWarnings("unchecked")
        List<String> blockhashs = (List<String>) request.get("blockhashs");
        List<BlockEvaluationDisplay> evaluations = this.store.getSearchBlockEvaluationsByhashs(blockhashs);

        return GetBlockEvaluationsResponse.create(evaluations);
    }

    public List<BlockWrap> getEntryPointCandidates() throws BlockStoreException {
        return store.getEntryPoints();
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

    public GetBlockListResponse blocksFromChainLength(Long start, Long end) throws BlockStoreException {

        return GetBlockListResponse.create(store.blocksFromChainLength(start, end));
    }

    protected CoinSelector coinSelector = new DefaultCoinSelector();

    protected final ReentrantLock lock = Threading.lock("blockService");

    protected final Random random = new Random();

    public Block getBlockPrototype() throws BlockStoreException, NoBlockException {
        return getNewBlockPrototype();
    }

    public Block getBlockPrototypeWithCache() throws Exception {
        lock.lock();
        try {
            if (lastPrototypeCacheUpdate < System.currentTimeMillis() - 1000) {
                prototypeCache.clear();
                for (int i = 0; i < PROTOTYPE_CACHE_SIZE; i++) {
                    Block newTip = getNewBlockPrototype();
                    prototypeCache.add(newTip);
                }
                lastPrototypeCacheUpdate = System.currentTimeMillis();
            }
            return prototypeCache.get(random.nextInt(prototypeCache.size()));
        } catch (Exception e) {
            logger.error("getTip ", e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    private Block getNewBlockPrototype() throws BlockStoreException, NoBlockException {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair();
        Block r1 = getBlock(tipsToApprove.getLeft());
        Block r2 = getBlock(tipsToApprove.getRight());

        Block b = Block.createBlock(networkParameters, r1, r2);

        b.setMinerAddress(Address.fromBase58(networkParameters, serverConfiguration.getMineraddress()).getHash160());

        return b;
    }

    public boolean getUTXOSpent(TransactionOutPoint txout) throws BlockStoreException {
        return store.getTransactionOutput(txout.getBlockHash(), txout.getTxHash(), txout.getIndex()).isSpent();
    }

    public boolean getUTXOConfirmed(TransactionOutPoint txout) throws BlockStoreException {
        return store.getOutputConfirmation(txout.getBlockHash(), txout.getTxHash(), txout.getIndex());
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
        } catch (VerificationException e) {
            return null;
        } catch (Exception e) {
            logger.debug("addConnectedFromKafka with sendkey:" + key.toString(), e);
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
            } catch (ProofOfWorkException | UnsolidException e) {
                return Optional.empty();
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

    public void adjustHeightRequiredBlocks(Block block) throws BlockStoreException, NoBlockException {
        adjustPrototyp(block);
        long h = calcHeightRequiredBlocks(block);
        if (h > block.getHeight()) {
            logger.debug("adjustHeightRequiredBlocks" + block + " to " + h);
            block.setHeight(h);
        }
    }

    public void adjustPrototyp(Block block) throws BlockStoreException, NoBlockException {
        // two hours for just getBlockPrototype
        int delaySeconds = 7200;

        if (block.getTimeSeconds() < System.currentTimeMillis() / 1000 - delaySeconds) {
            logger.debug("adjustPrototyp " + block);
            Block newblock = getBlockPrototype();
            for (Transaction transaction : block.getTransactions()) {
                newblock.addTransaction(transaction);
            }
            block = newblock;
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

    public long getCurrentMaxHeight() throws BlockStoreException {
        TXReward maxConfirmedReward = store.getMaxConfirmedReward();
        return store.get(maxConfirmedReward.getBlockHash()).getHeight() + NetworkParameters.FORWARD_BLOCK_HORIZON;
    }

    public long getCurrentCutoffHeight() throws BlockStoreException {
        TXReward maxConfirmedReward = store.getMaxConfirmedReward();
        long chainlength = Math.max(0, maxConfirmedReward.getChainLength() - NetworkParameters.MILESTONE_CUTOFF);
        TXReward confirmedAtHeightReward = store.getRewardConfirmedAtHeight(chainlength);
        return store.get(confirmedAtHeightReward.getBlockHash()).getHeight();
    }

    public long getRewardMaxHeight(Sha256Hash prevRewardHash) throws BlockStoreException {
        return Long.MAX_VALUE;
        // Block rewardBlock = store.get(prevRewardHash);
        // return rewardBlock.getHeight() +
        // NetworkParameters.FORWARD_BLOCK_HORIZON;
    }

    public long getRewardCutoffHeight(Sha256Hash prevRewardHash) throws BlockStoreException {
        Set<Sha256Hash> prevMilestoneBlocks = new HashSet<Sha256Hash>();
        Sha256Hash currPrevRewardHash = prevRewardHash;
        for (int i = 0; i < NetworkParameters.MILESTONE_CUTOFF; i++) {
            BlockWrap currRewardBlock = store.getBlockWrap(currPrevRewardHash);
            RewardInfo currRewardInfo = new RewardInfo()
                    .parseChecked(currRewardBlock.getBlock().getTransactions().get(0).getData());

            prevMilestoneBlocks.addAll(currRewardInfo.getBlocks());
            prevMilestoneBlocks.add(currPrevRewardHash);

            if (currPrevRewardHash.equals(networkParameters.getGenesisBlock().getHash()))
                return 0;

            currPrevRewardHash = currRewardInfo.getPrevRewardHash();
        }
        return store.get(currPrevRewardHash).getHeight();
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
            RewardInfo rewardInfo = new RewardInfo().parseChecked(transactions.get(0).getData());

            predecessors.add(rewardInfo.getPrevRewardHash());
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            TokenInfo currentToken = new TokenInfo().parseChecked(transactions.get(0).getData());
            predecessors.add(Sha256Hash.wrap(currentToken.getToken().getDomainNameBlockHash()));
            if (currentToken.getToken().getPrevblockhash() != null)
                predecessors.add(currentToken.getToken().getPrevblockhash());
            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_CONTRACT_EVENT:
            break;
        case BLOCKTYPE_CONTRACT_EXECUTE:
            break;
        case BLOCKTYPE_ORDER_OPEN:
            break;
        case BLOCKTYPE_ORDER_CANCEL:
            OrderCancelInfo opInfo = new OrderCancelInfo().parseChecked(transactions.get(0).getData());
            predecessors.add(opInfo.getBlockHash());
            break;
        default:
            throw new RuntimeException("No Implementation");
        }

        return predecessors;
    }

    /*
     * failed blocks without conflict for retry
     */
    public AbstractResponse findRetryBlocks(Map<String, Object> request) throws BlockStoreException {
        @SuppressWarnings("unchecked")
        List<String> address = (List<String>) request.get("address");
        String lastestAmount = request.get("lastestAmount") == null ? "0" : request.get("lastestAmount").toString();
        long height = request.get("height") == null ? 0l : Long.valueOf(request.get("height").toString());
        List<BlockEvaluationDisplay> evaluations = this.store.getSearchBlockEvaluations(address, lastestAmount, height,
                serverConfiguration.getMaxserachblocks());
        return GetBlockEvaluationsResponse.create(evaluations);
    }

    /*
     * Transactions in a block may has spent output, It is not final that the
     * reject of the block
     * Return false, if there is possible conflict
     */
    public boolean checkPossibleConflict(Block block) throws BlockStoreException {
        // All used transaction outputs
        final List<Transaction> transactions = block.getTransactions();
        for (final Transaction tx : transactions) {
            if (!tx.isCoinBase()) {
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    TransactionInput in = tx.getInputs().get(index);

                    UTXO b = store.getTransactionOutput(in.getOutpoint().getBlockHash(), 
                            in.getOutpoint().getTxHash(), in.getOutpoint().getIndex());
                    if(b!=null && b.isConfirmed() && b.isSpent()) {
                        //there is a confirmed output, conflict is very possible
                        return false;
                    }
                    if(b!=null && !b.isConfirmed()
                            && !checkSpendpending(b)) {
                        //there is a not confirmed output, conflict may be  possible
                        //check the time, if the output is stale
                        return false;
                    }
                }
            }
        }
        return true;
    }
    /*
     * spendpending has timeout for 5 minute return false, if there is
     * spendpending and timeout not
     */
    public boolean checkSpendpending(UTXO output)  {
          int SPENTPENDINGTIMEOUT = 300000;
        if (output.isSpendPending()) {
            return (System.currentTimeMillis() - output.getSpendPendingTime()) > SPENTPENDINGTIMEOUT;
        }
        return true;

    }
}

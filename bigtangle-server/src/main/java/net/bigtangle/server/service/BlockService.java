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
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.BlockMCMC;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderCancelInfo;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.ProtocolException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.ConflictPossibleException;
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
import net.bigtangle.store.FullBlockGraph;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.utils.DomainValidator;
import net.bigtangle.utils.Gzip;

/**
 * <p>
 * Provides services for blocks.
 * </p>
 */
@Service
public class BlockService {

    @Autowired
    private StoreService storeService;

    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    FullBlockGraph blockgraph;
    @Autowired
    private KafkaConfiguration kafkaConfiguration;

    @Autowired
    private ServerConfiguration serverConfiguration;

    @Autowired
    protected TipsService tipService;

    private static final Logger logger = LoggerFactory.getLogger(BlockService.class);

    // cache only binary block only
    @Cacheable(value = "blocksCache", key = "#blockhash")
    public Block getBlock(Sha256Hash blockhash, FullBlockStore store) throws BlockStoreException, NoBlockException {
        // logger.debug("read from databse and no cache for:"+ blockhash);
        return store.get(blockhash);
    }

    public BlockWrap getBlockWrap(Sha256Hash blockhash, FullBlockStore store) throws BlockStoreException {
        return store.getBlockWrap(blockhash);
    }

    public List<Block> getBlocks(List<Sha256Hash> hashes, FullBlockStore store)
            throws BlockStoreException, NoBlockException {
        List<Block> blocks = new ArrayList<Block>();
        for (Sha256Hash hash : hashes) {
            blocks.add(getBlock(hash, store));
        }
        return blocks;
    }

    public List<BlockWrap> getBlockWraps(List<Sha256Hash> hashes, FullBlockStore store) throws BlockStoreException {
        List<BlockWrap> blocks = new ArrayList<BlockWrap>();
        for (Sha256Hash hash : hashes) {
            blocks.add(getBlockWrap(hash, store));
        }
        return blocks;
    }

  
    public     BlockEvaluation getBlockEvaluation(Sha256Hash hash, FullBlockStore store) throws BlockStoreException {
        return store.getBlockWrap(hash).getBlockEvaluation();
    }
    public     BlockMCMC getBlockMCMC(Sha256Hash hash, FullBlockStore store) throws BlockStoreException {
        return store.getBlockWrap(hash).getMcmc();
    }
    public void saveBlock(Block block, FullBlockStore store) throws Exception {
 
        broadcastBlock(block);
        blockgraph.add(block, false, store);
        // removeBlockPrototype(block, store);

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
    public void removeBlockAndApproversFrom(Collection<BlockWrap> blocks, BlockWrap startingBlock, FullBlockStore store)
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
    public void addConfirmedApproversTo(Collection<BlockWrap> blocks, BlockWrap startingBlock, FullBlockStore store)
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
            long cutoffHeight, long prevMilestoneNumber, boolean throwException, FullBlockStore store)
            throws BlockStoreException {

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
            long cutoffHeight, FullBlockStore store) throws BlockStoreException {

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

    public AbstractResponse searchBlock(Map<String, Object> request, FullBlockStore store) throws BlockStoreException {
        @SuppressWarnings("unchecked")
        List<String> address = (List<String>) request.get("address");
        String lastestAmount = request.get("lastestAmount") == null ? "0" : request.get("lastestAmount").toString();
        long height = request.get("height") == null ? 0l : Long.valueOf(request.get("height").toString());
        List<BlockEvaluationDisplay> evaluations = store.getSearchBlockEvaluations(address, lastestAmount, height,
                serverConfiguration.getMaxsearchblocks());
        return GetBlockEvaluationsResponse.create(evaluations);
    }

    public AbstractResponse searchBlockByBlockHashs(Map<String, Object> request, FullBlockStore store)
            throws BlockStoreException {
        @SuppressWarnings("unchecked")
        List<String> blockhashs = (List<String>) request.get("blockhashs");
        List<BlockEvaluationDisplay> evaluations = store.getSearchBlockEvaluationsByhashs(blockhashs);

        return GetBlockEvaluationsResponse.create(evaluations);
    }

    public List<BlockWrap> getEntryPointCandidates(long currChainLength,FullBlockStore store) throws BlockStoreException {
        return store.getEntryPoints(currChainLength);
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

    public void batchBlock(Block block, FullBlockStore store) throws BlockStoreException {

        store.insertBatchBlock(block);
    }

    public void insertMyserverblocks(Sha256Hash prevhash, Sha256Hash hash, Long inserttime, FullBlockStore store)
            throws BlockStoreException {

        store.insertMyserverblocks(prevhash, hash, inserttime);
    }

    public boolean existMyserverblocks(Sha256Hash prevhash, FullBlockStore store) throws BlockStoreException {

        return store.existMyserverblocks(prevhash);
    }

    public void deleteMyserverblocks(Sha256Hash prevhash, FullBlockStore store) throws BlockStoreException {

        store.deleteMyserverblocks(prevhash);
    }

    public GetBlockListResponse blocksFromChainLength(Long start, Long end, FullBlockStore store)
            throws BlockStoreException {

        return GetBlockListResponse.create(store.blocksFromChainLength(start, end));
    }

    public Block getBlockPrototype(FullBlockStore store) throws BlockStoreException, NoBlockException {

        return getNewBlockPrototype(store);
    }
 
 

    private Block getNewBlockPrototype(FullBlockStore store) throws BlockStoreException, NoBlockException {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair(store);
        Block r1 = getBlock(tipsToApprove.getLeft(), store);
        Block r2 = getBlock(tipsToApprove.getRight(), store);
        Block b = Block.createBlock(networkParameters, r1, r2);
        b.setMinerAddress(Address.fromBase58(networkParameters, serverConfiguration.getMineraddress()).getHash160());

        return b;
    }

    public boolean getUTXOSpent(TransactionOutPoint txout, FullBlockStore store) throws BlockStoreException {
        return store.getTransactionOutput(txout.getBlockHash(), txout.getTxHash(), txout.getIndex()).isSpent();
    }

    public boolean getUTXOConfirmed(TransactionOutPoint txout, FullBlockStore store) throws BlockStoreException {
        return store.getOutputConfirmation(txout.getBlockHash(), txout.getTxHash(), txout.getIndex());
    }

    public BlockEvaluation getUTXOSpender(TransactionOutPoint txout, FullBlockStore store) throws BlockStoreException {
        return store.getTransactionOutputSpender(txout.getBlockHash(), txout.getTxHash(), txout.getIndex());
    }

    public UTXO getUTXO(TransactionOutPoint out, FullBlockStore store) throws BlockStoreException {
        return store.getTransactionOutput(out.getBlockHash(), out.getTxHash(), out.getIndex());
    }

    /*
     * Block byte[] bytes
     */
    public Optional<Block> addConnectedFromKafka(byte[] key, byte[] bytes) {

        try {

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
        FullBlockStore store = storeService.getStore();
        try {
            if (!store.existBlock(block.getHash()) ) {
                try {
                    if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
                        logger.debug(" connected  received chain block  " + block.getLastMiningRewardBlock());
                    }
                    blockgraph.add(block, allowUnsolid, store);
                    // removeBlockPrototype(block,store);
                    return Optional.of(block);
                } catch (ProofOfWorkException | UnsolidException e) {
                    return Optional.empty();
                } catch (Exception e) {
                    logger.debug(" can not added block  Blockhash=" + block.getHashAsString() + " height ="
                            + block.getHeight() + " block: " + block.toString(), e);
                    return Optional.empty();

                }
            }
        } finally {
            store.close();
        }

        return Optional.empty();
    }

 

    public void adjustHeightRequiredBlocks(Block block, FullBlockStore store)
            throws BlockStoreException, NoBlockException {
        adjustPrototyp(block, store);
        long h = calcHeightRequiredBlocks(block, store);
        if (h > block.getHeight()) {
            logger.debug("adjustHeightRequiredBlocks" + block + " to " + h);
            block.setHeight(h);
        }
    }

    public void adjustPrototyp(Block block, FullBlockStore store) throws BlockStoreException, NoBlockException {
        // two hours for just getBlockPrototype
        int delaySeconds = 7200;

        if (block.getTimeSeconds() < System.currentTimeMillis() / 1000 - delaySeconds) {
            logger.debug("adjustPrototyp " + block);
            Block newblock = getBlockPrototype(store);
            for (Transaction transaction : block.getTransactions()) {
                newblock.addTransaction(transaction);
            }
            block = newblock;
        }
    }

    public long calcHeightRequiredBlocks(Block block, FullBlockStore store) throws BlockStoreException {
        List<BlockWrap> requires = getAllRequirements(block, store);
        long height = 0;
        for (BlockWrap b : requires) {
            height = Math.max(height, b.getBlock().getHeight());
        }
        return height + 1;
    }

    public List<BlockWrap> getAllRequirements(Block block, FullBlockStore store) throws BlockStoreException {
        Set<Sha256Hash> allPredecessorBlockHashes = getAllRequiredBlockHashes(block);
        List<BlockWrap> result = new ArrayList<>();
        for (Sha256Hash pred : allPredecessorBlockHashes)
            result.add(store.getBlockWrap(pred));
        return result;
    }

    public long getCurrentMaxHeight(TXReward maxConfirmedReward,FullBlockStore store) throws BlockStoreException {
       // TXReward maxConfirmedReward = store.getMaxConfirmedReward();
        if (maxConfirmedReward == null)
            return NetworkParameters.FORWARD_BLOCK_HORIZON;
        return store.get(maxConfirmedReward.getBlockHash()).getHeight() + NetworkParameters.FORWARD_BLOCK_HORIZON;
    }

    public long getCurrentCutoffHeight(TXReward maxConfirmedReward, FullBlockStore store) throws BlockStoreException {
      //  TXReward maxConfirmedReward = store.getMaxConfirmedReward();
        if (maxConfirmedReward == null)
            return 0;
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

    public long getRewardCutoffHeight(Sha256Hash prevRewardHash, FullBlockStore store) throws BlockStoreException {

        Sha256Hash currPrevRewardHash = prevRewardHash;
        for (int i = 0; i < NetworkParameters.MILESTONE_CUTOFF; i++) {
            Block currRewardBlock;
            try {
                currRewardBlock = getBlock(currPrevRewardHash, store); 
                RewardInfo currRewardInfo = new RewardInfo()
                        .parseChecked(currRewardBlock.getTransactions().get(0).getData()); 
                if (currPrevRewardHash.equals(networkParameters.getGenesisBlock().getHash()))
                    return 0;

                currPrevRewardHash = currRewardInfo.getPrevRewardHash();
            } catch (NoBlockException e) {
                // missing prev reward chain, is
                logger.debug("", e);
                throw new BlockStoreException(e);
            }
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
    public AbstractResponse findRetryBlocks(Map<String, Object> request, FullBlockStore store)
            throws BlockStoreException {
        @SuppressWarnings("unchecked")
        List<String> address = (List<String>) request.get("address");
        String lastestAmount = request.get("lastestAmount") == null ? "0" : request.get("lastestAmount").toString();
        long height = request.get("height") == null ? 0l : Long.valueOf(request.get("height").toString());
        List<BlockEvaluationDisplay> evaluations = store.getSearchBlockEvaluations(address, lastestAmount, height,
                serverConfiguration.getMaxsearchblocks());
        return GetBlockEvaluationsResponse.create(evaluations);
    }

    public void checkBlockBeforeSave(Block block, FullBlockStore store) throws BlockStoreException {

        block.verifyHeader();
        if (!checkPossibleConflict(block, store))
            throw new ConflictPossibleException("Conflict Possible");
        checkDomainname(block);
    }

    public void checkDomainname(Block block) throws BlockStoreException {
        switch (block.getBlockType()) {
        case BLOCKTYPE_TOKEN_CREATION:
            TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
            if (TokenType.domainname.ordinal() == currentToken.getToken().getTokentype()) {
                if (!DomainValidator.getInstance().isValid(currentToken.getToken().getTokenname()))
                    throw new VerificationException("Domain name is not valid.");
            }
            break;
        default:
            break;
        }
    }

    /*
     * Transactions in a block may has spent output, It is not final that the
     * reject of the block Return false, if there is possible conflict
     */
    public boolean checkPossibleConflict(Block block, FullBlockStore store) throws BlockStoreException {
        // All used transaction outputs
        final List<Transaction> transactions = block.getTransactions();
        for (final Transaction tx : transactions) {
            if (!tx.isCoinBase()) {
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    TransactionInput in = tx.getInputs().get(index);

                    UTXO b = store.getTransactionOutput(in.getOutpoint().getBlockHash(), in.getOutpoint().getTxHash(),
                            in.getOutpoint().getIndex());
                    if (b != null && b.isConfirmed() && b.isSpent()) {
                        // there is a confirmed output, conflict is very
                        // possible
                        return false;
                    }
                    if (b != null && !b.isConfirmed() && !checkSpendpending(b)) {
                        // there is a not confirmed output, conflict may be
                        // possible
                        // check the time, if the output is stale
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
    public boolean checkSpendpending(UTXO output) {
        int SPENTPENDINGTIMEOUT = 300000;
        if (output.isSpendPending()) {
            return (System.currentTimeMillis() - output.getSpendPendingTime()) > SPENTPENDINGTIMEOUT;
        }
        return true;

    }
}

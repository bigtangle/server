/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Context;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderOpInfo;
import net.bigtangle.core.OrderOpInfo.OrderOp;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderReclaimInfo;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Side;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VOSExecute;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.ScriptException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.GenericInvalidityException;
import net.bigtangle.core.exception.VerificationException.UnsolidException;
import net.bigtangle.script.Script;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.ordermatch.bean.OrderBookEvents;
import net.bigtangle.server.ordermatch.bean.OrderBookEvents.Event;
import net.bigtangle.server.ordermatch.bean.OrderBookEvents.Match;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.OrderTickerService;
import net.bigtangle.server.service.SolidityState;
import net.bigtangle.server.service.SolidityState.State;
import net.bigtangle.server.service.ValidatorService;
import net.bigtangle.server.utils.OrderBook;

/**
 * <p>
 * A FullPrunedBlockChain works in conjunction with a
 * {@link FullPrunedBlockStore} to verify all the rules of the BigTangle system.
 * </p>
 */
@Service
public class FullPrunedBlockGraph extends AbstractBlockGraph {

    private static final Logger log = LoggerFactory.getLogger(FullPrunedBlockGraph.class);

    protected final FullPrunedBlockStore blockStore;

    @Autowired
    public FullPrunedBlockGraph(NetworkParameters networkParameters, FullPrunedBlockStore blockStore)
            throws BlockStoreException {

        super(Context.create(networkParameters), blockStore);
        this.blockStore = blockStore;
        this.networkParameters = networkParameters;
    }

    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    private ValidatorService validatorService;
    @Autowired
    private BlockService blockService;
    @Autowired
    private OrderTickerService tickerService;

    public static class OrderMatchingResult {
        Set<OrderRecord> spentOrders;
        Transaction outputTx;
        Collection<OrderRecord> remainingOrders;
        Map<String, List<Event>> tokenId2Events;

        public OrderMatchingResult(Set<OrderRecord> spentOrders, Transaction outputTx,
                Collection<OrderRecord> remainingOrders, Map<String, List<Event>> tokenId2Events) {
            this.spentOrders = spentOrders;
            this.outputTx = outputTx;
            this.remainingOrders = remainingOrders;
            this.tokenId2Events = tokenId2Events;

        }

        public Set<OrderRecord> getSpentOrders() {
            return spentOrders;
        }

        public Transaction getOutputTx() {
            return outputTx;
        }

        public Collection<OrderRecord> getRemainingOrders() {
            return remainingOrders;
        }

        public Map<String, List<Event>> getTokenId2Events() {
            return tokenId2Events;
        }

        public void setTokenId2Events(Map<String, List<Event>> tokenId2Events) {
            this.tokenId2Events = tokenId2Events;
        }
    }

    private long calculateNextChainDifficulty(Sha256Hash prevHash, long currChainLength, long currentTime) throws BlockStoreException {

        if (currChainLength % NetworkParameters.INTERVAL != 0) {
            return blockStore.getRewardDifficulty(prevHash);
        }
        
        // Get the block INTERVAL ago
        Block oldBlock = null;
        for (int i = 0; i < NetworkParameters.INTERVAL; i++) {
            oldBlock = blockStore.getBlockWrap(prevHash).getBlock();
            prevHash = blockStore.getRewardPrevBlockHash(prevHash);
        }
        
        int timespan = (int) Math.max(1, (currentTime - oldBlock.getTimeSeconds()));
        long prevDifficulty = blockStore.getRewardDifficulty(prevHash);

        // Limit the adjustment step.
        int targetTimespan = NetworkParameters.TARGET_TIMESPAN;
        if (timespan < targetTimespan / 4)
            timespan = targetTimespan / 4;
        if (timespan > targetTimespan * 4)
            timespan = targetTimespan * 4;

        BigInteger newTarget = Utils.decodeCompactBits(prevDifficulty);
        newTarget = newTarget.multiply(BigInteger.valueOf(timespan));
        newTarget = newTarget.divide(BigInteger.valueOf(targetTimespan));

        if (newTarget.compareTo(NetworkParameters.MAX_TARGET) > 0) {
            log.info("Difficulty hit proof of work limit: {}", newTarget.toString(16));
            newTarget = NetworkParameters.MAX_TARGET;
        }

        return Utils.encodeCompactBits(newTarget);
    }

    private void addReward(Block block) throws BlockStoreException {
        try {
            
            RewardInfo rewardInfo = RewardInfo.parse(block.getTransactions().get(0).getData());
            Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
            long toHeight = rewardInfo.getToHeight();
            long currChainLength = blockStore.getRewardChainLength(prevRewardHash) + 1;
            long difficulty = calculateNextChainDifficulty(prevRewardHash, currChainLength, block.getTimeSeconds());

            blockStore.insertReward(block.getHash(), toHeight, prevRewardHash, difficulty, currChainLength);
            
        } catch (IOException e) {
            // Cannot happen when connecting
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void runConsensusLogic(Block newestBlock) throws BlockStoreException {
        try {
            RewardInfo rewardInfo = RewardInfo.parse(newestBlock.getTransactions().get(0).getData());
            Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
            long currChainLength = blockStore.getRewardChainLength(prevRewardHash) + 1;
            
            // Consensus logic>
            Sha256Hash oldLongestChainEnd = blockStore.getMaxConfirmedRewardBlockHash();
            long maxChainLength = blockStore.getRewardChainLength(oldLongestChainEnd);
            if (maxChainLength < currChainLength) {
                
                // Find block to which to rollback (if at all) and all new chain blocks
                List<BlockWrap> newMilestoneBlocks = new ArrayList<>();
                newMilestoneBlocks.add(blockStore.getBlockWrap(newestBlock.getHash()));
                BlockWrap splitPoint = null;
                Sha256Hash prevHash = prevRewardHash;
                for (int i = 0; i < currChainLength; i++) {
                    splitPoint = blockStore.getBlockWrap(prevHash);
                    prevHash = blockStore.getRewardPrevBlockHash(prevHash);
                    
                    if (blockStore.getRewardConfirmed(splitPoint.getBlockHash()))
                        break;

                    newMilestoneBlocks.add(splitPoint);
                }
                Collections.reverse(newMilestoneBlocks);
                
                // Unconfirm anything not confirmed by milestone
                List<Sha256Hash> wipeBlocks = blockStore.getWhereConfirmedNotMilestone();
                HashSet<Sha256Hash> traversedBlockHashes = new HashSet<>();
                for (Sha256Hash wipeBlock : wipeBlocks)
                    unconfirm(wipeBlock, traversedBlockHashes);
                
                // Rollback to split point
                Sha256Hash maxConfirmedRewardBlockHash;
                while (!(maxConfirmedRewardBlockHash = blockStore.getMaxConfirmedRewardBlockHash()).equals(splitPoint.getBlockHash())) {
                    
                    // Unset the milestone of this one (where milestone = maxConfRewardblock.chainLength)
                    long milestoneNumber = blockStore.getRewardChainLength(maxConfirmedRewardBlockHash);
                    blockStore.updateUnsetMilestone(milestoneNumber);
                    
                    // Unconfirm anything not confirmed by milestone
                    wipeBlocks = blockStore.getWhereConfirmedNotMilestone();
                    traversedBlockHashes = new HashSet<>();
                    for (Sha256Hash wipeBlock : wipeBlocks)
                        unconfirm(wipeBlock, traversedBlockHashes);
                }

                // Build milestone forwards. 
                for (BlockWrap newMilestoneBlock : newMilestoneBlocks) {
                    
                    boolean rerunConsensusLogic = newMilestoneBlocks.indexOf(newMilestoneBlock) == newMilestoneBlocks.size() - 1;
                    
                    // Sanity check: if my predecessors are still not fully solid or invalid, there must be something wrong.
                    List<Sha256Hash> allRequiredBlockHashes = validatorService.getAllRequiredBlockHashes(newMilestoneBlock.getBlock());
                    for (Sha256Hash requiredBlockHash : allRequiredBlockHashes) {
                        if (blockStore.getBlockEvaluation(requiredBlockHash).getSolid() != 2) {
                            log.error("Predecessors are not solidified. This should not happen.");
                            throw new RuntimeException("Predecessors are not solidified. This should not happen.");
                        }
                    }

                    // Sanity check: At this point, predecessors cannot be missing
                    SolidityState solidityState = validatorService.checkSolidity(newMilestoneBlock.getBlock(), false);
                    if (!solidityState.isSuccessState() && !solidityState.isFailState()) {
                        log.error("The block is not failing or successful. This should not happen.");
                        throw new RuntimeException("The block is not failing or successful. This should not happen.");
                    }
                    
                    // Check: If all is ok, try confirming this milestone.
                    if (solidityState.isSuccessState()) {
                        
                        // Find conflicts in the dependency set
                        HashSet<BlockWrap> allApprovedNewBlocks = new HashSet<>();
                        blockService.addRequiredUnconfirmedBlocksTo(allApprovedNewBlocks, newMilestoneBlock);
                        
                        // If anything is already spent, no-go
                        boolean anySpentInputs = allApprovedNewBlocks.stream().map(b -> b.toConflictCandidates())
                                .flatMap(i -> i.stream()).anyMatch(c -> {
                                    try {
                                        return validatorService.hasSpentDependencies(c);
                                    } catch (BlockStoreException e) {
                                        e.printStackTrace();
                                        return true;
                                    }
                                });
                        
                        // If any conflicts exist between the current set of blocks, no-go
                        boolean anyCandidateConflicts = allApprovedNewBlocks.stream().map(b -> b.toConflictCandidates())
                                .flatMap(i -> i.stream()).collect(Collectors.groupingBy(i -> i.getConflictPoint())).values().stream()
                                .anyMatch(l -> l.size() > 1);
                        
                        // Did we fail? Then we stop now and rerun consensus logic on the new longest chain.
                        if (anySpentInputs || anyCandidateConflicts) {
                            solidityState = SolidityState.getFailState();
                            
                            // Solidification forward with failState
                            try {
                                blockStore.beginDatabaseBatchWrite();
                                solidifyBlock(newMilestoneBlock.getBlock(), solidityState, false, false);
                                blockStore.commitDatabaseBatchWrite();
                                try {
                                    scanWaitingBlocks(newMilestoneBlock.getBlock(), false);
                                } catch (BlockStoreException | VerificationException e) {
                                    log.debug(e.getLocalizedMessage());
                                }
                            } catch (BlockStoreException e) {
                                blockStore.abortDatabaseBatchWrite();
                                throw e;
                            }
                            
                            runConsensusLogic(blockStore.get(oldLongestChainEnd));
                            return;
                        }
                        
                        // Otherwise, all predecessors exist and were at least solid > 0, 
                        // so we should be able to confirm all of the predecessors.
                        while (!allApprovedNewBlocks.isEmpty()) {
                            @SuppressWarnings("unchecked")
                            HashSet<BlockWrap> nowApprovedBlocks = (HashSet<BlockWrap>) allApprovedNewBlocks.clone();
                            validatorService.removeWhereIneligible(nowApprovedBlocks);
                            validatorService.removeWhereUsedOutputsUnconfirmed(nowApprovedBlocks);
                            // Only here the current milestone block is eligible, so readd it:
                            nowApprovedBlocks.add(newMilestoneBlock);

                            // Confirm the addable blocks and remove them from the list
                            HashSet<Sha256Hash> traversedConfirms = new HashSet<>();
                            for (BlockWrap approvedBlock : nowApprovedBlocks)
                                confirm(approvedBlock.getBlockEvaluation().getBlockHash(), traversedConfirms);
                            
                            allApprovedNewBlocks.removeAll(nowApprovedBlocks);
                        }
                        
                        // Set the milestone on all confirmed non-milestone blocks
                        long milestoneNumber = blockStore.getRewardChainLength(newMilestoneBlock.getBlockHash());
                        blockStore.updateAllConfirmedToMilestone(milestoneNumber);
                    }
                    
                    // Solidification forward
                    try {
                        blockStore.beginDatabaseBatchWrite();
                        solidifyBlock(newMilestoneBlock.getBlock(), solidityState, rerunConsensusLogic, true);
                        blockStore.commitDatabaseBatchWrite();
                        try {
                            scanWaitingBlocks(newMilestoneBlock.getBlock(), rerunConsensusLogic);
                        } catch (BlockStoreException | VerificationException e) {
                            log.debug(e.getLocalizedMessage());
                        }
                    } catch (BlockStoreException e) {
                        blockStore.abortDatabaseBatchWrite();
                        throw e;
                    }
                }
            }
            
        } catch (IOException e) {
            // Cannot happen when connecting
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public boolean add(Block block, boolean allowUnsolid) {
        lock.lock();
        try {
            // If block already exists, no need to add this block to db
            if (blockStore.getBlockEvaluation(block.getHash()) != null) 
                return false;

            // Check the block is partially formally valid and fulfills PoW
            try {
                block.verifyHeader();
                block.verifyTransactions();

            } catch (VerificationException e) {
                log.warn("Failed to verify block: ", e);
                log.warn(block.getHashAsString());
                throw e;
            }
            checkState(lock.isHeldByCurrentThread());

            SolidityState solidityState = validatorService.checkSolidity(block, true);

            // If explicitly wanted (e.g. new block from local clients), this block must strictly be solid now.
            if (!allowUnsolid) {
                switch (solidityState.getState()) {
                case MissingPredecessor:
                    throw new UnsolidException();
                case MissingCalculation:
                case Success:
                    break;
                case Invalid:
                    throw new GenericInvalidityException();
                }
            } 
            
            // Sanity check: This should not happen because it should throw first.
            if (solidityState.isFailState())
                throw new GenericInvalidityException();

            // Accept the block
            try {
//                blockStore.beginDatabaseBatchWrite();
                connect(block, solidityState);
//                blockStore.commitDatabaseBatchWrite();
                try {
                    scanWaitingBlocks(block, true);
                } catch (BlockStoreException | VerificationException e) {
                    log.debug(e.getLocalizedMessage());
                }
                return true;
            } catch (BlockStoreException e) {
//                blockStore.abortDatabaseBatchWrite();
                throw e;
            }

        } catch (BlockStoreException e) {
            log.error("", e);
            throw new VerificationException(e);
        } catch (VerificationException e) {
            log.info("Could not verify block:\n" + e.getLocalizedMessage() + "\n" + block.toString());
            throw e;
        } catch (Exception e) {
            log.error("", e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    public boolean solidifyWaiting(Block block, boolean runConsensusLogic) {
        lock.lock();
        try {
            SolidityState solidityState = validatorService.checkSolidity(block, false);
            
            try {
                blockStore.beginDatabaseBatchWrite();
                solidifyBlock(block, solidityState, runConsensusLogic, false);
                blockStore.commitDatabaseBatchWrite();
                try {
                    // TODO this is recursive and may blow the stack
                    scanWaitingBlocks(block, runConsensusLogic);
                } catch (BlockStoreException | VerificationException e) {
                    log.debug(e.getLocalizedMessage());
                }
                return true;
            } catch (BlockStoreException e) {
                blockStore.abortDatabaseBatchWrite();
                throw e;
            }

        } catch (BlockStoreException e) {
            log.error("", e);
            throw new VerificationException(e);
        } catch (VerificationException e) {
            log.info("Could not verify block:\n" + e.getLocalizedMessage() + "\n" + block.toString());
            throw e;
        } catch (Exception e) {
            log.error("", e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    private void scanWaitingBlocks(Block block, boolean runConsensusLogic) throws BlockStoreException {
        // Finally, look in the solidity waiting queue for blocks that are still
        // waiting
        for (Block b : blockStore.getUnsolidBlocks(block.getHash().getBytes())) {
            try {
                // Clear from waiting list
                blockStore.deleteUnsolid(b.getHash());

                // If going through or waiting for more dependencies, all is
                // good
                solidifyWaiting(b, runConsensusLogic);

            } catch (VerificationException e) {
                // If the block is deemed invalid, we do not propagate the error
                // upwards
                log.debug(e.getMessage());
            }
        }
    }

    /**
     * Inserts the specified block into the DB
     * 
     * @param block
     *            the block
     * @param solidityState 
     * @param height
     *            the block's height
     * @throws BlockStoreException
     * @throws VerificationException
     */
    private void connect(final Block block, SolidityState solidityState) throws BlockStoreException, VerificationException {
        checkState(lock.isHeldByCurrentThread());
        blockStore.put(block);
        solidifyBlock(block, solidityState, true, false);
    }

    /**
     * Get the {@link Script} from the script bytes or return Script of empty
     * byte array.
     */
    private Script getScript(byte[] scriptBytes) {
        try {
            return new Script(scriptBytes);
        } catch (Exception e) {
            return new Script(new byte[0]);
        }
    }

    /**
     * Get the address from the {@link Script} if it exists otherwise return
     * empty string "".
     *
     * @param script
     *            The script.
     * @return The address.
     */
    private String getScriptAddress(@Nullable Script script) {
        String address = "";
        try {
            if (script != null) {
                address = script.getToAddress(params, true).toString();
            }
        } catch (Exception e) {
            // e.printStackTrace();
        }
        return address;
    }

    private void synchronizationUserData(Sha256Hash blockhash, DataClassName dataClassName, byte[] data, String pubKey,
            long blocktype) throws BlockStoreException {
        UserData userData = this.blockStore.queryUserDataWithPubKeyAndDataclassname(dataClassName.name(), pubKey);
        if (userData == null) {
            userData = new UserData();
            userData.setBlockhash(blockhash);
            userData.setData(data);
            userData.setDataclassname(dataClassName.name());
            userData.setPubKey(pubKey);
            userData.setBlocktype(blocktype);
            this.blockStore.insertUserData(userData);
            return;
        }
        userData.setBlockhash(blockhash);
        userData.setData(data);
        this.blockStore.updateUserData(userData);
    }

    @SuppressWarnings("unchecked")
    private void synchronizationVOSData(byte[] data) throws Exception {
        String jsonStr = new String(data);
        HashMap<String, Object> map = Json.jsonmapper().readValue(jsonStr, HashMap.class);
        String vosKey = (String) map.get("vosKey");
        String pubKey = (String) map.get("pubKey");
        VOSExecute vosExecute_ = this.blockStore.getVOSExecuteWith(vosKey, pubKey);
        if (vosExecute_ == null) {
            vosExecute_ = new VOSExecute();
            vosExecute_.setVosKey(vosKey);
            vosExecute_.setPubKey(pubKey);
            vosExecute_.setData(Utils.HEX.decode((String) map.get("dataHex")));
            vosExecute_.setStartDate(new Date((Long) map.get("startDate")));
            vosExecute_.setEndDate(new Date((Long) map.get("endDate")));
            vosExecute_.setExecute(1);
            this.blockStore.insertVOSExecute(vosExecute_);
            return;
        }
        vosExecute_.setData(Utils.HEX.decode((String) map.get("dataHex")));
        vosExecute_.setStartDate(new Date((Long) map.get("startDate")));
        vosExecute_.setEndDate(new Date((Long) map.get("endDate")));
        vosExecute_.setExecute(vosExecute_.getExecute() + 1);
        this.blockStore.updateVOSExecute(vosExecute_);
    }

    /**
     * Adds the specified block and all approved blocks to the confirmed set. This
     * will connect all transactions of the block by marking used UTXOs spent
     * and adding new UTXOs to the db.
     * 
     * @param blockHash
     * @throws BlockStoreException
     */
    public void confirm(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes) throws BlockStoreException {
        // Write to DB
        try {
            blockStore.beginDatabaseBatchWrite();
            confirmUntil(blockHash, traversedBlockHashes);
            blockStore.commitDatabaseBatchWrite();
        } catch (BlockStoreException e) {
            blockStore.abortDatabaseBatchWrite();
            throw e;
        }
    }

    private void confirmUntil(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {
        // If already confirmed, return
        if (traversedBlockHashes.contains(blockHash))
            return;

        BlockWrap blockWrap = blockStore.getBlockWrap(blockHash);
        BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();
        Block block = blockWrap.getBlock();

        // If already confirmed, return
        if (blockEvaluation.isConfirmed())
            return;

        // Set confirmed
        blockStore.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), true);

        // Connect all approved blocks first if not traversed already
        if (!traversedBlockHashes.contains(block.getPrevBlockHash()))
            confirmUntil(block.getPrevBlockHash(), traversedBlockHashes);
        if (!traversedBlockHashes.contains(block.getPrevBranchBlockHash()))
            confirmUntil(block.getPrevBranchBlockHash(), traversedBlockHashes);

        // Confirm the block
        confirmBlock(blockWrap);

        // Keep track of confirmed blocks
        traversedBlockHashes.add(blockHash);
    }
    
    /**
     * Calculates and inserts any virtual transaction outputs so dependees can become solid
     * 
     * @param block
     * @return
     * @throws BlockStoreException
     */
    public Optional<OrderMatchingResult> calculateBlock(Block block) throws BlockStoreException {
        
        Transaction tx = null;
        OrderMatchingResult matchingResult = null;

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
            tx = generateVirtualMiningRewardTX(block);
            insertVirtualUTXOs(block, tx);
            
            // Get list of consumed orders, virtual order matching tx and newly
            // generated remaining order book
            matchingResult = generateOrderMatching(block);
            tx = matchingResult.getOutputTx();
            insertVirtualUTXOs(block, tx);
            insertVirtualOrderRecords(block, matchingResult.getRemainingOrders());
            break;
        case BLOCKTYPE_TOKEN_CREATION:
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
        case BLOCKTYPE_ORDER_OP:
            break;
        case BLOCKTYPE_ORDER_RECLAIM:
            tx = generateReclaimTX(block);
            insertVirtualUTXOs(block, tx);
            break;
        default:
            throw new RuntimeException("Not Implemented");

        }
        
        // Return the computation result
        return Optional.ofNullable(matchingResult);
    }

    private void confirmBlock(BlockWrap block) throws BlockStoreException {
        
        // Update block's transactions in db
        for (final Transaction tx : block.getBlock().getTransactions()) {
            confirmTransaction(block, tx);
        }

        // type-specific updates
        switch (block.getBlock().getBlockType()) {
        case BLOCKTYPE_CROSSTANGLE:
            break;
        case BLOCKTYPE_FILE:
            break;
        case BLOCKTYPE_GOVERNANCE:
            break;
        case BLOCKTYPE_INITIAL:
            break;
        case BLOCKTYPE_REWARD:
            // For rewards, update reward to be confirmed now
            confirmReward(block);
            confirmOrderMatching(block);
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            // For token creations, update token db
            confirmToken(block);
            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
        case BLOCKTYPE_VOS:
            confirmVOSOrUserData(block);
            break;
        case BLOCKTYPE_VOS_EXECUTE:
            confirmVOSExecute(block);
            break;
        case BLOCKTYPE_ORDER_OPEN:
            confirmOrderOpen(block);
            break;
        case BLOCKTYPE_ORDER_OP:
            break;
        case BLOCKTYPE_ORDER_RECLAIM:
            confirmOrderReclaim(block);
            break;
        default:
            throw new RuntimeException("Not Implemented");

        }
    }

    private void confirmVOSOrUserData(BlockWrap block) {
        Transaction tx = block.getBlock().getTransactions().get(0);
        if (tx.getData() != null && tx.getDataSignature() != null) {
            try {
                @SuppressWarnings("unchecked")
                List<HashMap<String, Object>> multiSignBies = Json.jsonmapper().readValue(tx.getDataSignature(),
                        List.class);
                Map<String, Object> multiSignBy = multiSignBies.get(0);
                byte[] pubKey = Utils.HEX.decode((String) multiSignBy.get("publickey"));
                byte[] data = tx.getHash().getBytes();
                byte[] signature = Utils.HEX.decode((String) multiSignBy.get("signature"));
                boolean success = ECKey.verify(data, signature, pubKey);
                if (!success) {
                    throw new BlockStoreException("multisign signature error");
                }
                this.synchronizationUserData(block.getBlock().getHash(), DataClassName.valueOf(tx.getDataClassName()),
                        tx.getData(), (String) multiSignBy.get("publickey"), block.getBlock().getBlockType().ordinal());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void confirmVOSExecute(BlockWrap block) {
        Transaction tx1 = block.getBlock().getTransactions().get(0);
        if (tx1.getData() != null && tx1.getDataSignature() != null) {
            try {
                @SuppressWarnings("unchecked")
                List<HashMap<String, Object>> multiSignBies = Json.jsonmapper().readValue(tx1.getDataSignature(),
                        List.class);
                Map<String, Object> multiSignBy = multiSignBies.get(0);
                byte[] pubKey = Utils.HEX.decode((String) multiSignBy.get("publickey"));
                byte[] data = tx1.getHash().getBytes();
                byte[] signature = Utils.HEX.decode((String) multiSignBy.get("signature"));
                boolean success = ECKey.verify(data, signature, pubKey);
                if (!success) {
                    throw new BlockStoreException("multisign signature error");
                }
                this.synchronizationVOSData(tx1.getData());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void confirmOrderMatching(BlockWrap block) throws BlockStoreException {
        // Get list of consumed orders, virtual order matching tx and newly
        // generated remaining order book
        // TODO don't calculate again, it should already have been calculated before
        OrderMatchingResult actualCalculationResult = generateOrderMatching(block.getBlock());

        // All consumed order records are now spent by this block
        for (OrderRecord o : actualCalculationResult.getSpentOrders()) {
            blockStore.updateOrderSpent(o.getInitialBlockHash(), o.getIssuingMatcherBlockHash(), true, block.getBlock().getHash());
        }

        // Set virtual outputs confirmed
        confirmVirtualCoinbaseTransaction(block);

        // Set new orders confirmed
        for (OrderRecord o : actualCalculationResult.getRemainingOrders())
            blockStore.updateOrderConfirmed(o.getInitialBlockHash(), o.getIssuingMatcherBlockHash(), true);

        // Update the matching history in db
        tickerService.addMatchingEvents(actualCalculationResult);
    }

    private void confirmOrderReclaim(BlockWrap block) throws BlockStoreException {
        // Set virtual outputs confirmed
        confirmVirtualCoinbaseTransaction(block);
        
        // Read the requested reclaim
        OrderReclaimInfo info = null;
        try {
            info = OrderReclaimInfo.parse(block.getBlock().getTransactions().get(0).getData());
        } catch (IOException e) {
            // Cannot happen.
            throw new RuntimeException(e);
        }

        // Set consumed order record to spent and set spender block to this
        // block's hash
        blockStore.updateOrderSpent(info.getOrderBlockHash(), Sha256Hash.ZERO_HASH, true, block.getBlock().getHash());        

        // Insert helper dependencies
        blockStore.insertDependents(block.getBlock().getHash(), info.getOrderBlockHash());
        blockStore.insertDependents(block.getBlock().getHash(), info.getNonConfirmingMatcherBlockHash());
    }

    private void confirmOrderOpen(BlockWrap block) throws BlockStoreException {
        // Set own output confirmed
        blockStore.updateOrderConfirmed(block.getBlock().getHash(), Sha256Hash.ZERO_HASH, true);
    }

    private void confirmReward(BlockWrap block) throws BlockStoreException {
        // Set virtual reward tx outputs confirmed
        confirmVirtualCoinbaseTransaction(block);

        // Set used other output spent
        blockStore.updateRewardSpent(blockStore.getRewardPrevBlockHash(block.getBlock().getHash()), true, block.getBlock().getHash());

        // Set own output confirmed
        blockStore.updateRewardConfirmed(block.getBlock().getHash(), true);
    }

    private void insertVirtualOrderRecords(Block block, Collection<OrderRecord> orders) {
        try {
            for (OrderRecord o : orders)
                blockStore.insertOrder(o);
        } catch (BlockStoreException e) {
            // Expected after reorgs
            log.warn("Probably reinserting orders: ", e);
        }
    }

    private void insertVirtualUTXOs(Block block, Transaction virtualTx) {
        try {
            ArrayList<Transaction> txs = new ArrayList<Transaction>();
            txs.add(virtualTx);
            connectUTXOs(block, txs);
        } catch (BlockStoreException e) {
            // Expected after reorgs
            log.warn("Probably reinserting reward: ", e);
        }
    }

    private void confirmToken(BlockWrap block) throws BlockStoreException {
        // Set used other output spent
        blockStore.updateTokenSpent(blockStore.getTokenPrevblockhash(block.getBlock().getHashAsString()), true, block.getBlock().getHash());

        // Set own output confirmed
        blockStore.updateTokenConfirmed(block.getBlock().getHashAsString(), true);
    }

    private void confirmTransaction(BlockWrap block, Transaction tx) throws BlockStoreException {
        // Set used other outputs spent
        if (!tx.isCoinBase()) {
            for (TransactionInput in : tx.getInputs()) {
                UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getBlockHash(),
                        in.getOutpoint().getTxHash(), in.getOutpoint().getIndex());

                // Sanity check
                if (prevOut == null)
                    throw new RuntimeException("Attempted to spend a non-existent output!");
                if (prevOut.isSpent())
                    throw new RuntimeException("Attempted to spend an already spent output!");

                blockStore.updateTransactionOutputSpent(prevOut.getBlockHash(), prevOut.getTxHash(), prevOut.getIndex(),
                        true, block.getBlockHash());
            }
        }

        // Set own outputs confirmed
        for (TransactionOutput out : tx.getOutputs()) {
            blockStore.updateTransactionOutputConfirmed(block.getBlockHash(), tx.getHash(), out.getIndex(), true);
        }
    }

    private void confirmVirtualCoinbaseTransaction(BlockWrap block) throws BlockStoreException {
        // Set own outputs confirmed
        blockStore.updateAllTransactionOutputsConfirmed(block.getBlock().getHash(), true);
    }

    /**
     * Confirms the specified block and all approved blocks. This
     * will connect all transactions of the block by marking used UTXOs spent
     * and adding new UTXOs to the db.
     * 
     * @param blockHash
     * @throws BlockStoreException
     */
    public void unconfirm(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes) throws BlockStoreException {
        // Write to DB
        try {
            blockStore.beginDatabaseBatchWrite();
            unconfirmFrom(blockHash, traversedBlockHashes);
            blockStore.commitDatabaseBatchWrite();
        } catch (BlockStoreException e) {
            blockStore.abortDatabaseBatchWrite();
            throw e;
        }
    }

    private void unconfirmFrom(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {
        // If already confirmed, return
        if (traversedBlockHashes.contains(blockHash))
            return;

        BlockWrap blockWrap = blockStore.getBlockWrap(blockHash);
        BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();
        Block block = blockWrap.getBlock();

        // If already unconfirmed, return
        if (!blockEvaluation.isConfirmed())
            return;

        // Unconfirm all dependents
        unconfirmDependents(block, traversedBlockHashes);

        // Then unconfirm the block itself
        unconfirmBlock(block);

        // Set unconfirmed
        blockStore.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), false);

        // Keep track of unconfirmed blocks
        traversedBlockHashes.add(blockHash);
    }

    private void unconfirmDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes) throws BlockStoreException {
        // Unconfirm all approver blocks first
        for (Sha256Hash approver : blockStore.getSolidApproverBlockHashes(block.getHash())) {
            unconfirmFrom(approver, traversedBlockHashes);
        }

        // Unconfirm all helper dependents
        for (Sha256Hash dependent : blockStore.getDependents(block.getHash())) {
            unconfirmFrom(dependent, traversedBlockHashes);
        }

        // Then clear own dependencies since no longer confirmed
        blockStore.removeDependents(block.getHash());

        // Disconnect all transaction output dependents
        for (Transaction tx : block.getTransactions()) {
            for (TransactionOutput txout : tx.getOutputs()) {
                UTXO utxo = blockStore.getTransactionOutput(block.getHash(), tx.getHash(), txout.getIndex());
                if (utxo.isSpent()) {
                    unconfirmFrom(
                            blockStore.getTransactionOutputSpender(block.getHash(), tx.getHash(), txout.getIndex())
                                    .getBlockHash(),
                            traversedBlockHashes);
                }
            }
        }

        // Disconnect all type-specific dependents
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
            // Unconfirm dependents
            unconfirmRewardDependents(block, traversedBlockHashes);
            unconfirmOrderMatchingDependents(block, traversedBlockHashes);
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            // Unconfirm dependents
            unconfirmTokenDependents(block, traversedBlockHashes);
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
            unconfirmOrderOpenDependents(block, traversedBlockHashes);
            break;
        case BLOCKTYPE_ORDER_OP:
            break;
        case BLOCKTYPE_ORDER_RECLAIM:
            unconfirmOrderReclaimDependents(block, traversedBlockHashes);
            break;
        default:
            throw new RuntimeException("Not Implemented");

        }
    }

    private void unconfirmOrderMatchingDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {
        // Get list of consumed orders, virtual order matching tx and newly
        // generated remaining order book
        OrderMatchingResult matchingResult = generateOrderMatching(block);

        // Disconnect all virtual transaction output dependents
        Transaction tx = matchingResult.getOutputTx();
        for (TransactionOutput txout : tx.getOutputs()) {
            UTXO utxo = blockStore.getTransactionOutput(block.getHash(), tx.getHash(), txout.getIndex());
            if (utxo.isSpent()) {
                unconfirmFrom(blockStore
                        .getTransactionOutputSpender(block.getHash(), tx.getHash(), txout.getIndex()).getBlockHash(),
                        traversedBlockHashes);
            }
        }
    }

    private void unconfirmOrderReclaimDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {

        // Disconnect all virtual transaction output dependents
        Transaction tx = generateReclaimTX(block);
        for (TransactionOutput txout : tx.getOutputs()) {
            UTXO utxo = blockStore.getTransactionOutput(block.getHash(), tx.getHash(), txout.getIndex());
            if (utxo.isSpent()) {
                unconfirmFrom(blockStore
                        .getTransactionOutputSpender(block.getHash(), tx.getHash(), txout.getIndex()).getBlockHash(),
                        traversedBlockHashes);
            }
        }
    }

    private void unconfirmOrderOpenDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {

        // Disconnect order record spender
        if (blockStore.getOrderSpent(block.getHash(), Sha256Hash.ZERO_HASH)) {
            unconfirmFrom(blockStore.getOrderSpender(block.getHash(), Sha256Hash.ZERO_HASH),
                    traversedBlockHashes);
        }
    }

    private void unconfirmTokenDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {

        // Disconnect token record spender
        if (blockStore.getTokenSpent(block.getHashAsString())) {
            unconfirmFrom(blockStore.getTokenSpender(block.getHashAsString()), traversedBlockHashes);
        }

        // If applicable: Disconnect all domain definitions that were based on
        // this domain
        Token token = blockStore.getToken(block.getHashAsString());
        if (token.getTokentype() == TokenType.domainname.ordinal()) {
            List<String> dependents = blockStore
                    .getDomainDescendantConfirmedBlocks(token.getDomainPredecessorBlockHash());
            for (String b : dependents) {
                unconfirmFrom(Sha256Hash.wrap(b), traversedBlockHashes);
            }
        }
    }

    private void unconfirmRewardDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {

        // Disconnect reward record spender
        if (blockStore.getRewardSpent(block.getHash())) {
            unconfirmFrom(blockStore.getRewardSpender(block.getHash()), traversedBlockHashes);
        }

        // Disconnect all virtual transaction output dependents
        Transaction tx = generateVirtualMiningRewardTX(block);
        for (TransactionOutput txout : tx.getOutputs()) {
            UTXO utxo = blockStore.getTransactionOutput(block.getHash(), tx.getHash(), txout.getIndex());
            if (utxo.isSpent()) {
                unconfirmFrom(blockStore
                        .getTransactionOutputSpender(block.getHash(), tx.getHash(), txout.getIndex()).getBlockHash(),
                        traversedBlockHashes);
            }
        }
    }

    /**
     * Disconnect the block, unconfirming all UTXOs and UTXO-like constructs.
     * 
     * @throws BlockStoreException
     *             if the block store had an underlying error or block does not
     *             exist in the block store at all.
     */
    private void unconfirmBlock(Block block) throws BlockStoreException {
        // Unconfirm all transactions of the block
        for (Transaction tx : block.getTransactions()) {
            unconfirmTransaction(tx, block);
        }

        // Then unconfirm type-specific stuff
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
            unconfirmReward(block);
            unconfirmOrderMatching(block);
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            unconfirmToken(block.getHashAsString());
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
            unconfirmOrderOpen(block);
            break;
        case BLOCKTYPE_ORDER_OP:
            break;
        case BLOCKTYPE_ORDER_RECLAIM:
            unconfirmOrderReclaim(block);
            break;
        default:
            throw new RuntimeException("Not Implemented");

        }
    }

    private void unconfirmOrderMatching(Block block) throws BlockStoreException {
        // Get list of consumed orders, virtual order matching tx and newly
        // generated remaining order book
        OrderMatchingResult matchingResult = generateOrderMatching(block);

        // All consumed order records are now unspent by this block
        for (OrderRecord o : matchingResult.getSpentOrders()) {
            blockStore.updateOrderSpent(o.getInitialBlockHash(), o.getIssuingMatcherBlockHash(), false, null);
        }

        // Set virtual outputs unconfirmed
        unconfirmVirtualCoinbaseTransaction(block);

        // Set new orders unconfirmed
        for (OrderRecord o : matchingResult.getRemainingOrders())
            blockStore.updateOrderConfirmed(o.getInitialBlockHash(), o.getIssuingMatcherBlockHash(), false);

        // Update the matching history in db
        tickerService.removeMatchingEvents(matchingResult.outputTx, matchingResult.tokenId2Events);
    }

    private void unconfirmOrderReclaim(Block block) throws BlockStoreException {
        // Read the requested reclaim
        OrderReclaimInfo info = null;
        try {
            info = OrderReclaimInfo.parse(block.getTransactions().get(0).getData());
        } catch (IOException e) {
            // Cannot happen.
            throw new RuntimeException(e);
        }

        // Set consumed order record unspent
        blockStore.updateOrderSpent(info.getOrderBlockHash(), Sha256Hash.ZERO_HASH, false, null);

        // Set virtual outputs unconfirmed
        unconfirmVirtualCoinbaseTransaction(block);
    }

    private void unconfirmOrderOpen(Block block) throws BlockStoreException {
        // Set own output unconfirmed
        blockStore.updateOrderConfirmed(block.getHash(), Sha256Hash.ZERO_HASH, false);
    }

    private void unconfirmReward(Block block) throws BlockStoreException {
        // Unconfirm virtual tx
        unconfirmVirtualCoinbaseTransaction(block);

        // Set used other output unspent
        blockStore.updateRewardSpent(blockStore.getRewardPrevBlockHash(block.getHash()), false, null);

        // Set own output unconfirmed
        blockStore.updateRewardConfirmed(block.getHash(), false);
    }

    private void unconfirmToken(String blockhash) throws BlockStoreException {
        // Set used other output unspent
        blockStore.updateTokenSpent(blockStore.getTokenPrevblockhash(blockhash), false, null);

        // Set own output unconfirmed
        blockStore.updateTokenConfirmed(blockhash, false);
    }

    /**
     * Disconnects the UTXOs of the transaction
     * 
     * @param tx
     * @param parentBlock
     * @throws BlockStoreException
     */
    private void unconfirmTransaction(Transaction tx, Block parentBlock) throws BlockStoreException {
        // Set used outputs as unspent
        if (!tx.isCoinBase()) {
            for (TransactionInput txin : tx.getInputs()) {
                blockStore.updateTransactionOutputSpent(txin.getOutpoint().getBlockHash(),
                        txin.getOutpoint().getTxHash(), txin.getOutpoint().getIndex(), false, null);
            }
        }

        // Set own outputs unconfirmed
        for (TransactionOutput txout : tx.getOutputs()) {
            blockStore.updateTransactionOutputConfirmed(parentBlock.getHash(), tx.getHash(), txout.getIndex(), false);
        }
    }
    
    private void unconfirmVirtualCoinbaseTransaction(Block parentBlock) throws BlockStoreException {
        // Set own outputs unconfirmed
        blockStore.updateAllTransactionOutputsConfirmed(parentBlock.getHash(), false);
    }

    public void solidifyBlock(Block block, SolidityState solidityState, boolean runConsensusLogic, boolean setMilestoneSuccess) throws BlockStoreException {
        // Sanity check: TODO && soliditiyState!=Success, then readd ignore success reset
        if (blockStore.getBlockEvaluation(block.getHash()).getSolid() == 2)
            throw new RuntimeException("Shouldn't happen");
        
        switch (solidityState.getState()) {
        case MissingCalculation:            
            blockStore.updateBlockEvaluationSolid(block.getHash(), 1);
            
            // Reward blocks follow different logic: If this is new, run consensus logic
            if (block.getBlockType() == Type.BLOCKTYPE_REWARD && runConsensusLogic) {
                addReward(block);                
                runConsensusLogic(block);
                return;
            }
            
            // Insert other blocks into waiting list
            insertUnsolidBlock(block, solidityState);
            break;
        case MissingPredecessor:
            blockStore.updateBlockEvaluationSolid(block.getHash(), 0);
            
            // Insert into waiting list
            insertUnsolidBlock(block, solidityState);
            break;
        case Success:
            // TODO If already set, nothing to do here...
//            if (blockStore.getBlockEvaluation(block.getHash()).getSolid() == 2)
//                return;

            // TODO don't calculate again, it may already have been calculated before
            connectUTXOs(block);
            connectTypeSpecificUTXOs(block); 
            calculateBlock(block); 

            if (block.getBlockType() == Type.BLOCKTYPE_REWARD && !setMilestoneSuccess) {
                // If we don't want to set the milestone success, initialize as missing calc
                blockStore.updateBlockEvaluationSolid(block.getHash(), 1);
            } else {
                // Else normal update
                blockStore.updateBlockEvaluationSolid(block.getHash(), 2);
                
                // Update tips table
                blockStore.deleteTip(block.getPrevBlockHash());
                blockStore.deleteTip(block.getPrevBranchBlockHash());
                blockStore.deleteTip(block.getHash());
                blockStore.insertTip(block.getHash());
            }
            
            if (block.getBlockType() == Type.BLOCKTYPE_REWARD && runConsensusLogic) {
                addReward(block);                
                runConsensusLogic(block);
                return;
            }
            
            break;
        case Invalid:
            blockStore.updateBlockEvaluationSolid(block.getHash(), -1);  
            break;
        }
    }

    protected void insertUnsolidBlock(Block block, SolidityState solidityState) throws BlockStoreException {
        if (solidityState.getState() == State.Success || solidityState.getState() == State.Invalid) {
            log.warn("Block should not be inserted into waiting list");
            return;
        }

        // Insert waiting into solidity waiting queue until dependency is
        // resolved
        blockStore.insertUnsolid(block, solidityState);
    }

    protected void connectUTXOs(Block block) throws BlockStoreException, VerificationException {
        List<Transaction> transactions = block.getTransactions();
        connectUTXOs(block, transactions);
    }

    private void connectUTXOs(Block block, List<Transaction> transactions) throws BlockStoreException {
        for (final Transaction tx : transactions) {
            boolean isCoinBase = tx.isCoinBase();
            if (!isCoinBase) {
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    TransactionInput in = tx.getInputs().get(index);
                    UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getBlockHash(),
                            in.getOutpoint().getTxHash(), in.getOutpoint().getIndex());
                    if(prevOut!=null)
                    blockStore.updateTransactionOutputSpendPending(prevOut.getBlockHash(), prevOut.getTxHash(),
                            prevOut.getIndex(), true, System.currentTimeMillis());
                }
            }
            for (TransactionOutput out : tx.getOutputs()) {
                Script script = getScript(out.getScriptBytes());
                String fromAddress = ""; 
                try {
                    if (!isCoinBase) {
                        fromAddress = tx.getInputs().get(0).getFromAddress().toBase58();
                    }
                } catch (ScriptException e) {
                    // No address found.
                }
                UTXO newOut = new UTXO(tx.getHash(), out.getIndex(), out.getValue(), isCoinBase, script,
                        getScriptAddress(script), block.getHash(), fromAddress, tx.getMemo(),
                        Utils.HEX.encode(out.getValue().getTokenid()), false, false, false, 0, 0);
                newOut.setTime(System.currentTimeMillis() / 1000);
                blockStore.addUnspentTransactionOutput(newOut);
                if (script.isSentToMultiSig()) {
                    int minsignnumber = script.getNumberOfSignaturesRequiredToSpend();
                    for (ECKey ecKey : script.getPubKeys()) {
                        String toaddress = ecKey.toAddress(params).toBase58();
                        OutputsMulti outputsMulti = new OutputsMulti(newOut.getTxHash(), toaddress, newOut.getIndex(),
                                minsignnumber);
                        this.blockStore.insertOutputsMulti(outputsMulti);
                    }
                }
            }
        }
    }

    private void connectTypeSpecificUTXOs(Block block) throws BlockStoreException {
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
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            connectToken(block);
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
            connectOrder(block);
            break;
        case BLOCKTYPE_ORDER_OP:
            break;
        case BLOCKTYPE_ORDER_RECLAIM:
            break;
        default:
            break;

        }
    }

    private void connectOrder(Block block) throws BlockStoreException {
        try {
            OrderOpenInfo reqInfo = OrderOpenInfo.parse(block.getTransactions().get(0).getData());

            Coin offer = validatorService.countBurnedToken(block);
            Side side = offer.getTokenHex().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING) ? Side.BUY : Side.SELL;

            OrderRecord record = new OrderRecord(block.getHash(), Sha256Hash.ZERO_HASH, offer.getValue().longValue(),
                    offer.getTokenHex(), false, false, null, reqInfo.getTargetValue(), reqInfo.getTargetTokenid(),
                    reqInfo.getBeneficiaryPubKey(), reqInfo.getValidToTime(), 0, reqInfo.getValidFromTime(),
                    side.name(), reqInfo.getBeneficiaryAddress());

            blockStore.insertOrder(record);
        } catch (IOException e) {
            // Cannot happen when connecting
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void connectToken(Block block) throws BlockStoreException {
        Transaction tx = block.getTransactions().get(0);
        if (tx.getData() != null) {
            try {
                byte[] buf = tx.getData();
                TokenInfo tokenInfo = TokenInfo.parse(buf);

                // Correctly insert tokens
                tokenInfo.getToken().setConfirmed(false);
                tokenInfo.getToken().setBlockhash(block.getHashAsString());

                this.blockStore.insertToken(block.getHashAsString(), tokenInfo.getToken());

                // Correctly insert additional data
                for (MultiSignAddress permissionedAddress : tokenInfo.getMultiSignAddresses()) {
                    if (permissionedAddress == null)
                        continue;
                    // The primary key must be the correct block
                    permissionedAddress.setBlockhash(block.getHashAsString());
                    permissionedAddress.setTokenid(tokenInfo.getToken().getTokenid());
                    if (permissionedAddress.getAddress() != null)
                        blockStore.insertMultiSignAddress(permissionedAddress);
                }
            } catch (IOException e) {
                // Cannot happen when connecting
                e.printStackTrace();
                throw new RuntimeException(e);
            }

        }
    }

    private void insertIntoOrderBooks(OrderRecord o, TreeMap<String, OrderBook> orderBooks,
            ArrayList<OrderRecord> orderId2Order, long orderId) {

        Side side = o.getOfferTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING) ? Side.BUY : Side.SELL;
        long price = o.getOfferTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                ? o.getOfferValue() / o.getTargetValue()
                : o.getTargetValue() / o.getOfferValue();
        long size = o.getOfferTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING) ? o.getTargetValue()
                : o.getOfferValue();
        String tokenId = o.getOfferTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING) ? o.getTargetTokenid()
                : o.getOfferTokenid();

        OrderBook orderBook = orderBooks.get(tokenId);
        if (orderBook == null) {
            orderBook = new OrderBook(new OrderBookEvents());
            orderBooks.put(tokenId, orderBook);
        }

        orderId2Order.add(o);
        orderBook.enter(orderId, side, price, size);
    }

    /**
     * Deterministically execute the order matching algorithm on this block.
     * 
     * @return new consumed orders, virtual order matching tx and newly
     *         generated remaining MODIFIED order book
     * @throws BlockStoreException
     */
    public OrderMatchingResult generateOrderMatching(Block block) throws BlockStoreException {
        Map<ByteBuffer, TreeMap<String, Long>> pubKey2Proceeds = new HashMap<>();

        // Get previous order matching block
        Sha256Hash prevHash = null;
        long fromHeight = 0;
        long toHeight = 0;
        try {
            RewardInfo info = RewardInfo.parse(block.getTransactions().get(0).getData());
            prevHash = info.getPrevRewardHash();
            fromHeight = info.getFromHeight();
            toHeight = info.getToHeight();
        } catch (IOException e) {
            // Cannot happen since checked before
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        final Block prevMatchingBlock = blockStore.getBlockWrap(prevHash).getBlock();

        // Deterministic randomization
        byte[] randomness = Utils.xor(block.getPrevBlockHash().getBytes(), block.getPrevBranchBlockHash().getBytes());

        // Collect all orders approved by this block in the interval
        List<OrderOpInfo> cancels = new ArrayList<>(), refreshs = new ArrayList<>();
        Map<Sha256Hash, OrderRecord> newOrders = new TreeMap<>(
                Comparator.comparing(hash -> Sha256Hash.wrap(Utils.xor(((Sha256Hash) hash).getBytes(), randomness))));
        Set<OrderRecord> spentOrders = new HashSet<>(blockStore.getOrderMatchingIssuedOrders(prevHash).values());
        Set<OrderRecord> cancelledOrders = new HashSet<>();

        for (BlockWrap b : collectConsumedOrdersAndOpsBlocks(block, fromHeight, toHeight)) {
            if (b.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_OPEN) {
                final Sha256Hash blockHash = b.getBlock().getHash();

                // Add only if not taken yet or taken by this block
                OrderRecord order = blockStore.getOrder(blockHash, Sha256Hash.ZERO_HASH);
                if (!order.isSpent() || order.getSpenderBlockHash().equals(block.getHash())) {
                    newOrders.put(blockHash, order);
                    spentOrders.add(blockStore.getOrder(blockHash, Sha256Hash.ZERO_HASH));
                }
            } else if (b.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_OP) {
                OrderOpInfo info = null;
                try {
                    info = OrderOpInfo.parse(b.getBlock().getTransactions().get(0).getData());
                } catch (IOException e) {
                    // Cannot happen since checked before
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
                if (info.getOp() == OrderOp.CANCEL)
                    cancels.add(info);
                else if (info.getOp() == OrderOp.REFRESH)
                    refreshs.add(info);
            }
        }

        // OPTIMIZATION POSSIBLE: stop getting orders twice, just do a deep copy
        // Collect all remaining orders, split into newOrders and oldOrders
        HashMap<Sha256Hash, OrderRecord> remainingOrders = blockStore.getOrderMatchingIssuedOrders(prevHash);
        Map<Sha256Hash, OrderRecord> oldOrders = new TreeMap<>(
                Comparator.comparing(hash -> Sha256Hash.wrap(Utils.xor(((Sha256Hash) hash).getBytes(), randomness))));
        oldOrders.putAll(remainingOrders);
        remainingOrders.putAll(newOrders);

        // Issue timeout cancels, set issuing order blockhash
        Iterator<Entry<Sha256Hash, OrderRecord>> it = remainingOrders.entrySet().iterator();
        while (it.hasNext()) {
            OrderRecord order = it.next().getValue();
            order.setIssuingMatcherBlockHash(block.getHash());
            if (order.isTimeouted(block.getTimeSeconds())) {
                cancelledOrders.add(order);
            }
        }

        // Process cancel ops after matching
        for (OrderOpInfo c : cancels) {
            if (remainingOrders.containsKey(c.getInitialBlockHash())) {
                cancelledOrders.add(remainingOrders.get(c.getInitialBlockHash()));
            }
        }

        // Remove the now cancelled orders from remaining orders
        for (OrderRecord c : cancelledOrders) {
            if (remainingOrders.remove(c.getInitialBlockHash()) == null)
                log.error("Should not happen: non-existent order");

            oldOrders.remove(c.getInitialBlockHash());
            newOrders.remove(c.getInitialBlockHash());
        }

        // Add to proceeds all cancelled orders going back to the beneficiary
        for (OrderRecord o : cancelledOrders) {
            TreeMap<String, Long> proceeds = pubKey2Proceeds.get(ByteBuffer.wrap(o.getBeneficiaryPubKey()));
            if (proceeds == null) {
                proceeds = new TreeMap<>();
                pubKey2Proceeds.put(ByteBuffer.wrap(o.getBeneficiaryPubKey()), proceeds);
            }
            Long offerTokenProceeds = proceeds.get(o.getOfferTokenid());
            if (offerTokenProceeds == null) {
                offerTokenProceeds = 0L;
                proceeds.put(o.getOfferTokenid(), offerTokenProceeds);
            }
            proceeds.put(o.getOfferTokenid(), offerTokenProceeds + o.getOfferValue());
        }

        // From all orders and ops, begin order matching algorithm by filling
        // order books
        int orderId = 0;
        ArrayList<OrderRecord> orderId2Order = new ArrayList<>();
        TreeMap<String, OrderBook> orderBooks = new TreeMap<String, OrderBook>();

        // Add old orders first
        for (OrderRecord o : oldOrders.values().stream()
                .filter(o -> o.isValidYet(block.getTimeSeconds()) && o.isValidYet(prevMatchingBlock.getTimeSeconds()))
                .collect(Collectors.toList())) {
            insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++);
        }

        // Now orders not valid before but valid now
        for (OrderRecord o : oldOrders.values().stream()
                .filter(o -> o.isValidYet(block.getTimeSeconds()) && !o.isValidYet(prevMatchingBlock.getTimeSeconds()))
                .collect(Collectors.toList())) {
            insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++);
        }

        // Now new orders that are valid yet
        for (OrderRecord o : newOrders.values().stream().filter(o -> o.isValidYet(block.getTimeSeconds()))
                .collect(Collectors.toList())) {
            insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++);
        }

        // Collect and process all matching events
        Map<String, List<Event>> tokenId2Events = new HashMap<>();
        for (Entry<String, OrderBook> orderBook : orderBooks.entrySet()) {
            String tokenId = orderBook.getKey();
            List<Event> events = ((OrderBookEvents) orderBook.getValue().listener()).collect();
            tokenId2Events.put(tokenId, events);

            for (Event event : events) {
                if (!(event instanceof Match))
                    continue;

                Match matchEvent = (Match) event;
                OrderRecord restingOrder = orderId2Order.get(Integer.parseInt(matchEvent.restingOrderId));
                OrderRecord incomingOrder = orderId2Order.get(Integer.parseInt(matchEvent.incomingOrderId));

                // Ensure the pubkeys have a proceeds entry
                TreeMap<String, Long> restingProceeds = pubKey2Proceeds
                        .get(ByteBuffer.wrap(restingOrder.getBeneficiaryPubKey()));
                if (restingProceeds == null) {
                    restingProceeds = new TreeMap<>();
                    pubKey2Proceeds.put(ByteBuffer.wrap(restingOrder.getBeneficiaryPubKey()), restingProceeds);
                }
                TreeMap<String, Long> incomingProceeds = pubKey2Proceeds
                        .get(ByteBuffer.wrap(incomingOrder.getBeneficiaryPubKey()));
                if (incomingProceeds == null) {
                    incomingProceeds = new TreeMap<>();
                    pubKey2Proceeds.put(ByteBuffer.wrap(incomingOrder.getBeneficiaryPubKey()), incomingProceeds);
                }

                // Now disburse proceeds accordingly
                long executedPrice = matchEvent.price;
                long executedAmount = matchEvent.executedQuantity;

                if (matchEvent.incomingSide == Side.BUY) {
                    // When incoming is buy, the resting proceeds would receive
                    // BIG
                    if (restingProceeds.get(NetworkParameters.BIGTANGLE_TOKENID_STRING) == null) {
                        restingProceeds.put(NetworkParameters.BIGTANGLE_TOKENID_STRING, 0L);
                    }

                    // When incoming is buy, the incoming proceeds would receive
                    // the token and perhaps returned BIGs
                    if (incomingProceeds.get(tokenId) == null) {
                        incomingProceeds.put(tokenId, 0L);
                    }
                    if (incomingProceeds.get(NetworkParameters.BIGTANGLE_TOKENID_STRING) == null) {
                        incomingProceeds.put(NetworkParameters.BIGTANGLE_TOKENID_STRING, 0L);
                    }

                    long sellableAmount = restingOrder.getOfferValue();
                    long buyableAmount = incomingOrder.getTargetValue();
                    long incomingPrice = incomingOrder.getOfferValue() / incomingOrder.getTargetValue();

                    // The resting order receives the BIG according to its price
                    restingProceeds.put(NetworkParameters.BIGTANGLE_TOKENID_STRING,
                            restingProceeds.get(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                                    + executedAmount * executedPrice);

                    // The incoming order receives the token according to the
                    // resting price
                    incomingProceeds.put(tokenId, incomingProceeds.get(tokenId) + executedAmount);

                    // The difference in price is returned to the incoming
                    // beneficiary
                    incomingProceeds.put(NetworkParameters.BIGTANGLE_TOKENID_STRING,
                            incomingProceeds.get(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                                    + executedAmount * (incomingPrice - executedPrice));

                    // Finally, the orders could be fulfilled now, so we can
                    // remove them from the order list
                    // Otherwise, we will make the orders smaller by the
                    // executed amounts
                    if (sellableAmount == executedAmount) {
                        remainingOrders.remove(restingOrder.getInitialBlockHash());
                    } else {
                        restingOrder.setOfferValue(restingOrder.getOfferValue() - executedAmount);
                        restingOrder.setTargetValue(restingOrder.getTargetValue() - executedAmount * executedPrice);
                    }
                    if (buyableAmount == executedAmount) {
                        remainingOrders.remove(incomingOrder.getInitialBlockHash());
                    } else {
                        incomingOrder.setOfferValue(incomingOrder.getOfferValue() - executedAmount * incomingPrice);
                        incomingOrder.setTargetValue(incomingOrder.getTargetValue() - executedAmount);
                    }

                } else {
                    // When incoming is sell, the resting proceeds would receive
                    // tokens
                    if (restingProceeds.get(tokenId) == null) {
                        restingProceeds.put(tokenId, 0L);
                    }

                    // When incoming is sell, the incoming proceeds would
                    // receive BIGs
                    if (incomingProceeds.get(NetworkParameters.BIGTANGLE_TOKENID_STRING) == null) {
                        incomingProceeds.put(NetworkParameters.BIGTANGLE_TOKENID_STRING, 0L);
                    }

                    long sellableAmount = incomingOrder.getOfferValue();
                    long buyableAmount = restingOrder.getTargetValue();
                    long incomingPrice = incomingOrder.getTargetValue() / incomingOrder.getOfferValue();

                    // The resting order receives the tokens
                    restingProceeds.put(tokenId, restingProceeds.get(tokenId) + executedAmount);

                    // The incoming order receives the BIG according to the
                    // resting price
                    incomingProceeds.put(NetworkParameters.BIGTANGLE_TOKENID_STRING,
                            incomingProceeds.get(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                                    + executedAmount * executedPrice);

                    // Finally, the orders could be fulfilled now, so we can
                    // remove them from the order list
                    // Otherwise, we will make the orders smaller by the
                    // executed amounts
                    if (sellableAmount == executedAmount) {
                        remainingOrders.remove(incomingOrder.getInitialBlockHash());
                    } else {
                        incomingOrder.setOfferValue(incomingOrder.getOfferValue() - executedAmount);
                        incomingOrder.setTargetValue(incomingOrder.getTargetValue() - executedAmount * incomingPrice);
                    }
                    if (buyableAmount == executedAmount) {
                        remainingOrders.remove(restingOrder.getInitialBlockHash());
                    } else {
                        restingOrder.setOfferValue(restingOrder.getOfferValue() - executedAmount * executedPrice);
                        restingOrder.setTargetValue(restingOrder.getTargetValue() - executedAmount);
                    }
                }
            }
        }

        // Make deterministic tx with proceeds
        Transaction tx = new Transaction(networkParameters);
        for (Entry<ByteBuffer, TreeMap<String, Long>> proceeds : pubKey2Proceeds.entrySet()) {
            byte[] beneficiaryPubKey = proceeds.getKey().array();

            for (Entry<String, Long> tokenProceeds : proceeds.getValue().entrySet()) {
                String tokenId = tokenProceeds.getKey();
                long proceedsValue = tokenProceeds.getValue();

                if (proceedsValue != 0)
                    tx.addOutput(Coin.valueOf(proceedsValue, tokenId), ECKey.fromPublicOnly(beneficiaryPubKey));
            }
        }

        // The coinbase input does not really need to be a valid signature
        TransactionInput input = new TransactionInput(networkParameters, tx, Script
                .createInputScript(block.getPrevBlockHash().getBytes(), block.getPrevBranchBlockHash().getBytes()));
        tx.addInput(input);

        // Return all consumed orders, virtual order matching tx and newly
        // generated remaining MODIFIED order book
        return new OrderMatchingResult(spentOrders, tx, remainingOrders.values(), tokenId2Events);
    }

    private List<BlockWrap> collectConsumedOrdersAndOpsBlocks(Block block, long fromHeight, long toHeight)
            throws BlockStoreException {
        List<BlockWrap> relevantBlocks = new ArrayList<>();

        Queue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());

        BlockWrap prevTrunkBlock = blockStore.getBlockWrap(block.getPrevBlockHash());
        BlockWrap prevBranchBlock = blockStore.getBlockWrap(block.getPrevBranchBlockHash());
        blockQueue.add(prevTrunkBlock);
        blockQueue.add(prevBranchBlock);

        // Initialize
        BlockWrap currentBlock = null, approvedBlock = null;
        long currentHeight;

        // Go backwards by height
        while ((currentBlock = blockQueue.poll()) != null) {
            currentHeight = currentBlock.getBlockEvaluation().getHeight();

            // Stop criterion: Block height lower than approved interval height
            if (currentHeight < fromHeight)
                continue;

            // If in relevant reward height interval and a relevant block,
            // collect it
            if (currentHeight <= toHeight) {
                if (currentBlock.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_OP
                        || currentBlock.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_OPEN)
                    relevantBlocks.add(currentBlock);
            }

            // Continue with both approved blocks
            approvedBlock = blockStore.getBlockWrap(currentBlock.getBlock().getPrevBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                }
            }
            approvedBlock = blockStore.getBlockWrap(currentBlock.getBlock().getPrevBranchBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                }
            }
        }

        return relevantBlocks;
    }

    /**
     * Deterministically create a reclaim tx for a lost order. Assumes the block
     * is a valid reclaim block.
     * 
     * @return reclaim transaction
     * @throws BlockStoreException
     */
    public Transaction generateReclaimTX(Block block) throws BlockStoreException {
        // Read the requested reclaim
        OrderReclaimInfo info = null;
        try {
            info = OrderReclaimInfo.parse(block.getTransactions().get(0).getData());
        } catch (IOException e) {
            // Cannot happen.
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        // Read the order of the referenced block
        OrderRecord order = blockStore.getOrder(info.getOrderBlockHash(), Sha256Hash.ZERO_HASH);

        // Build transaction returning the spent tokens
        Transaction tx = new Transaction(networkParameters);
        tx.addOutput(Coin.valueOf(order.getOfferValue(), order.getOfferTokenid()),
                ECKey.fromPublicOnly(order.getBeneficiaryPubKey()));

        // The input does not really need to be a valid signature, as long
        // as it has the right general form and is slightly different for
        // different tx
        TransactionInput input = new TransactionInput(networkParameters, tx, Script
                .createInputScript(block.getPrevBlockHash().getBytes(), block.getPrevBranchBlockHash().getBytes()));
        tx.addInput(input);

        return tx;
    }

    /**
     * Deterministically creates a mining reward transaction based on the
     * previous blocks and previous reward transaction. DOES NOT CHECK FOR
     * SOLIDITY. You have to ensure that the approved blocks result in an
     * eligible reward block.
     * 
     * @return mining reward transaction
     * @throws BlockStoreException
     */
    public Transaction generateVirtualMiningRewardTX(Block block) throws BlockStoreException {

        Sha256Hash prevRewardHash = null;
        try {
            RewardInfo rewardInfo = RewardInfo.parse(block.getTransactions().get(0).getData());
            prevRewardHash = rewardInfo.getPrevRewardHash();
        } catch (IOException e) {
            // Cannot happen since checked before
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        // Count how many blocks from miners in the reward interval are approved
        // and build rewards
        Queue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());

        BlockWrap prevTrunkBlock = blockStore.getBlockWrap(block.getPrevBlockHash());
        BlockWrap prevBranchBlock = blockStore.getBlockWrap(block.getPrevBranchBlockHash());
        blockQueue.add(prevTrunkBlock);
        blockQueue.add(prevBranchBlock);

        // Read previous reward block's data
        BlockWrap prevRewardBlock = blockStore.getBlockWrap(prevRewardHash);
        long prevToHeight = 0;
        try {
            RewardInfo rewardInfo = RewardInfo.parse(prevRewardBlock.getBlock().getTransactions().get(0).getData());
            prevToHeight = rewardInfo.getToHeight();
        } catch (IOException e) {
            // Cannot happen since checked before
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        long fromHeight = prevToHeight + 1;
        long toHeight = Math.max(prevTrunkBlock.getBlockEvaluation().getHeight(),
                prevBranchBlock.getBlockEvaluation().getHeight()) - NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE;

        // Initialize
        Set<BlockWrap> currentHeightBlocks = new HashSet<>();
        Map<BlockWrap, Set<Sha256Hash>> snapshotWeights = new HashMap<>();
        Map<Address, Long> finalRewardCount = new HashMap<>();
        BlockWrap currentBlock = null, approvedBlock = null;
        Address consensusBlockMiner = new Address(networkParameters, block.getMinerAddress());
        long currentHeight = Long.MAX_VALUE;
        long totalRewardCount = 0;

        for (BlockWrap tip : blockQueue) {
            snapshotWeights.put(tip, new HashSet<>());
        }

        // Go backwards by height
        while ((currentBlock = blockQueue.poll()) != null) {

            // If we have reached a new height level, try trigger payout
            // calculation
            if (currentHeight > currentBlock.getBlockEvaluation().getHeight()) {

                // Calculate rewards if in reward interval height
                if (currentHeight <= toHeight) {
                    totalRewardCount = calculateHeightRewards(currentHeightBlocks, snapshotWeights, finalRewardCount,
                            totalRewardCount);
                }

                // Finished with this height level, go to next level
                currentHeightBlocks.clear();
                long currentHeightTmp = currentHeight;
                snapshotWeights.entrySet()
                        .removeIf(e -> e.getKey().getBlockEvaluation().getHeight() == currentHeightTmp);
                currentHeight = currentBlock.getBlockEvaluation().getHeight();
            }

            // Stop criterion: Block height lower than approved interval height
            if (currentHeight < fromHeight)
                continue;

            // Add your own hash to approver hashes of current approver hashes
            snapshotWeights.get(currentBlock).add(currentBlock.getBlockHash());

            // If in relevant reward height interval, count it
            if (currentHeight <= toHeight) {
                // Count the blocks of current height
                currentHeightBlocks.add(currentBlock);
            }

            // Continue with both approved blocks
            approvedBlock = blockStore.getBlockWrap(currentBlock.getBlock().getPrevBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                    snapshotWeights.put(approvedBlock, new HashSet<>(snapshotWeights.get(currentBlock)));
                }
            } else {
                snapshotWeights.get(approvedBlock).add(currentBlock.getBlockHash());
            }
            approvedBlock = blockStore.getBlockWrap(currentBlock.getBlock().getPrevBranchBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                    snapshotWeights.put(approvedBlock, new HashSet<>(snapshotWeights.get(currentBlock)));
                }
            } else {
                snapshotWeights.get(approvedBlock).add(currentBlock.getBlockHash());
            }
        }

        // Exception for height 0 (genesis): since prevblock does not exist,
        // finish payout
        // calculation
        if (currentHeight == 0) {
            totalRewardCount = calculateHeightRewards(currentHeightBlocks, snapshotWeights, finalRewardCount,
                    totalRewardCount);
        }

        // Build transaction outputs sorted by addresses
        Transaction tx = new Transaction(networkParameters);
        
        // Reward the consensus block with the static reward
        tx.addOutput(Coin.SATOSHI.times(NetworkParameters.CONSENSUS_BLOCK_REWARD), consensusBlockMiner);

        // Reward twice: once the consensus block, once the normal block maker of good quantiles
        for (Entry<Address, Long> entry : finalRewardCount.entrySet().stream()
                .sorted(Comparator.comparing((Entry<Address, Long> e) -> e.getKey())).collect(Collectors.toList())) {
            tx.addOutput(Coin.SATOSHI.times(entry.getValue() * NetworkParameters.PER_BLOCK_REWARD), consensusBlockMiner);
            tx.addOutput(Coin.SATOSHI.times(entry.getValue() * NetworkParameters.PER_BLOCK_REWARD), entry.getKey());
        }

        // The input does not really need to be a valid signature, as long
        // as it has the right general form and is slightly different for
        // different tx
        TransactionInput input = new TransactionInput(networkParameters, tx, Script.createInputScript(
                prevTrunkBlock.getBlockHash().getBytes(), prevBranchBlock.getBlockHash().getBytes()));
        tx.addInput(input);

        return tx;
    }

    // For each height, throw away anything below the 99-percentile
    // in terms of reduced weight
    private long calculateHeightRewards(Set<BlockWrap> currentHeightBlocks,
            Map<BlockWrap, Set<Sha256Hash>> snapshotWeights, Map<Address, Long> finalRewardCount,
            long totalRewardCount) {
        long heightRewardCount = (long) Math.ceil(0.95d * currentHeightBlocks.size());
        totalRewardCount += heightRewardCount;

        long rewarded = 0;
        for (BlockWrap rewardedBlock : currentHeightBlocks.stream()
                .sorted(Comparator.comparingLong(b -> snapshotWeights.get(b).size()).reversed())
                .collect(Collectors.toList())) {
            if (rewarded >= heightRewardCount)
                break;

            Address miner = new Address(networkParameters, rewardedBlock.getBlock().getMinerAddress());
            if (!finalRewardCount.containsKey(miner))
                finalRewardCount.put(miner, 1L);
            else
                finalRewardCount.put(miner, finalRewardCount.get(miner) + 1);
            rewarded++;
        }
        return totalRewardCount;
    }
}

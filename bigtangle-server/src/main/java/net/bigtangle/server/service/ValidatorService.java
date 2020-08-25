/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ContractEventInfo;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderCancelInfo;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.DifficultyConsensusInheritanceException;
import net.bigtangle.core.exception.VerificationException.GenesisBlockDisallowedException;
import net.bigtangle.core.exception.VerificationException.IncorrectTransactionCountException;
import net.bigtangle.core.exception.VerificationException.InsufficientSignaturesException;
import net.bigtangle.core.exception.VerificationException.InvalidDependencyException;
import net.bigtangle.core.exception.VerificationException.InvalidOrderException;
import net.bigtangle.core.exception.VerificationException.InvalidSignatureException;
import net.bigtangle.core.exception.VerificationException.InvalidTokenOutputException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionDataException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionException;
import net.bigtangle.core.exception.VerificationException.MalformedTransactionDataException;
import net.bigtangle.core.exception.VerificationException.MissingDependencyException;
import net.bigtangle.core.exception.VerificationException.MissingSignatureException;
import net.bigtangle.core.exception.VerificationException.MissingTransactionDataException;
import net.bigtangle.core.exception.VerificationException.NotCoinbaseException;
import net.bigtangle.core.exception.VerificationException.PreviousTokenDisallowsException;
import net.bigtangle.core.exception.VerificationException.SigOpsException;
import net.bigtangle.core.exception.VerificationException.TimeReversionException;
import net.bigtangle.core.exception.VerificationException.TransactionOutputsDisallowedException;
import net.bigtangle.core.exception.VerificationException.UnsolidException;
import net.bigtangle.core.response.MultiSignByRequest;
import net.bigtangle.script.Script;
import net.bigtangle.script.Script.VerifyFlag;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.core.ConflictCandidate;
import net.bigtangle.server.data.SolidityState;
import net.bigtangle.server.data.SolidityState.State;
import net.bigtangle.store.FullBlockGraph;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.utils.ContextPropagatingThreadFactory;
import net.bigtangle.utils.Json;

@Service
public class ValidatorService {

    @Autowired
    protected FullBlockGraph blockGraph;

    @Autowired
    private BlockService blockService;
    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    protected TokenDomainnameService tokenDomainnameService;
    @Autowired
    protected RewardService rewardService;
    @Autowired
    private NetworkParameters params;

    private static final Logger logger = LoggerFactory.getLogger(ValidatorService.class);

    ExecutorService scriptVerificationExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(), new ContextPropagatingThreadFactory("Script verification"));

    /**
     * A job submitted to the executor which verifies signatures.
     */
    private static class Verifier implements Callable<VerificationException> {
        final Transaction tx;
        final List<Script> prevOutScripts;
        final Set<VerifyFlag> verifyFlags;

        public Verifier(final Transaction tx, final List<Script> prevOutScripts, final Set<VerifyFlag> verifyFlags) {
            this.tx = tx;
            this.prevOutScripts = prevOutScripts;
            this.verifyFlags = verifyFlags;
        }

        @Nullable
        @Override
        public VerificationException call() throws Exception {
            try {
                ListIterator<Script> prevOutIt = prevOutScripts.listIterator();
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    tx.getInputs().get(index).getScriptSig().correctlySpends(tx, index, prevOutIt.next(), verifyFlags);
                }
            } catch (VerificationException e) {
                return e;
            }
            return null;
        }
    }

    public static class RewardBuilderResult {
        Transaction tx;
        long difficulty;

        public RewardBuilderResult(Transaction tx, long difficulty) {
            this.tx = tx;
            this.difficulty = difficulty;
        }

        public Transaction getTx() {
            return tx;
        }

        public long getDifficulty() {
            return difficulty;
        }
    }

    /**
     * Checks if the given set is eligible to be walked to during local approval
     * tip selection given the current set of non-confirmed blocks to include.
     * This is the case if the set is compatible with the current milestone. It
     * must disallow spent prev UTXOs / unconfirmed prev UTXOs
     * 
     * @param currentApprovedUnconfirmedBlocks
     *            The set of all currently approved unconfirmed blocks.
     * @return true if the given set is eligible
     * @throws BlockStoreException
     */
    public boolean isEligibleForApprovalSelection(HashSet<BlockWrap> currentApprovedUnconfirmedBlocks,
            FullBlockStore store) throws BlockStoreException {
        // Currently ineligible blocks are not ineligible. If we find one, we
        // must stop
        if (!findWhereCurrentlyIneligible(currentApprovedUnconfirmedBlocks).isEmpty())
            return false;

        // If there exists a new block whose dependency is already spent
        // or not confirmed yet, we fail to approve this block since the
        // current set of confirmed blocks takes precedence
        if (findBlockWithSpentOrUnconfirmedInputs(currentApprovedUnconfirmedBlocks, store))
            return false;

        // If conflicts among the approved blocks exist, cannot approve
        HashSet<ConflictCandidate> conflictingOutPoints = new HashSet<>();
        findCandidateConflicts(currentApprovedUnconfirmedBlocks, conflictingOutPoints);
        if (!conflictingOutPoints.isEmpty())
            return false;

        // Otherwise, the new approved block set is compatible with current
        // confirmation set
        return true;
    }

    /**
     * Checks if the given block is eligible to be walked to during local
     * approval tip selection given the current set of unconfirmed blocks to
     * include. This is the case if the block + the set is compatible with the
     * current confirmeds. It must disallow spent prev UTXOs / unconfirmed prev
     * UTXOs or unsolid blocks.
     * 
     * @param block
     *            The block to check for eligibility.
     * @param currentApprovedUnconfirmedBlocks
     *            The set of all currently approved unconfirmed blocks.
     * @return true if the given block is eligible to be walked to during
     *         approval tip selection.
     * @throws BlockStoreException
     */
    public boolean isEligibleForApprovalSelection(BlockWrap block, HashSet<BlockWrap> currentApprovedUnconfirmedBlocks,
            long cutoffHeight, long maxHeight, FullBlockStore store) throws BlockStoreException {
        // Any confirmed blocks are always compatible with the current
        // confirmeds
        if (block.getBlockEvaluation().isConfirmed())
            return true;

        // Unchecked blocks are not allowed
        if (block.getBlockEvaluation().getSolid() < 2)
            return false;

        // Above maxHeight is not allowed
        if (block.getBlockEvaluation().getHeight() > maxHeight)
            return false;

        // Get sets of all / all new unconfirmed blocks when approving the
        // specified block in combination with the currently included blocks
        @SuppressWarnings("unchecked")
        HashSet<BlockWrap> allApprovedUnconfirmedBlocks = (HashSet<BlockWrap>) currentApprovedUnconfirmedBlocks.clone();
        try {
            if (!blockService.addRequiredUnconfirmedBlocksTo(allApprovedUnconfirmedBlocks, block, cutoffHeight, store))
                throw new RuntimeException("Shouldn't happen: Block is solid but missing predecessors. ");
        } catch (VerificationException e) {
            return false;
        }

        // If this set of blocks is eligible, all is fine
        return isEligibleForApprovalSelection(allApprovedUnconfirmedBlocks, store);
    }

    public boolean hasSpentDependencies(ConflictCandidate c, FullBlockStore store) throws BlockStoreException {
        switch (c.getConflictPoint().getType()) {
        case TXOUT:
            return blockService.getUTXOSpent(c.getConflictPoint().getConnectedOutpoint(), store);
        case TOKENISSUANCE:
            final Token connectedToken = c.getConflictPoint().getConnectedToken();

            // Initial issuances are allowed iff no other same token issuances
            // are confirmed, i.e. spent iff any token confirmed
            if (connectedToken.getTokenindex() == 0)
                return store.getTokenAnyConfirmed(connectedToken.getTokenid(), connectedToken.getTokenindex());
            else
                return store.getTokenSpent(connectedToken.getPrevblockhash());
        case REWARDISSUANCE:
            return store.getRewardSpent(c.getConflictPoint().getConnectedReward().getPrevRewardHash());
        case DOMAINISSUANCE:
            final Token connectedDomainToken = c.getConflictPoint().getConnectedDomainToken();
            return store.getDomainIssuingConfirmedBlock(connectedDomainToken.getTokenname(),
                    connectedDomainToken.getDomainNameBlockHash(), connectedDomainToken.getTokenindex()) != null;
        default:
            throw new RuntimeException("Not Implemented");
        }
    }

    public boolean hasConfirmedDependencies(ConflictCandidate c, FullBlockStore store)
            throws BlockStoreException {
        switch (c.getConflictPoint().getType()) {
        case TXOUT:
            return blockService.getUTXOConfirmed(c.getConflictPoint().getConnectedOutpoint(), store);
        case TOKENISSUANCE:
            final Token connectedToken = c.getConflictPoint().getConnectedToken();

            // Initial issuances are allowed (although they may be spent
            // already)
            if (connectedToken.getTokenindex() == 0)
                return true;
            else
                return store.getTokenConfirmed(connectedToken.getPrevblockhash());
        case REWARDISSUANCE:
            return store.getRewardConfirmed(c.getConflictPoint().getConnectedReward().getPrevRewardHash());
        case DOMAINISSUANCE:
            final Token connectedDomainToken = c.getConflictPoint().getConnectedDomainToken();
            return store.getTokenConfirmed(Sha256Hash.wrap(connectedDomainToken.getDomainNameBlockHash()));
        default:
            throw new RuntimeException("not implemented");
        }
    }

    private boolean findBlockWithSpentOrUnconfirmedInputs(HashSet<BlockWrap> blocks, FullBlockStore store) {
        // Get all conflict candidates in blocks
        Stream<ConflictCandidate> candidates = blocks.stream().map(b -> b.toConflictCandidates())
                .flatMap(i -> i.stream());

        // Find conflict candidates whose used outputs are already spent or
        // still unconfirmed
        return candidates.filter((ConflictCandidate c) -> {
            try {
                return hasSpentDependencies(c, store) || !hasConfirmedDependencies(c, store);
            } catch (BlockStoreException e) {
                e.printStackTrace();
            }
            return false;
        }).findFirst().isPresent();
    }

    /**
     * Resolves all conflicts such that the confirmed set is compatible with all
     * blocks remaining in the set of blocks.
     * 
     * @param blocksToAdd
     *            the set of blocks to add to the current milestone
     * @param cutoffHeight
     * @throws BlockStoreException
     */
    public void resolveAllConflicts(TreeSet<BlockWrap> blocksToAdd, long cutoffHeight, FullBlockStore store)
            throws BlockStoreException {
        // Cutoff: Remove if predecessors neither in milestone nor to be
        // confirmed
        removeWhereUnconfirmedRequirements(blocksToAdd, store);

        // Remove ineligible blocks, i.e. only reward blocks
        // since they follow a different logic
        removeWhereIneligible(blocksToAdd, store);

        // Remove blocks and their approvers that have at least one input
        // with its corresponding output not confirmed yet
        removeWhereUsedOutputsUnconfirmed(blocksToAdd, store);

        // Resolve conflicting block combinations:
        // Disallow conflicts with milestone blocks,
        // i.e. remove those whose input is already spent by such blocks
        resolveMilestoneConflicts(blocksToAdd, store);

        // Then resolve conflicts between non-milestone + new candidates
        resolveTemporaryConflicts(blocksToAdd, cutoffHeight, store);

        // Remove blocks and their approvers that have at least one input
        // with its corresponding output no longer confirmed
        removeWhereUsedOutputsUnconfirmed(blocksToAdd, store);
    }

    /**
     * Remove blocks from blocksToAdd that miss their required predecessors,
     * i.e. the predecessors are not confirmed or in blocksToAdd.
     * 
     * @param blocksToAdd
     * @throws BlockStoreException
     */
    private void removeWhereUnconfirmedRequirements(TreeSet<BlockWrap> blocksToAdd, FullBlockStore store)
            throws BlockStoreException {
        Iterator<BlockWrap> iterator = blocksToAdd.iterator();
        while (iterator.hasNext()) {
            BlockWrap b = iterator.next();
            List<BlockWrap> allRequirements = blockService.getAllRequirements(b.getBlock(), store);
            for (BlockWrap req : allRequirements) {
                if (!req.getBlockEvaluation().isConfirmed() && !blocksToAdd.contains(req)) {
                    iterator.remove();
                    break;
                }
            }
        }
    }

    /**
     * Remove blocks from blocksToAdd that are currently locally ineligible.
     * 
     * @param blocksToAdd
     * @throws BlockStoreException
     */
    public void removeWhereIneligible(Set<BlockWrap> blocksToAdd, FullBlockStore store) {
        findWhereCurrentlyIneligible(blocksToAdd).forEach(b -> {
            try {
                blockService.removeBlockAndApproversFrom(blocksToAdd, b, store);
            } catch (BlockStoreException e) {
                // Cannot happen.
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Find blocks from blocksToAdd that are currently locally ineligible.
     * 
     * @param blocksToAdd
     * @throws BlockStoreException
     */
    private Set<BlockWrap> findWhereCurrentlyIneligible(Set<BlockWrap> blocksToAdd) {
        return blocksToAdd.stream().filter(b -> b.getBlock().getBlockType() == Type.BLOCKTYPE_REWARD)
                .collect(Collectors.toSet());
    }

    /**
     * Remove blocks from blocksToAdd that have at least one used output not
     * confirmed yet. They may however be spent already, since this leads to
     * conflicts.
     * 
     * @param blocksToAdd
     * @throws BlockStoreException
     */
    public void removeWhereUsedOutputsUnconfirmed(Set<BlockWrap> blocksToAdd, FullBlockStore store)
            throws BlockStoreException {
        // Confirmed blocks are always ok
        new HashSet<BlockWrap>(blocksToAdd).stream().filter(b -> !b.getBlockEvaluation().isConfirmed())
                .flatMap(b -> b.toConflictCandidates().stream()).filter(c -> {
                    try {
                        return !hasConfirmedDependencies(c, store); // Any
                                                                    // candidates
                        // where used
                        // dependencies unconfirmed
                    } catch (BlockStoreException e) {
                        // Cannot happen.
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }).forEach(c -> {
                    try {
                        blockService.removeBlockAndApproversFrom(blocksToAdd, c.getBlock(), store);
                    } catch (BlockStoreException e) {
                        // Cannot happen.
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                });
    }

    private void resolveMilestoneConflicts(Set<BlockWrap> blocksToAdd, FullBlockStore store)
            throws BlockStoreException {
        // Find all conflict candidates in blocks to add
        List<ConflictCandidate> conflicts = blocksToAdd.stream().map(b -> b.toConflictCandidates())
                .flatMap(i -> i.stream()).collect(Collectors.toList());

        // Find only those that are spent
        filterSpent(conflicts, store);

        // Drop any spent by milestone
        for (ConflictCandidate c : conflicts) {
            // Find the spending block we are competing with
            BlockWrap milestoneBlock = getSpendingBlock(c, store);

            // If it is pruned or a milestone, we drop the blocks
            if (milestoneBlock == null || milestoneBlock.getBlockEvaluation().getMilestone() != -1) {
                blockService.removeBlockAndApproversFrom(blocksToAdd, c.getBlock(), store);
            }
        }
    }

    /**
     * Resolves conflicts between non-milestone blocks and candidates
     * 
     * @param blocksToAdd
     * @param cutoffHeight
     * @throws BlockStoreException
     */
    private void resolveTemporaryConflicts(Set<BlockWrap> blocksToAdd, long cutoffHeight, FullBlockStore store)
            throws BlockStoreException {
        HashSet<ConflictCandidate> conflictingOutPoints = new HashSet<ConflictCandidate>();
        HashSet<BlockWrap> conflictingConfirmedBlocks = new HashSet<BlockWrap>();

        // Find all conflicts in the new blocks + confirmed blocks
        findFixableConflicts(blocksToAdd, conflictingOutPoints, conflictingConfirmedBlocks, store);

        // Resolve all conflicts by grouping by UTXO ordered by descending
        // rating
        HashSet<BlockWrap> losingBlocks = resolveTemporaryConflicts(conflictingOutPoints, blocksToAdd, cutoffHeight,
                store);

        // For confirmed blocks that have been eliminated call disconnect
        // procedure
        HashSet<Sha256Hash> traversedUnconfirms = new HashSet<>();
        for (BlockWrap b : conflictingConfirmedBlocks.stream().filter(b -> losingBlocks.contains(b))
                .collect(Collectors.toList())) {
            blockGraph.unconfirmRecursive(b.getBlockEvaluation().getBlockHash(), traversedUnconfirms,store);
        }

        // For candidates that have been eliminated (conflictingOutPoints in
        // blocksToAdd \ winningBlocks) remove them from blocksToAdd
        for (BlockWrap b : losingBlocks) {
            blockService.removeBlockAndApproversFrom(blocksToAdd, b, store);
        }
    }

    /**
     * Resolve all conflicts by grouping by UTXO ordered by descending rating.
     * 
     * @param conflictingOutPoints
     * @return losingBlocks: blocks that have been removed due to conflict
     *         resolution
     * @throws BlockStoreException
     */
    private HashSet<BlockWrap> resolveTemporaryConflicts(Set<ConflictCandidate> conflictingOutPoints,
            Set<BlockWrap> blocksToAdd, long cutoffHeight, FullBlockStore store) throws BlockStoreException {
        // Initialize blocks that will/will not survive the conflict resolution
        HashSet<BlockWrap> initialBlocks = conflictingOutPoints.stream().map(c -> c.getBlock())
                .collect(Collectors.toCollection(HashSet::new));
        HashSet<BlockWrap> winningBlocks = new HashSet<>(blocksToAdd);
        for (BlockWrap winningBlock : initialBlocks) {
            if (!blockService.addRequiredUnconfirmedBlocksTo(winningBlocks, winningBlock, cutoffHeight, store))
                throw new RuntimeException("Shouldn't happen: Block is solid but missing predecessors. ");
            blockService.addConfirmedApproversTo(winningBlocks, winningBlock, store);
        }
        HashSet<BlockWrap> losingBlocks = new HashSet<>(winningBlocks);

        // Sort conflicts internally by descending rating, then cumulative
        // weight.
        Comparator<ConflictCandidate> byDescendingRating = getConflictComparator()
                .thenComparingLong((ConflictCandidate e) -> e.getBlock().getMcmc().getRating())
                .thenComparingLong((ConflictCandidate e) -> e.getBlock().getMcmc().getCumulativeWeight())
                .thenComparingLong((ConflictCandidate e) -> -e.getBlock().getBlockEvaluation().getInsertTime())
                .thenComparing((ConflictCandidate e) -> e.getBlock().getBlockEvaluation().getBlockHash()).reversed();

        Supplier<TreeSet<ConflictCandidate>> conflictTreeSetSupplier = () -> new TreeSet<ConflictCandidate>(
                byDescendingRating);

        Map<Object, TreeSet<ConflictCandidate>> conflicts = conflictingOutPoints.stream().collect(
                Collectors.groupingBy(i -> i.getConflictPoint(), Collectors.toCollection(conflictTreeSetSupplier)));

        // Sort conflicts among each other by descending max(rating).
        Comparator<TreeSet<ConflictCandidate>> byDescendingSetRating = getConflictSetComparator()
                .thenComparingLong(
                        (TreeSet<ConflictCandidate> s) -> s.first().getBlock().getMcmc().getRating())
                .thenComparingLong((TreeSet<ConflictCandidate> s) -> s.first().getBlock().getMcmc()
                        .getCumulativeWeight())
                .thenComparingLong(
                        (TreeSet<ConflictCandidate> s) -> -s.first().getBlock().getBlockEvaluation().getInsertTime())
                .thenComparing(
                        (TreeSet<ConflictCandidate> s) -> s.first().getBlock().getBlockEvaluation().getBlockHash())
                .reversed();

        Supplier<TreeSet<TreeSet<ConflictCandidate>>> conflictsTreeSetSupplier = () -> new TreeSet<TreeSet<ConflictCandidate>>(
                byDescendingSetRating);

        TreeSet<TreeSet<ConflictCandidate>> sortedConflicts = conflicts.values().stream()
                .collect(Collectors.toCollection(conflictsTreeSetSupplier));

        // Now handle conflicts by descending max(rating)
        for (TreeSet<ConflictCandidate> conflict : sortedConflicts) {
            // Take the block with the maximum rating in this conflict that is
            // still in winning Blocks
            ConflictCandidate maxRatingPair = null;
            for (ConflictCandidate c : conflict) {
                if (winningBlocks.contains(c.getBlock())) {
                    maxRatingPair = c;
                    break;
                }
            }

            // If such a block exists, this conflict is resolved by eliminating
            // all other blocks in this conflict from winning blocks
            if (maxRatingPair != null) {
                for (ConflictCandidate c : conflict) {
                    if (c != maxRatingPair) {
                        blockService.removeBlockAndApproversFrom(winningBlocks, c.getBlock(), store);
                    }
                }
            }
        }

        losingBlocks.removeAll(winningBlocks);

        return losingBlocks;
    }

    private Comparator<TreeSet<ConflictCandidate>> getConflictSetComparator() {
        return new Comparator<TreeSet<ConflictCandidate>>() {
            @Override
            public int compare(TreeSet<ConflictCandidate> o1, TreeSet<ConflictCandidate> o2) {
                return 0;
            }
        };
    }

    private Comparator<ConflictCandidate> getConflictComparator() {
        return new Comparator<ConflictCandidate>() {
            @Override
            public int compare(ConflictCandidate o1, ConflictCandidate o2) {
                return 0;
            }
        };
    }

    /**
     * Finds conflicts in blocksToAdd itself and with the confirmed blocks.
     * 
     * @param blocksToAdd
     * @param conflictingOutPoints
     * @throws BlockStoreException
     */
    private void findFixableConflicts(Set<BlockWrap> blocksToAdd, Set<ConflictCandidate> conflictingOutPoints,
            Set<BlockWrap> conflictingConfirmedBlocks, FullBlockStore store) throws BlockStoreException {

        findUndoableConflicts(blocksToAdd, conflictingOutPoints, conflictingConfirmedBlocks, store);
        findCandidateConflicts(blocksToAdd, conflictingOutPoints);
    }

    /**
     * Finds conflicts among blocks to add themselves
     * 
     * @param blocksToAdd
     * @param conflictingOutPoints
     * @throws BlockStoreException
     */
    private void findCandidateConflicts(Set<BlockWrap> blocksToAdd, Set<ConflictCandidate> conflictingOutPoints)
            throws BlockStoreException {
        // Get conflicts that are spent more than once in the
        // candidates
        List<ConflictCandidate> candidateCandidateConflicts = blocksToAdd.stream().map(b -> b.toConflictCandidates())
                .flatMap(i -> i.stream()).collect(Collectors.groupingBy(i -> i.getConflictPoint())).values().stream()
                .filter(l -> l.size() > 1).flatMap(l -> l.stream()).collect(Collectors.toList());

        // Add the conflicting candidates
        for (ConflictCandidate c : candidateCandidateConflicts) {
            conflictingOutPoints.add(c);
        }
    }

    /**
     * Finds conflicts between current confirmed and blocksToAdd
     * 
     * @param blocksToAdd
     * @param conflictingOutPoints
     * @throws BlockStoreException
     */
    private void findUndoableConflicts(Set<BlockWrap> blocksToAdd, Set<ConflictCandidate> conflictingOutPoints,
            Set<BlockWrap> conflictingConfirmedBlocks, FullBlockStore store) throws BlockStoreException {
        // Find all conflict candidates in blocks to add
        List<ConflictCandidate> conflicts = blocksToAdd.stream().map(b -> b.toConflictCandidates())
                .flatMap(i -> i.stream()).collect(Collectors.toList());

        // Find only those that are spent in confirmed
        filterSpent(conflicts, store);

        // Add the conflicting candidates and confirmed blocks to given set
        for (ConflictCandidate c : conflicts) {
            // Find the spending block we are competing with
            BlockWrap confirmedBlock = getSpendingBlock(c, store);

            // Only go through if the block is undoable, i.e. not milestone
            if (confirmedBlock == null || confirmedBlock.getBlockEvaluation().getMilestone() != -1)
                continue;

            // Add confirmed block
            conflictingOutPoints.add(ConflictCandidate.fromConflictPoint(confirmedBlock, c.getConflictPoint()));
            conflictingConfirmedBlocks.add(confirmedBlock);

            // Then add corresponding new block
            conflictingOutPoints.add(c);
        }
    }

    // Returns null if no spending block found
    private BlockWrap getSpendingBlock(ConflictCandidate c, FullBlockStore store) throws BlockStoreException {
        switch (c.getConflictPoint().getType()) {
        case TXOUT:
            final BlockEvaluation utxoSpender = blockService.getUTXOSpender(c.getConflictPoint().getConnectedOutpoint(),
                    store);
            if (utxoSpender == null)
                return null;
            return store.getBlockWrap(utxoSpender.getBlockHash());
        case TOKENISSUANCE:
            final Token connectedToken = c.getConflictPoint().getConnectedToken();

            // The spender is always the one block with the same tokenid and
            // index that is confirmed
            return store.getTokenIssuingConfirmedBlock(connectedToken.getTokenid(), connectedToken.getTokenindex());
        case REWARDISSUANCE:
            final Sha256Hash txRewardSpender = store
                    .getRewardSpender(c.getConflictPoint().getConnectedReward().getPrevRewardHash());
            if (txRewardSpender == null)
                return null;
            return store.getBlockWrap(txRewardSpender);
        case DOMAINISSUANCE:
            final Token connectedDomainToken = c.getConflictPoint().getConnectedDomainToken();

            // The spender is always the one block with the same domainname and
            // predecessing domain tokenid that is confirmed
            return store.getDomainIssuingConfirmedBlock(connectedDomainToken.getTokenname(),
                    connectedDomainToken.getDomainNameBlockHash(), connectedDomainToken.getTokenindex());
        default:
            throw new RuntimeException("No Implementation");
        }
    }

    private void filterSpent(Collection<ConflictCandidate> blockConflicts, FullBlockStore store) {
        blockConflicts.removeIf(c -> {
            try {
                return !hasSpentDependencies(c, store);
            } catch (BlockStoreException e) {
                e.printStackTrace();
                return true;
            }
        });
    }

    private SolidityState checkPredecessorsExistAndOk(Block block, boolean throwExceptions, FullBlockStore store)
            throws BlockStoreException {
        final Set<Sha256Hash> allPredecessorBlockHashes = blockService.getAllRequiredBlockHashes(block);
        for (Sha256Hash predecessorReq : allPredecessorBlockHashes) {
            final BlockWrap pred = store.getBlockWrap(predecessorReq);
            if (pred == null)
                return SolidityState.from(predecessorReq, true);
            if (pred.getBlock().getBlockType().requiresCalculation() && pred.getBlockEvaluation().getSolid() != 2)
                return SolidityState.fromMissingCalculation(predecessorReq);
            if (pred.getBlock().getHeight() >= block.getHeight()) {
                if (throwExceptions)
                    throw new VerificationException("Height of used blocks must be lower than height of this block.");
                return SolidityState.getFailState();
            }
        }
        return SolidityState.getSuccessState();
    }

    public SolidityState getMinPredecessorSolidity(Block block, boolean throwExceptions, FullBlockStore store)
            throws BlockStoreException {
        final List<BlockWrap> allPredecessors = blockService.getAllRequirements(block, store);
        SolidityState missingCalculation = null;
        SolidityState missingDependency = null;
        for (BlockWrap predecessor : allPredecessors) {
            if (predecessor.getBlockEvaluation().getSolid() == 2) {
                continue;
            } else if (predecessor.getBlockEvaluation().getSolid() == 1) {
                missingCalculation = SolidityState.fromMissingCalculation(predecessor.getBlockHash());
            } else if (predecessor.getBlockEvaluation().getSolid() == 0) {
                missingDependency = SolidityState.from(predecessor.getBlockHash(), false);
            } else if (predecessor.getBlockEvaluation().getSolid() == -1) {
                if (throwExceptions)
                    throw new VerificationException("The used blocks are invalid.");
                return SolidityState.getFailState();
            } else {
                throw new RuntimeException("not implemented");
            }
        }

        if (missingDependency == null) {
            if (missingCalculation == null) {
                return SolidityState.getSuccessState();
            } else {
                return missingCalculation;
            }
        } else {
            return missingDependency;
        }
    }

    /*
     * Checks if the block is formally correct without relying on predecessors
     */
    public SolidityState checkFormalBlockSolidity(Block block, boolean throwExceptions) {
        try {
            if (block.getHash() == Sha256Hash.ZERO_HASH) {
                if (throwExceptions)
                    throw new VerificationException("Lucky zeros not allowed");
                return SolidityState.getFailState();
            }

            if (block.getBlockType() == Block.Type.BLOCKTYPE_INITIAL) {
                if (throwExceptions)
                    throw new GenesisBlockDisallowedException();
                return SolidityState.getFailState();
            }

            // Disallow someone burning other people's orders
            if (block.getBlockType() != Type.BLOCKTYPE_ORDER_OPEN) {
                for (Transaction tx : block.getTransactions())
                    if (tx.getDataClassName() != null && tx.getDataClassName().equals("OrderOpen")) {
                        if (throwExceptions)
                            throw new MalformedTransactionDataException();
                        return SolidityState.getFailState();
                    }
            }

            // Check transaction solidity
            SolidityState transactionalSolidityState = checkFormalTransactionalSolidity(block, throwExceptions);
            if (!(transactionalSolidityState.getState() == State.Success)) {
                return transactionalSolidityState;
            }

            // Check type-specific solidity
            SolidityState typeSpecificSolidityState = checkFormalTypeSpecificSolidity(block, throwExceptions);
            if (!(typeSpecificSolidityState.getState() == State.Success)) {
                return typeSpecificSolidityState;
            }

            return SolidityState.getSuccessState();
        } catch (VerificationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unhandled exception in checkSolidity: ", e);
            if (throwExceptions)
                throw new VerificationException(e);
            return SolidityState.getFailState();
        }
    }

    private SolidityState checkFormalTransactionalSolidity(Block block, boolean throwExceptions)
            throws BlockStoreException {
        try {
            long sigOps = 0;

            for (Transaction tx : block.getTransactions()) {
                sigOps += tx.getSigOpCount();
            }

            for (final Transaction tx : block.getTransactions()) {
                Map<String, Coin> valueOut = new HashMap<String, Coin>();
                for (TransactionOutput out : tx.getOutputs()) {
                    if (valueOut.containsKey(Utils.HEX.encode(out.getValue().getTokenid()))) {
                        valueOut.put(Utils.HEX.encode(out.getValue().getTokenid()),
                                valueOut.get(Utils.HEX.encode(out.getValue().getTokenid())).add(out.getValue()));
                    } else {
                        valueOut.put(Utils.HEX.encode(out.getValue().getTokenid()), out.getValue());
                    }
                }
                if (!checkTxOutputSigns(valueOut)) {
                    throw new InvalidTransactionException("Transaction output value negative");
                }

                final Set<VerifyFlag> verifyFlags = params.getTransactionVerificationFlags(block, tx);
                if (verifyFlags.contains(VerifyFlag.P2SH)) {
                    if (sigOps > NetworkParameters.MAX_BLOCK_SIGOPS)
                        throw new SigOpsException();
                }
            }

        } catch (VerificationException e) {
            scriptVerificationExecutor.shutdownNow();
            logger.info("", e);
            if (throwExceptions)
                throw e;
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkFormalTypeSpecificSolidity(Block block, boolean throwExceptions)
            throws BlockStoreException {
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
            // Check rewards are solid
            SolidityState rewardSolidityState = checkFormalRewardSolidity(block, throwExceptions);
            if (!(rewardSolidityState.getState() == State.Success)) {
                return rewardSolidityState;
            }

            break;
        case BLOCKTYPE_TOKEN_CREATION:
            // Check token issuances are solid
            SolidityState tokenSolidityState = checkFormalTokenSolidity(block, throwExceptions);
            if (!(tokenSolidityState.getState() == State.Success)) {
                return tokenSolidityState;
            }

            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_CONTRACT_EXECUTE:
            break;
        case BLOCKTYPE_ORDER_OPEN:
            SolidityState openSolidityState = checkFormalOrderOpenSolidity(block, throwExceptions);
            if (!(openSolidityState.getState() == State.Success)) {
                return openSolidityState;
            }
            break;
        case BLOCKTYPE_ORDER_CANCEL:
            SolidityState opSolidityState = checkFormalOrderOpSolidity(block, throwExceptions);
            if (!(opSolidityState.getState() == State.Success)) {
                return opSolidityState;
            }
            break;
        case BLOCKTYPE_CONTRACT_EVENT:
            SolidityState check = checkFormalContractEventSolidity(block, throwExceptions);
            if (!(check.getState() == State.Success)) {
                return check;
            }
            break;
        default:
            throw new RuntimeException("No Implementation");
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkFormalOrderOpenSolidity(Block block, boolean throwExceptions)
            throws BlockStoreException {
        List<Transaction> transactions = block.getTransactions();

        if (transactions.size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        if (transactions.get(0).getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        // Check that the tx has correct data
        OrderOpenInfo orderInfo;
        try {
            orderInfo = new OrderOpenInfo().parse(transactions.get(0).getData());
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        if (!transactions.get(0).getDataClassName().equals("OrderOpen")) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        // NotNull checks
        if (orderInfo.getTargetTokenid() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid target tokenid");
            return SolidityState.getFailState();
        }

        // Check bounds for target coin values
        if (orderInfo.getTargetValue() < 1 || orderInfo.getTargetValue() > Long.MAX_VALUE) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid target long value: " + orderInfo.getTargetValue());
            return SolidityState.getFailState();
        }

        if (orderInfo.getValidToTime() > Math.addExact(orderInfo.getValidFromTime(),
                NetworkParameters.ORDER_TIMEOUT_MAX)) {
            if (throwExceptions)
                throw new InvalidOrderException("The given order's timeout is too long.");
            return SolidityState.getFailState();
        }

        if (!ECKey.fromPublicOnly(orderInfo.getBeneficiaryPubKey()).toAddress(params).toBase58()
                .equals(orderInfo.getBeneficiaryAddress())) {
            if (throwExceptions)
                throw new InvalidOrderException("The address does not match with the given pubkey.");
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkFormalContractEventSolidity(Block block, boolean throwExceptions)
            throws BlockStoreException {
        List<Transaction> transactions = block.getTransactions();

        if (transactions.size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        if (transactions.get(0).getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        // Check that the tx has correct data
        ContractEventInfo contractEventInfo;
        try {
            contractEventInfo = new ContractEventInfo().parse(transactions.get(0).getData());
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        if (!transactions.get(0).getDataClassName().equals("ContractEventInfo")) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        // NotNull checks
        if (contractEventInfo.getTargetTokenid() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid target tokenid");
            return SolidityState.getFailState();
        }

        // NotNull checks
        if (contractEventInfo.getContractTokenid() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid contract tokenid");
            return SolidityState.getFailState();
        }

        // Check bounds for target coin values
        if (contractEventInfo.getTargetValue().signum() < 0) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid target value max: " + Long.MAX_VALUE);
            return SolidityState.getFailState();
        }

        if (contractEventInfo.getValidToTime() > Math.addExact(contractEventInfo.getValidFromTime(),
                NetworkParameters.ORDER_TIMEOUT_MAX)) {
            if (throwExceptions)
                throw new InvalidOrderException("The given order's timeout is too long.");
            return SolidityState.getFailState();
        }

        if (!ECKey.fromPublicOnly(contractEventInfo.getBeneficiaryPubKey()).toAddress(params).toBase58()
                .equals(contractEventInfo.getBeneficiaryAddress())) {
            if (throwExceptions)
                throw new InvalidOrderException("The address does not match with the given pubkey.");
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkFormalOrderOpSolidity(Block block, boolean throwExceptions) throws BlockStoreException {
        if (block.getTransactions().size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        // No output creation
        if (!block.getTransactions().get(0).getOutputs().isEmpty()) {
            if (throwExceptions)
                throw new TransactionOutputsDisallowedException();
            return SolidityState.getFailState();
        }

        Transaction tx = block.getTransactions().get(0);
        if (tx.getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        OrderCancelInfo info = null;
        try {
            info = new OrderCancelInfo().parse(tx.getData());
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        // NotNull checks
        if (info.getBlockHash() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid target txhash");
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkFormalRewardSolidity(Block block, boolean throwExceptions) throws BlockStoreException {
        List<Transaction> transactions = block.getTransactions();

        if (transactions.size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        // No output creation
        if (!transactions.get(0).getOutputs().isEmpty()) {
            if (throwExceptions)
                throw new TransactionOutputsDisallowedException();
            return SolidityState.getFailState();
        }

        if (transactions.get(0).getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        // Check that the tx has correct data
        RewardInfo rewardInfo = new RewardInfo().parseChecked(transactions.get(0).getData());
        // NotNull checks
        if (rewardInfo.getPrevRewardHash() == null) {
            if (throwExceptions)
                throw new MissingDependencyException();
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    public SolidityState checkFormalTokenSolidity(Block block, boolean throwExceptions) throws BlockStoreException {

        if (block.getTransactions().size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        if (!block.getTransactions().get(0).isCoinBase()) {
            if (throwExceptions)
                throw new NotCoinbaseException();
            return SolidityState.getFailState();
        }

        Transaction tx = block.getTransactions().get(0);
        if (tx.getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        TokenInfo currentToken = null;
        try {
            currentToken = new TokenInfo().parse(tx.getData());
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        if (checkFormalTokenFields(throwExceptions, currentToken) == SolidityState.getFailState())
            return SolidityState.getFailState();

        // Check field correctness: amount
        if (!currentToken.getToken().getAmount().equals(block.getTransactions().get(0).getOutputSum())) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Incorrect amount field");
            return SolidityState.getFailState();
        }

        // Check all token issuance transaction outputs are actually of the
        // given token
        for (Transaction tx1 : block.getTransactions()) {
            for (TransactionOutput out : tx1.getOutputs()) {
                if (!out.getValue().getTokenHex().equals(currentToken.getToken().getTokenid())) {
                    if (throwExceptions)
                        throw new InvalidTokenOutputException();
                    return SolidityState.getFailState();
                }
            }
        }

        // Must define enough permissioned addresses
        if (currentToken.getToken().getSignnumber() > currentToken.getMultiSignAddresses().size()) {
            if (throwExceptions)
                throw new InvalidTransactionDataException(
                        "Cannot fulfill required sign number from multisign address list");
            return SolidityState.getFailState();
        }

        // Ensure signatures exist
        if (tx.getDataSignature() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        // Get signatures from transaction
        String jsonStr = new String(tx.getDataSignature());
        try {
            Json.jsonmapper().readValue(jsonStr, MultiSignByRequest.class);
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    public void checkTokenUnique(Block block, FullBlockStore store)
            throws BlockStoreException, JsonParseException, JsonMappingException, IOException {
        /*
         * Token is unique with token name and domain
         */
        TokenInfo currentToken = new TokenInfo().parse(block.getTransactions().get(0).getData());
        if (store.getTokennameAndDomain(currentToken.getToken().getTokenname(),
                currentToken.getToken().getDomainNameBlockHash()) && currentToken.getToken().getTokenindex() == 0) {
            throw new VerificationException(" Token name and domain exists.");
        }
    }

    /*
     * Checks if the block is valid based on itself and its dependencies.
     * Rechecks formal criteria too. If SolidityState.getSuccessState() is
     * returned, the block is valid. If SolidityState.getFailState() is
     * returned, the block is invalid. Otherwise, appropriate solidity states
     * are returned to imply missing dependencies.
     */
    private SolidityState checkFullBlockSolidity(Block block, boolean throwExceptions, FullBlockStore store) {
        try {
            BlockWrap storedPrev = store.getBlockWrap(block.getPrevBlockHash());
            BlockWrap storedPrevBranch = store.getBlockWrap(block.getPrevBranchBlockHash());

            if (block.getHash() == Sha256Hash.ZERO_HASH) {
                if (throwExceptions)
                    throw new VerificationException("Lucky zeros not allowed");
                return SolidityState.getFailState();
            }
            // Check predecessor blocks exist
            if (storedPrev == null) {
                return SolidityState.from(block.getPrevBlockHash(), true);
            }
            if (storedPrevBranch == null) {
                return SolidityState.from(block.getPrevBranchBlockHash(), true);
            }
            if (block.getBlockType() == Block.Type.BLOCKTYPE_INITIAL) {
                if (throwExceptions)
                    throw new GenesisBlockDisallowedException();
                return SolidityState.getFailState();
            }

            // Check height, all required max +1
            if (block.getHeight() != blockService.calcHeightRequiredBlocks(block, store)) {
                if (throwExceptions)
                    throw new VerificationException("Wrong height");
                return SolidityState.getFailState();
            }

            // Disallow someone burning other people's orders
            if (block.getBlockType() != Type.BLOCKTYPE_ORDER_OPEN) {
                for (Transaction tx : block.getTransactions())
                    if (tx.getDataClassName() != null && tx.getDataClassName().equals("OrderOpen")) {
                        if (throwExceptions)
                            throw new MalformedTransactionDataException();
                        return SolidityState.getFailState();
                    }
            }

            // Check timestamp: enforce monotone time increase
            if (block.getTimeSeconds() < storedPrev.getBlock().getTimeSeconds()
                    || block.getTimeSeconds() < storedPrevBranch.getBlock().getTimeSeconds()) {
                if (throwExceptions)
                    throw new TimeReversionException();
                return SolidityState.getFailState();
            }

            // Check difficulty and latest consensus block is passed through
            // correctly
            if (block.getBlockType() != Block.Type.BLOCKTYPE_REWARD) {
                if (storedPrev.getBlock().getLastMiningRewardBlock() >= storedPrevBranch.getBlock()
                        .getLastMiningRewardBlock()) {
                    if (block.getLastMiningRewardBlock() != storedPrev.getBlock().getLastMiningRewardBlock()
                            || block.getDifficultyTarget() != storedPrev.getBlock().getDifficultyTarget()) {
                        if (throwExceptions)
                            throw new DifficultyConsensusInheritanceException();
                        return SolidityState.getFailState();
                    }
                } else {
                    if (block.getLastMiningRewardBlock() != storedPrevBranch.getBlock().getLastMiningRewardBlock()
                            || block.getDifficultyTarget() != storedPrevBranch.getBlock().getDifficultyTarget()) {
                        if (throwExceptions)
                            throw new DifficultyConsensusInheritanceException();
                        return SolidityState.getFailState();
                    }
                }
            }

            // Check transactions are solid
            SolidityState transactionalSolidityState = checkFullTransactionalSolidity(block, block.getHeight(),
                    throwExceptions, store);
            if (!(transactionalSolidityState.getState() == State.Success)) {
                return transactionalSolidityState;
            }

            // Check type-specific solidity
            SolidityState typeSpecificSolidityState = checkFullTypeSpecificSolidity(block, storedPrev, storedPrevBranch,
                    block.getHeight(), throwExceptions, store);
            if (!(typeSpecificSolidityState.getState() == State.Success)) {
                return typeSpecificSolidityState;
            }

            return SolidityState.getSuccessState();
        } catch (VerificationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unhandled exception in checkSolidity: ", e);
            if (throwExceptions)
                throw new VerificationException(e);
            return SolidityState.getFailState();
        }
    }

    private SolidityState checkFullTransactionalSolidity(Block block, long height, boolean throwExceptions,
            FullBlockStore store) throws BlockStoreException {
        List<Transaction> transactions = block.getTransactions();

        // All used transaction outputs must exist
        for (final Transaction tx : transactions) {
            if (!tx.isCoinBase()) {
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    TransactionInput in = tx.getInputs().get(index);
                    UTXO prevOut = store.getTransactionOutput(in.getOutpoint().getBlockHash(),
                            in.getOutpoint().getTxHash(), in.getOutpoint().getIndex());
                    if (prevOut == null) {
                        // Missing previous transaction output
                        return SolidityState.from(in.getOutpoint(), true);
                    }
                }
            }
        }

        // Transaction validation
        try {
            LinkedList<UTXO> txOutsSpent = new LinkedList<UTXO>();
            long sigOps = 0;

            if (scriptVerificationExecutor.isShutdown())
                scriptVerificationExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            List<Future<VerificationException>> listScriptVerificationResults = new ArrayList<Future<VerificationException>>(
                    block.getTransactions().size());

            for (Transaction tx : block.getTransactions()) {
                sigOps += tx.getSigOpCount();
            }

            for (final Transaction tx : block.getTransactions()) {
                boolean isCoinBase = tx.isCoinBase();
                Map<String, Coin> valueIn = new HashMap<String, Coin>();
                Map<String, Coin> valueOut = new HashMap<String, Coin>();

                final List<Script> prevOutScripts = new LinkedList<Script>();
                final Set<VerifyFlag> verifyFlags = params.getTransactionVerificationFlags(block, tx);
                if (!isCoinBase) {
                    for (int index = 0; index < tx.getInputs().size(); index++) {
                        TransactionInput in = tx.getInputs().get(index);
                        UTXO prevOut = store.getTransactionOutput(in.getOutpoint().getBlockHash(),
                                in.getOutpoint().getTxHash(), in.getOutpoint().getIndex());
                        if (prevOut == null) {
                            // Cannot happen due to solidity checks before
                            throw new RuntimeException("Block attempts to spend a not yet existent output!");
                        }

                        if (valueIn.containsKey(Utils.HEX.encode(prevOut.getValue().getTokenid()))) {
                            valueIn.put(Utils.HEX.encode(prevOut.getValue().getTokenid()), valueIn
                                    .get(Utils.HEX.encode(prevOut.getValue().getTokenid())).add(prevOut.getValue()));
                        } else {
                            valueIn.put(Utils.HEX.encode(prevOut.getValue().getTokenid()), prevOut.getValue());

                        }
                        if (verifyFlags.contains(VerifyFlag.P2SH)) {
                            if (prevOut.getScript().isPayToScriptHash())
                                sigOps += Script.getP2SHSigOpCount(in.getScriptBytes());
                            if (sigOps > NetworkParameters.MAX_BLOCK_SIGOPS)
                                throw new SigOpsException();
                        }
                        prevOutScripts.add(prevOut.getScript());
                        txOutsSpent.add(prevOut);
                    }
                }
                // Sha256Hash hash = tx.getHash();
                for (TransactionOutput out : tx.getOutputs()) {
                    if (valueOut.containsKey(Utils.HEX.encode(out.getValue().getTokenid()))) {
                        valueOut.put(Utils.HEX.encode(out.getValue().getTokenid()),
                                valueOut.get(Utils.HEX.encode(out.getValue().getTokenid())).add(out.getValue()));
                    } else {
                        valueOut.put(Utils.HEX.encode(out.getValue().getTokenid()), out.getValue());
                    }
                }
                if (!checkTxOutputSigns(valueOut))
                    throw new InvalidTransactionException("Transaction output value negative");
                if (isCoinBase) {
                    // coinbaseValue = valueOut;
                } else {
                    if (!checkTxInputOutput(valueIn, valueOut))
                        throw new InvalidTransactionException("Transaction input and output values do not match");
                    // totalFees = totalFees.add(valueIn.subtract(valueOut));
                }

                if (!isCoinBase) {
                    // Because correctlySpends modifies transactions, this must
                    // come after we are done with tx
                    FutureTask<VerificationException> future = new FutureTask<VerificationException>(
                            new Verifier(tx, prevOutScripts, verifyFlags));
                    scriptVerificationExecutor.execute(future);
                    listScriptVerificationResults.add(future);
                }
            }
            for (Future<VerificationException> future : listScriptVerificationResults) {
                VerificationException e;
                try {
                    e = future.get();
                } catch (InterruptedException thrownE) {
                    throw new RuntimeException(thrownE); // Shouldn't happen
                } catch (ExecutionException thrownE) {
                    // log.error("Script.correctlySpends threw a non-normal
                    // exception: " ,thrownE );
                    throw new VerificationException(
                            "Bug in Script.correctlySpends, likely script malformed in some new and interesting way.",
                            thrownE);
                }
                if (e != null)
                    throw e;
            }
        } catch (VerificationException e) {
            scriptVerificationExecutor.shutdownNow();
            logger.info("", e);
            if (throwExceptions)
                throw e;
            return SolidityState.getFailState();
        } catch (BlockStoreException e) {
            scriptVerificationExecutor.shutdownNow();
            logger.error("", e);
            if (throwExceptions)
                throw new VerificationException(e);
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    private boolean checkTxOutputSigns(Map<String, Coin> valueOut) {
        for (Map.Entry<String, Coin> entry : valueOut.entrySet()) {
            // System.out.println(entry.getKey() + "/" + entry.getValue());
            if (entry.getValue().signum() < 0) {
                return false;
            }
        }
        return true;
    }

    private boolean checkTxInputOutput(Map<String, Coin> valueInput, Map<String, Coin> valueOut) {
        for (Map.Entry<String, Coin> entry : valueOut.entrySet()) {
            if (!valueInput.containsKey(entry.getKey())) {
                return false;
            } else {
                if (valueInput.get(entry.getKey()).compareTo(entry.getValue()) < 0)
                    return false;
            }
        }
        return true;
    }

    private SolidityState checkFullTypeSpecificSolidity(Block block, BlockWrap storedPrev, BlockWrap storedPrevBranch,
            long height, boolean throwExceptions, FullBlockStore store) throws BlockStoreException {
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
            // Check rewards are solid
            SolidityState rewardSolidityState = checkFullRewardSolidity(block, storedPrev, storedPrevBranch, height,
                    throwExceptions, store);
            if (!(rewardSolidityState.getState() == State.Success)) {
                return rewardSolidityState;
            }

            break;
        case BLOCKTYPE_TOKEN_CREATION:
            // Check token issuances are solid
            SolidityState tokenSolidityState = checkFullTokenSolidity(block, height, throwExceptions, store);
            if (!(tokenSolidityState.getState() == State.Success)) {
                return tokenSolidityState;
            }

            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_CONTRACT_EXECUTE:
            break;
        case BLOCKTYPE_ORDER_OPEN:
            SolidityState openSolidityState = checkFullOrderOpenSolidity(block, height, throwExceptions, store);
            if (!(openSolidityState.getState() == State.Success)) {
                return openSolidityState;
            }
            break;
        case BLOCKTYPE_ORDER_CANCEL:
            SolidityState opSolidityState = checkFullOrderOpSolidity(block, height, throwExceptions, store);
            if (!(opSolidityState.getState() == State.Success)) {
                return opSolidityState;
            }
            break;
        case BLOCKTYPE_CONTRACT_EVENT:
            SolidityState check = checkFullContractEventSolidity(block, height, throwExceptions);
            if (!(check.getState() == State.Success)) {
                return check;
            }
            break;
        default:
            throw new RuntimeException("No Implementation");
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkFullContractEventSolidity(Block block, long height, boolean throwExceptions)
            throws BlockStoreException {
        return checkFormalContractEventSolidity(block, throwExceptions);
    }

    private SolidityState checkFullOrderOpenSolidity(Block block, long height, boolean throwExceptions,
            FullBlockStore store) throws BlockStoreException {
        List<Transaction> transactions = block.getTransactions();

        if (transactions.size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        if (transactions.get(0).getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        // Check that the tx has correct data
        OrderOpenInfo orderInfo;
        try {
            orderInfo = new OrderOpenInfo().parse(transactions.get(0).getData());
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        if (!transactions.get(0).getDataClassName().equals("OrderOpen")) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        // NotNull checks
        if (orderInfo.getTargetTokenid() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid target tokenid");
            return SolidityState.getFailState();
        }

        // Check bounds for target coin values
        if (orderInfo.getTargetValue() < 1 || orderInfo.getTargetValue() > Long.MAX_VALUE) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid target value");
            return SolidityState.getFailState();
        }

        // Check that the tx inputs only burn one type of tokens
        Coin burnedCoins = countBurnedToken(block, store);

        if (burnedCoins == null || burnedCoins.getValue().longValue() == 0) {
            if (throwExceptions)
                throw new InvalidOrderException("No tokens were offered.");
            return SolidityState.getFailState();
        }

        if (burnedCoins.getValue().longValue() > Long.MAX_VALUE) {
            if (throwExceptions)
                throw new InvalidOrderException("The order is too large.");
            return SolidityState.getFailState();
        }

        // Check that either the burnt token or the target token is BIG
         if (checkOrderBaseToken(orderInfo, burnedCoins)) {
            if (throwExceptions)
                throw new InvalidOrderException("Invalid exchange combination. Ensure order base token is sold or bought.");
            return SolidityState.getFailState();
        }
   
         if (!networkParameters.getOrderBaseTokens().contains(orderInfo.getOrderBaseToken())) {
             if (throwExceptions)
                 throw new InvalidOrderException("Invalid exchange combination. Ensure order base token."
                          );
             return SolidityState.getFailState();
         }
         
        // Check that we have a correct price given in full  Base Token
        // OK

        if (burnedCoins.getTokenHex().equals(orderInfo.getOrderBaseToken())) {
            if (burnedCoins.getValue().longValue() % orderInfo.getTargetValue() != 0
                    || burnedCoins.getValue().longValue() / orderInfo.getTargetValue() <= 0) {
                if (throwExceptions)
                    throw new InvalidOrderException("The given order's price is not integer.");
                return SolidityState.getFailState();
            }
        } else {
            if (orderInfo.getTargetValue() % burnedCoins.getValue().longValue() != 0
                    || orderInfo.getTargetValue() / burnedCoins.getValue().longValue() <= 0) {
                if (throwExceptions)
                    throw new InvalidOrderException("The given order's price is not integer.");
                return SolidityState.getFailState();
            }
        }

        if (orderInfo.getValidToTime() > Math.addExact(orderInfo.getValidFromTime(),
                NetworkParameters.ORDER_TIMEOUT_MAX)) {
            if (throwExceptions)
                throw new InvalidOrderException("The given order's timeout is too long.");
            return SolidityState.getFailState();
        }

        if (!ECKey.fromPublicOnly(orderInfo.getBeneficiaryPubKey()).toAddress(params).toBase58()
                .equals(orderInfo.getBeneficiaryAddress())) {
            if (throwExceptions)
                throw new InvalidOrderException("The address does not match with the given pubkey.");
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    private boolean checkOrderBaseToken(OrderOpenInfo orderInfo, Coin burnedCoins) {
        return burnedCoins.getTokenHex().equals(orderInfo.getOrderBaseToken())
                && orderInfo.getTargetTokenid().equals(orderInfo.getOrderBaseToken())
                || !burnedCoins.getTokenHex().equals(orderInfo.getOrderBaseToken())
                        && !orderInfo.getTargetTokenid().equals(orderInfo.getOrderBaseToken());
    }

    /**
     * Counts the number tokens that are being burned in this block. If multiple
     * tokens exist in the transaction, throws InvalidOrderException.
     * 
     * @param block
     * @return
     * @throws BlockStoreException
     */
    public Coin countBurnedToken(Block block, FullBlockStore store) throws BlockStoreException {
        Coin burnedCoins = null;
        for (final Transaction tx : block.getTransactions()) {
            for (int index = 0; index < tx.getInputs().size(); index++) {
                TransactionInput in = tx.getInputs().get(index);
                UTXO prevOut = store.getTransactionOutput(in.getOutpoint().getBlockHash(), in.getOutpoint().getTxHash(),
                        in.getOutpoint().getIndex());
                if (prevOut == null) {
                    // Cannot happen due to solidity checks before
                    throw new RuntimeException("Block attempts to spend a not yet existent output!");
                }

                if (burnedCoins == null)
                    burnedCoins = Coin.valueOf(0, Utils.HEX.encode(prevOut.getValue().getTokenid()));

                try {
                    burnedCoins = burnedCoins.add(prevOut.getValue());
                } catch (IllegalArgumentException e) {
                    throw new InvalidOrderException(e.getMessage());
                }
            }

            for (int index = 0; index < tx.getOutputs().size(); index++) {
                TransactionOutput out = tx.getOutputs().get(index);

                try {
                    burnedCoins = burnedCoins.subtract(out.getValue());
                } catch (IllegalArgumentException e) {
                    throw new InvalidOrderException(e.getMessage());
                }
            }
        }
        return burnedCoins;
    }

    private SolidityState checkFullOrderOpSolidity(Block block, long height, boolean throwExceptions,
            FullBlockStore store) throws BlockStoreException {
        if (block.getTransactions().size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        // No output creation
        if (!block.getTransactions().get(0).getOutputs().isEmpty()) {
            if (throwExceptions)
                throw new TransactionOutputsDisallowedException();
            return SolidityState.getFailState();
        }

        Transaction tx = block.getTransactions().get(0);
        if (tx.getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        OrderCancelInfo info = null;
        try {
            info = new OrderCancelInfo().parse(tx.getData());
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        // NotNull checks
        if (info.getBlockHash() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid target txhash");
            return SolidityState.getFailState();
        }

        // Ensure the predecessing order exists
        OrderRecord order = store.getOrder(info.getBlockHash(), Sha256Hash.ZERO_HASH);
        if (order == null) {
            return SolidityState.from(info.getBlockHash(), true);
        }

        byte[] pubKey = order.getBeneficiaryPubKey();
        byte[] data = tx.getHash().getBytes();
        byte[] signature = block.getTransactions().get(0).getDataSignature();

        // If signature of beneficiary is missing, fail
        if (!ECKey.verify(data, signature, pubKey)) {
            if (throwExceptions)
                throw new InsufficientSignaturesException();
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkFullRewardSolidity(Block block, BlockWrap storedPrev, BlockWrap storedPrevBranch,
            long height, boolean throwExceptions, FullBlockStore store) throws BlockStoreException {
        List<Transaction> transactions = block.getTransactions();

        if (transactions.size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        // No output creation
        if (!transactions.get(0).getOutputs().isEmpty()) {
            if (throwExceptions)
                throw new TransactionOutputsDisallowedException();
            return SolidityState.getFailState();
        }

        if (transactions.get(0).getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        // Check that the tx has correct data
        RewardInfo rewardInfo = new RewardInfo().parseChecked(transactions.get(0).getData());

        // NotNull checks
        if (rewardInfo.getPrevRewardHash() == null) {
            if (throwExceptions)
                throw new MissingDependencyException();
            return SolidityState.getFailState();
        }

        // Ensure dependency (prev reward hash) exists
        Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
        BlockWrap dependency = store.getBlockWrap(prevRewardHash);
        if (dependency == null)
            return SolidityState.from(prevRewardHash, true);

        // Ensure dependency (prev reward hash) is valid predecessor
        if (dependency.getBlock().getBlockType() != Type.BLOCKTYPE_INITIAL
                && dependency.getBlock().getBlockType() != Type.BLOCKTYPE_REWARD) {
            if (throwExceptions)
                throw new InvalidDependencyException("Predecessor is not reward or genesis");
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    public SolidityState checkFullTokenSolidity(Block block, long height, boolean throwExceptions,
            FullBlockStore store) throws BlockStoreException {
        if (block.getTransactions().size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        if (!block.getTransactions().get(0).isCoinBase()) {
            if (throwExceptions)
                throw new NotCoinbaseException();
            return SolidityState.getFailState();
        }

        Transaction tx = block.getTransactions().get(0);
        if (tx.getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        TokenInfo currentToken = null;
        try {
            currentToken = new TokenInfo().parse(tx.getData());
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        if (checkFormalTokenFields(throwExceptions, currentToken) == SolidityState.getFailState())
            return SolidityState.getFailState();

        // Check field correctness: amount
        if (!currentToken.getToken().getAmount().equals(block.getTransactions().get(0).getOutputSum())) {
            logger.debug("Incorrect amount field" + currentToken.getToken().getAmount() + " !="
                    + block.getTransactions().get(0).getOutputSum());
            if (throwExceptions)
                throw new InvalidTransactionDataException("Incorrect amount field");
            return SolidityState.getFailState();
        }

        // Check all token issuance transaction outputs are actually of the
        // given token
        for (Transaction tx1 : block.getTransactions()) {
            for (TransactionOutput out : tx1.getOutputs()) {
                if (!out.getValue().getTokenHex().equals(currentToken.getToken().getTokenid())) {
                    if (throwExceptions)
                        throw new InvalidTokenOutputException();
                    return SolidityState.getFailState();
                }
            }
        }

        // Check previous issuance hash exists or initial issuance
        if ((currentToken.getToken().getPrevblockhash() == null && currentToken.getToken().getTokenindex() != 0)
                || (currentToken.getToken().getPrevblockhash() != null
                        && currentToken.getToken().getTokenindex() == 0)) {
            if (throwExceptions)
                throw new MissingDependencyException();
            return SolidityState.getFailState();
        }

        // Must define enough permissioned addresses
        if (currentToken.getToken().getSignnumber() > currentToken.getMultiSignAddresses().size()) {
            if (throwExceptions)
                throw new InvalidTransactionDataException(
                        "Cannot fulfill required sign number from multisign address list");
            return SolidityState.getFailState();
        }

        // Must have a predecessing domain definition
        if (currentToken.getToken().getDomainNameBlockHash() == null) {
            if (throwExceptions)
                throw new InvalidDependencyException("Domain predecessor is empty");
            return SolidityState.getFailState();
        }

        // Requires the predecessing domain definition block to exist and be a
        // legal domain
        Token prevDomain = null;

        if (!currentToken.getToken().getDomainNameBlockHash()
                .equals(networkParameters.getGenesisBlock().getHashAsString())) {

            prevDomain = store.getTokenByBlockHash(Sha256Hash.wrap(currentToken.getToken().getDomainNameBlockHash()));
            if (prevDomain == null) {
                if (throwExceptions)
                    throw new MissingDependencyException();
                return SolidityState.from(Sha256Hash.wrap(currentToken.getToken().getDomainNameBlockHash()), true);
            }

        }
        // Ensure signatures exist
        int signatureCount = 0;
        if (tx.getDataSignature() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        // Get signatures from transaction
        String jsonStr = new String(tx.getDataSignature());
        MultiSignByRequest txSignatures;
        try {
            txSignatures = Json.jsonmapper().readValue(jsonStr, MultiSignByRequest.class);
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        // Get permissioned addresses
        Token prevToken = null;
        List<MultiSignAddress> permissionedAddresses = new ArrayList<MultiSignAddress>();
        // If not initial issuance, we check according to the previous token
        if (currentToken.getToken().getTokenindex() != 0) {
            try {
                // Previous issuance must exist to check solidity
                prevToken = store.getTokenByBlockHash(currentToken.getToken().getPrevblockhash());
                if (prevToken == null) {
                    return SolidityState.from(currentToken.getToken().getPrevblockhash(), true);
                }

                // Compare members of previous and current issuance
                if (!currentToken.getToken().getTokenid().equals(prevToken.getTokenid())) {
                    if (throwExceptions)
                        throw new InvalidDependencyException("Wrong token ID");
                    return SolidityState.getFailState();
                }
                if (currentToken.getToken().getTokenindex() != prevToken.getTokenindex() + 1) {
                    if (throwExceptions)
                        throw new InvalidDependencyException("Wrong token index");
                    return SolidityState.getFailState();
                }

                if (!currentToken.getToken().getTokenname().equals(prevToken.getTokenname())) {
                    if (throwExceptions)
                        throw new PreviousTokenDisallowsException("Cannot change token name");
                    return SolidityState.getFailState();
                }

                if (currentToken.getToken().getDomainName() != null
                        && !currentToken.getToken().getDomainName().equals(prevToken.getDomainName())) {
                    if (throwExceptions)
                        throw new PreviousTokenDisallowsException("Cannot change token domain name");
                    return SolidityState.getFailState();
                }

                if (currentToken.getToken().getDecimals() != prevToken.getDecimals()) {
                    if (throwExceptions)
                        throw new PreviousTokenDisallowsException("Cannot change token decimal");
                    return SolidityState.getFailState();
                }
                if (currentToken.getToken().getTokentype() != prevToken.getTokentype()) {
                    if (throwExceptions)
                        throw new PreviousTokenDisallowsException("Cannot change token type");
                    return SolidityState.getFailState();
                }
                if (!currentToken.getToken().getDomainNameBlockHash().equals(prevToken.getDomainNameBlockHash())) {
                    if (throwExceptions)
                        throw new PreviousTokenDisallowsException("Cannot change token domain");
                    return SolidityState.getFailState();
                }

                // Must allow more issuances
                if (prevToken.isTokenstop()) {
                    if (throwExceptions)
                        throw new PreviousTokenDisallowsException("Previous token does not allow further issuance");
                    return SolidityState.getFailState();
                }

                // Get addresses allowed to reissue
                permissionedAddresses.addAll(store.getMultiSignAddressListByTokenidAndBlockHashHex(
                        prevToken.getTokenid(), prevToken.getBlockHash()));

            } catch (BlockStoreException e) {
                // Cannot happen, previous token must exist
                e.printStackTrace();
            }
        } else {
            // First time issuances must sign for the token id
            permissionedAddresses = currentToken.getMultiSignAddresses();

            // Any first time issuances also require the domain signatures
            List<MultiSignAddress> prevDomainPermissionedAddresses = tokenDomainnameService
                    .queryDomainnameTokenMultiSignAddresses(
                            prevDomain == null ? networkParameters.getGenesisBlock().getHash()
                                    : prevDomain.getBlockHash(),store);
            SolidityState domainPermission = checkDomainPermission(prevDomainPermissionedAddresses,
                    txSignatures.getMultiSignBies(), 1,
                    // TODO remove the high level domain sign
                    // only one sign of prev domain needed
                    // prevDomain == null ? 1 : prevDomain.getSignnumber(),
                    throwExceptions, tx.getHash());
            if (domainPermission != SolidityState.getSuccessState())
                return domainPermission;
        }

        // Get permissioned pubkeys wrapped to check for bytearray equality
        Set<ByteBuffer> permissionedPubKeys = new HashSet<ByteBuffer>();
        for (MultiSignAddress multiSignAddress : permissionedAddresses) {
            byte[] pubKey = Utils.HEX.decode(multiSignAddress.getPubKeyHex());
            permissionedPubKeys.add(ByteBuffer.wrap(pubKey));
        }

        // Ensure all multiSignBys pubkeys are from the permissioned list
        for (MultiSignBy multiSignBy : new ArrayList<>(txSignatures.getMultiSignBies())) {
            ByteBuffer pubKey = ByteBuffer.wrap(Utils.HEX.decode(multiSignBy.getPublickey()));
            if (!permissionedPubKeys.contains(pubKey)) {
                // If a pubkey is not from the list, drop it.
                txSignatures.getMultiSignBies().remove(multiSignBy);
                continue;
            } else {
                // Otherwise the listed address is used. Cannot use same address
                // multiple times.
                permissionedPubKeys.remove(pubKey);
            }
        }

        // For first issuance, ensure the tokenid pubkey signature exists to
        // prevent others from generating conflicts
        if (currentToken.getToken().getTokenindex() == 0) {
            if (permissionedPubKeys.contains(ByteBuffer.wrap(Utils.HEX.decode(currentToken.getToken().getTokenid())))) {
                if (throwExceptions)
                    throw new MissingSignatureException();
                return SolidityState.getFailState();
            }
        }

        // Verify signatures
        for (MultiSignBy multiSignBy : txSignatures.getMultiSignBies()) {
            byte[] pubKey = Utils.HEX.decode(multiSignBy.getPublickey());
            byte[] data = tx.getHash().getBytes();
            byte[] signature = Utils.HEX.decode(multiSignBy.getSignature());

            if (ECKey.verify(data, signature, pubKey)) {
                signatureCount++;
            } else {
                if (throwExceptions)
                    throw new InvalidSignatureException();
                return SolidityState.getFailState();
            }
        }

        // Return whether sufficient signatures exist
        int requiredSignatureCount = prevToken != null ? prevToken.getSignnumber() : 1;
        // int requiredSignatureCount = signNumberCount;
        if (signatureCount >= requiredSignatureCount)
            return SolidityState.getSuccessState();

        if (throwExceptions)
            throw new InsufficientSignaturesException();
        return SolidityState.getFailState();
    }

    private SolidityState checkDomainPermission(List<MultiSignAddress> permissionedAddresses,
            List<MultiSignBy> multiSignBies_0, int requiredSignatures, boolean throwExceptions, Sha256Hash txHash) {

        // Make original list inaccessible by cloning list
        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>(multiSignBies_0);

        // Get permissioned pubkeys wrapped to check for bytearray equality
        Set<ByteBuffer> permissionedPubKeys = new HashSet<ByteBuffer>();
        for (MultiSignAddress multiSignAddress : permissionedAddresses) {
            byte[] pubKey = Utils.HEX.decode(multiSignAddress.getPubKeyHex());
            permissionedPubKeys.add(ByteBuffer.wrap(pubKey));
        }

        // Ensure all multiSignBys pubkeys are from the permissioned list
        for (MultiSignBy multiSignBy : new ArrayList<MultiSignBy>(multiSignBies)) {
            ByteBuffer pubKey = ByteBuffer.wrap(Utils.HEX.decode(multiSignBy.getPublickey()));
            if (!permissionedPubKeys.contains(pubKey)) {
                // If a pubkey is not from the list, drop it.
                multiSignBies.remove(multiSignBy);
                continue;
            } else {
                // Otherwise the listed address is used. Cannot use same address
                // multiple times.
                permissionedPubKeys.remove(pubKey);
            }
        }

        // Verify signatures
        int signatureCount = 0;
        for (MultiSignBy multiSignBy : multiSignBies) {
            byte[] pubKey = Utils.HEX.decode(multiSignBy.getPublickey());
            byte[] data = txHash.getBytes();
            byte[] signature = Utils.HEX.decode(multiSignBy.getSignature());

            if (ECKey.verify(data, signature, pubKey)) {
                signatureCount++;
            } else {
                if (throwExceptions)
                    throw new InvalidSignatureException();
                return SolidityState.getFailState();
            }
        }

        // Return whether sufficient signatures exist
        if (signatureCount >= requiredSignatures)
            return SolidityState.getSuccessState();
        else {
            if (throwExceptions)
                throw new InsufficientSignaturesException();
            return SolidityState.getFailState();
        }
    }

    private SolidityState checkFormalTokenFields(boolean throwExceptions, TokenInfo currentToken) {
        if (currentToken.getToken() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("getToken is null");
            return SolidityState.getFailState();
        }
        if (currentToken.getMultiSignAddresses() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("getMultiSignAddresses is null");
            return SolidityState.getFailState();
        }
        if (currentToken.getToken().getTokenid() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("getTokenid is null");
            return SolidityState.getFailState();
        }
        // if (currentToken.getToken().getPrevblockhash() == null) {
        // if (throwExceptions)
        // throw new InvalidTransactionDataException("getPrevblockhash is
        // null");
        // return SolidityState.getFailState();
        // }
        if (currentToken.getToken().getTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Not allowed");
            return SolidityState.getFailState();
        }

        if (currentToken.getToken().getDescription() != null
                && currentToken.getToken().getDescription().length() > Token.TOKEN_MAX_DESC_LENGTH) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Too long description");
            return SolidityState.getFailState();
        }

        if (currentToken.getToken().getTokenid() != null
                && currentToken.getToken().getTokenid().length() > Token.TOKEN_MAX_ID_LENGTH) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Too long tokenid");
            return SolidityState.getFailState();
        }

        if (currentToken.getToken().getLanguage() != null
                && currentToken.getToken().getLanguage().length() > Token.TOKEN_MAX_LANGUAGE_LENGTH) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Too long language");
            return SolidityState.getFailState();
        }
        if (currentToken.getToken().getClassification() != null
                && currentToken.getToken().getClassification().length() > Token.TOKEN_MAX_CLASSIFICATION_LENGTH) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Too long classification");
            return SolidityState.getFailState();
        }
        if (currentToken.getToken().getTokenname() == null || "".equals(currentToken.getToken().getTokenname())) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Token name cannot be null.");
        }
        if (currentToken.getToken().getTokenname() != null
                && currentToken.getToken().getTokenname().length() > Token.TOKEN_MAX_NAME_LENGTH) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Too long token name");
            return SolidityState.getFailState();
        }

        if (currentToken.getToken().getDomainName() != null
                && currentToken.getToken().getDomainName().length() > Token.TOKEN_MAX_URL_LENGTH) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Too long domainname");
            return SolidityState.getFailState();
        }
        if (currentToken.getToken().getSignnumber() < 0) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid sign number");
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    public SolidityState checkRewardBlockPow(Block block, boolean throwExceptions) throws BlockStoreException {
        try {
            RewardInfo rewardInfo = new RewardInfo().parse(block.getTransactions().get(0).getData());
            // Get difficulty from predecessors
            BigInteger target = Utils.decodeCompactBits(rewardInfo.getDifficultyTargetReward());
            // Check PoW
            boolean allOk = false;
            try {
                allOk = block.checkProofOfWork(throwExceptions, target);
            } catch (VerificationException e) {
                logger.warn("Failed to verify block: ", e);
                logger.warn(block.getHashAsString());
                throw e;
            }

            if (!allOk)
                return SolidityState.getFailState();
            else
                return SolidityState.getSuccessState();
        } catch (Exception e) {
            throw new UnsolidException();
        }
    }

    public SolidityState checkChainSolidity(Block block, boolean throwExceptions, FullBlockStore store)
            throws BlockStoreException {

        // Check the block fulfills PoW as chain
        checkRewardBlockPow(block, true);

        // Check the chain block formally valid
        checkFormalBlockSolidity(block, true);

        BlockWrap prevTrunkBlock = store.getBlockWrap(block.getPrevBlockHash());
        BlockWrap prevBranchBlock = store.getBlockWrap(block.getPrevBranchBlockHash());
        if (prevTrunkBlock == null)
            SolidityState.from(block.getPrevBlockHash(), true);
        if (prevBranchBlock == null)
            SolidityState.from(block.getPrevBranchBlockHash(), true);

        long difficulty = rewardService.calculateNextBlockDifficulty(block.getRewardInfo());
        if (difficulty != block.getDifficultyTarget()) {
            throw new VerificationException("calculateNextBlockDifficulty does not match.");
        }

        if (block.getLastMiningRewardBlock() != block.getRewardInfo().getChainlength()) {
            if (throwExceptions)
                throw new DifficultyConsensusInheritanceException();
            return SolidityState.getFailState();
        }

        SolidityState difficultyResult = rewardService.checkRewardDifficulty(block,store);
        if (!difficultyResult.isSuccessState()) {
            return difficultyResult;
        }

        SolidityState referenceResult = rewardService.checkRewardReferencedBlocks(block,store);
        if (!referenceResult.isSuccessState()) {
            return referenceResult;
        }

        // Solidify referenced blocks
        rewardService.solidifyBlocks(block.getRewardInfo(),store);

        return SolidityState.getSuccessState();
    }

    /**
     * Checks if the block has all of its dependencies to fully determine its
     * validity. Then checks if the block is valid based on its dependencies. If
     * SolidityState.getSuccessState() is returned, the block is valid. If
     * SolidityState.getFailState() is returned, the block is invalid.
     * Otherwise, appropriate solidity states are returned to imply missing
     * dependencies.
     *
     * @param block
     * @return SolidityState
     * @throws BlockStoreException
     */
    public SolidityState checkSolidity(Block block, boolean throwExceptions, FullBlockStore store)
            throws BlockStoreException {
        try {
            // Check formal correctness of the block
            SolidityState formalSolidityResult = checkFormalBlockSolidity(block, throwExceptions);
            if (formalSolidityResult.isFailState())
                return formalSolidityResult;

            // Predecessors must exist and be ok
            SolidityState predecessorsExist = checkPredecessorsExistAndOk(block, throwExceptions, store);
            if (!predecessorsExist.isSuccessState()) {
                return predecessorsExist;
            }

            // Inherit solidity from predecessors if they are not solid
            SolidityState minPredecessorSolidity = getMinPredecessorSolidity(block, throwExceptions, store);

            // For consensus blocks, it works as follows:
            // If solid == 1 or solid == 2, we also check for PoW now
            // since it is possible to do so
            if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
                if (minPredecessorSolidity.getState() == State.MissingCalculation
                        || minPredecessorSolidity.getState() == State.Success) {
                    SolidityState state = checkRewardBlockPow(block, throwExceptions);
                    if (!state.isSuccessState()) {
                        return state;
                    }
                }
            }

            // Inherit solidity from predecessors if they are not solid
            switch (minPredecessorSolidity.getState()) {
            case MissingCalculation:
            case Invalid:
            case MissingPredecessor:
                return minPredecessorSolidity;
            case Success:
                break;
            }
        } catch (IllegalArgumentException e) {
            throw new VerificationException(e);
        }

        // Otherwise, the solidity of the block itself is checked
        return checkFullBlockSolidity(block, throwExceptions, store);
    }
}

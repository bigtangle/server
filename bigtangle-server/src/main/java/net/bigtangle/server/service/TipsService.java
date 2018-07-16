/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class TipsService {
    private final Logger log = LoggerFactory.getLogger(TipsService.class);

    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    private BlockService blockService;
    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    private ValidatorService validatorService;

    private static Random seed = new SecureRandom();

    public List<Sha256Hash> getRatingTips(int count) throws Exception {
        Stopwatch watch = Stopwatch.createStarted();

        List<Sha256Hash> entryPoints = getRatingEntryPoints(count);
        List<Sha256Hash> results = new ArrayList<>();
        long latestImportTime = store.getMaxImportTime();

        for (int i = 0; i < entryPoints.size(); i++) {
            results.add(getMCMCResultBlock(entryPoints.get(i), latestImportTime).peek().getBlock().getHash());
            // TODO eventually use low-pass filter here, e.g. latestImportTime -
            // 1000*i
        }

        watch.stop();
        log.info("getRatingTips time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

        return results;
    }

    public Pair<Sha256Hash, Sha256Hash> getValidatedBlockPair() throws Exception {
        return getValidatedBlockPairIteratively();
        
//        Stopwatch watch = Stopwatch.createStarted();
//        List<Pair<Sha256Hash, Sha256Hash>> pairs = getValidatedBlockPairs(1);
//        watch.stop();
//        log.info("getValidatedBlockPair time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
//
//        return pairs.get(0);
    }

    public Pair<Sha256Hash, Sha256Hash> getValidatedBlockPairIteratively() throws Exception {
        Stopwatch watch = Stopwatch.createStarted();
        List<Sha256Hash> ratingEntryPoints = getRatingEntryPoints(2);
        BlockWrap left = store.getBlockWrap(ratingEntryPoints.get(0));
        BlockWrap right = store.getBlockWrap(ratingEntryPoints.get(1));

        // Init conflict set
        HashSet<ConflictPoint> currentConflictPoints = new HashSet<>();
        currentConflictPoints.addAll(validatorService.toConflictPointCandidates(left));
        currentConflictPoints.addAll(validatorService.toConflictPointCandidates(right));

        // Find valid approvers to go to
        // TODO filter low-pass filter here and below too
        List<BlockWrap> leftApprovers = blockService.getSolidApproverBlocks(left.getBlock().getHash());
        List<BlockWrap> rightApprovers = blockService.getSolidApproverBlocks(right.getBlock().getHash());
        leftApprovers.removeIf(b -> validatorService.isConflicting(b, currentConflictPoints));
        rightApprovers.removeIf(b -> validatorService.isConflicting(b, currentConflictPoints));

        // Perform next steps
        BlockWrap nextLeft = performStep(left, leftApprovers);
        BlockWrap nextRight = performStep(right, rightApprovers);

        // Proceed on path to be included first (highest rating else right which is random)
        while (nextLeft != left && nextRight != right) {
            if (nextLeft.getBlockEvaluation().getRating() > nextRight.getBlockEvaluation().getRating()) {
                left = nextLeft;
                currentConflictPoints.addAll(validatorService.toConflictPointCandidates(left));
                leftApprovers = blockService.getSolidApproverBlocks(left.getBlock().getHash());
                leftApprovers.removeIf(b -> validatorService.isConflicting(b, currentConflictPoints));
                nextLeft = performStep(left, leftApprovers);
            } else {
                right = nextRight;
                currentConflictPoints.addAll(validatorService.toConflictPointCandidates(right));
                rightApprovers = blockService.getSolidApproverBlocks(right.getBlock().getHash());
                rightApprovers.removeIf(b -> validatorService.isConflicting(b, currentConflictPoints));
                nextRight = performStep(right, rightApprovers);
            }
        }

        // Go forward on the remaining paths
        while (nextLeft != left) {
            left = nextLeft;
            currentConflictPoints.addAll(validatorService.toConflictPointCandidates(left));
            leftApprovers = blockService.getSolidApproverBlocks(left.getBlock().getHash());
            leftApprovers.removeIf(b -> validatorService.isConflicting(b, currentConflictPoints));
            nextLeft = performStep(left, leftApprovers);
        }
        while (nextRight != right) {
            right = nextRight;
            currentConflictPoints.addAll(validatorService.toConflictPointCandidates(right));
            rightApprovers = blockService.getSolidApproverBlocks(right.getBlock().getHash());
            rightApprovers.removeIf(b -> validatorService.isConflicting(b, currentConflictPoints));
            nextRight = performStep(right, rightApprovers);
        }

        watch.stop();
        log.info("getValidatedBlockPairIteratively time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

        return Pair.of(left.getBlock().getHash(), right.getBlock().getHash());
    }

    public BlockWrap performStep(BlockWrap currentBlock, List<BlockWrap> approverHashes) {
        if (approverHashes.size() == 0) {
            return currentBlock;
        } else if (approverHashes.size() == 1) {
            return approverHashes.get(0);
        } else {
            double[] transitionWeights = new double[approverHashes.size()];
            double transitionWeightSum = 0;
            long currentCumulativeWeight = currentBlock.getBlockEvaluation().getCumulativeWeight();

            // Calculate the unnormalized transition weights
            // TODO eventually set alpha according to ideal orphan rates as
            // found in parameter tuning simulations and papers (see
            // POLB.pdf)
            for (int i = 0; i < approverHashes.size(); i++) {
                double alpha = 0.5;
                transitionWeights[i] = Math.exp(-alpha
                        * (currentCumulativeWeight - approverHashes.get(i).getBlockEvaluation().getCumulativeWeight()));
                transitionWeightSum += transitionWeights[i];
            }

            // Randomly select one of the approvers weighted by their
            // transition probabilities
            double transitionRealization = seed.nextDouble() * transitionWeightSum;
            for (int i = 0; i < approverHashes.size(); i++) {
                transitionRealization -= transitionWeights[i];
                if (transitionRealization <= 0) {
                    return approverHashes.get(i);
                }
            }

            log.warn("MCMC step failed");
            return currentBlock;
        }
    }

    public List<Pair<Sha256Hash, Sha256Hash>> getValidatedBlockPairs(int count) throws Exception {
        Stopwatch watch = Stopwatch.createStarted();

        List<Pair<Stack<BlockWrap>, HashSet<BlockWrap>>> blocks = getValidatedBlocks(2 * count);
        List<Pair<Sha256Hash, Sha256Hash>> results = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            Pair<Stack<BlockWrap>, HashSet<BlockWrap>> b1 = blocks.get(index);
            Pair<Stack<BlockWrap>, HashSet<BlockWrap>> b2 = blocks.get(count + index);
            Stack<BlockWrap> selectedBlock1 = b1.getLeft();
            Stack<BlockWrap> selectedBlock2 = b2.getLeft();

            // Get all blocks that are approved together
            HashSet<BlockWrap> approvedNonMilestoneBlockEvaluations = new HashSet<>(b1.getRight());
            approvedNonMilestoneBlockEvaluations.addAll(b2.getRight());

            // Remove blocks that are conflicting
            validatorService.resolveValidityConflicts(approvedNonMilestoneBlockEvaluations, false);

            // If the selected blocks were in conflict, walk backwards until we
            // find a non-conflicting block
            if (!approvedNonMilestoneBlockEvaluations.contains(selectedBlock1.peek()))
                selectedBlock1 = walkBackwardsUntilContained(b1.getLeft(), approvedNonMilestoneBlockEvaluations.stream()
                        .map(block -> block.getBlock().getHash()).collect(Collectors.toSet()));

            if (!approvedNonMilestoneBlockEvaluations.contains(selectedBlock2.peek()))
                selectedBlock2 = walkBackwardsUntilContained(b2.getLeft(), approvedNonMilestoneBlockEvaluations.stream()
                        .map(block -> block.getBlock().getHash()).collect(Collectors.toSet()));

            results.add(
                    Pair.of(selectedBlock1.peek().getBlock().getHash(), selectedBlock2.peek().getBlock().getHash()));
        }

        watch.stop();
        log.info("getValidatedBlockPairs time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

        return results;
    }

    // Specifically, we check for milestone-candidate-conflicts +
    // candidate-candidate-conflicts and reverse until there are no such
    // conflicts
    // Also returns all approved non-milestone blocks in topological ordering
    private List<Pair<Stack<BlockWrap>, HashSet<BlockWrap>>> getValidatedBlocks(int count) throws Exception {
        List<Pair<Stack<BlockWrap>, HashSet<BlockWrap>>> results = new ArrayList<>();
        List<Sha256Hash> entryPoints = getValidationEntryPoints(count);

        for (Sha256Hash entryPoint : entryPoints) {
            Stack<BlockWrap> selectedBlock = getMCMCResultBlock(entryPoint);
            BlockWrap selectedBlockEvaluation = selectedBlock.peek();

            // Get all non-milestone blocks that are to be approved by this
            // selection
            HashSet<BlockWrap> approvedNonMilestoneBlockEvaluations = new HashSet<>();
            blockService.addApprovedNonMilestoneBlocksTo(approvedNonMilestoneBlockEvaluations, selectedBlockEvaluation); // empty
                                                                                                                         // for
                                                                                                                         // approving
                                                                                                                         // milestones

            // Remove blocks that are conflicting
            validatorService.resolveValidityConflicts(approvedNonMilestoneBlockEvaluations, false);

            // If the selected block is in conflict, walk backwards until we
            // find a non-conflicting block
            if (!approvedNonMilestoneBlockEvaluations.contains(selectedBlockEvaluation)) {
                selectedBlock = walkBackwardsUntilContained(selectedBlock, approvedNonMilestoneBlockEvaluations.stream()
                        .map(block -> block.getBlock().getHash()).collect(Collectors.toSet()));
                selectedBlockEvaluation = selectedBlock.peek();
                approvedNonMilestoneBlockEvaluations.clear();
                blockService.addApprovedNonMilestoneBlocksTo(approvedNonMilestoneBlockEvaluations,
                        selectedBlockEvaluation);
            }

            results.add(Pair.of(selectedBlock, approvedNonMilestoneBlockEvaluations));
        }

        return results;
    }

    private Stack<BlockWrap> getMCMCResultBlock(Sha256Hash entryPoint) throws Exception {
        return randomWalk(entryPoint, Long.MAX_VALUE);
    }

    private Stack<BlockWrap> getMCMCResultBlock(Sha256Hash entryPoint, long maxTime) throws Exception {
        return randomWalk(entryPoint, maxTime);
    }

    private List<Sha256Hash> getRatingEntryPoints(int count) throws Exception {
        List<BlockEvaluation> candidates = blockService.getRatingEntryPointCandidates();
        return getRandomsByCumulativeWeight(candidates, count);
    }

    private List<Sha256Hash> getValidationEntryPoints(int count) throws Exception {
        List<BlockEvaluation> candidates = blockService.getValidationEntryPointCandidates();
        return getRandomsByCumulativeWeight(candidates, count);
    }

    private List<Sha256Hash> getRandomsByCumulativeWeight(List<BlockEvaluation> candidates, int count) {
        double maxBlockWeight = candidates.stream().mapToLong(e -> e.getCumulativeWeight()).max().orElse(1L);
        double normalizedBlockWeightSum = candidates.stream().mapToDouble(e -> e.getCumulativeWeight() / maxBlockWeight)
                .sum();
        List<Sha256Hash> results = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            if (candidates.isEmpty()) {
                results.add(networkParameters.getGenesisBlock().getHash());
            } else {
                // Randomly select one of the candidates weighted by their
                // cumulative weights
                double selectionRealization = seed.nextDouble() * normalizedBlockWeightSum;
                for (int selection = 0; selection < candidates.size(); selection++) {
                    BlockEvaluation selectedBlock = candidates.get(selection);
                    selectionRealization -= selectedBlock.getCumulativeWeight() / maxBlockWeight;
                    if (selectionRealization <= 0) {
                        results.add(selectedBlock.getBlockHash());
                        break;
                    }
                }
            }
        }

        return results;
    }

    private Stack<BlockWrap> randomWalk(Sha256Hash blockHash, long maxTime) throws Exception {
        // Repeatedly perform transitions until the final tip is found
        Stack<BlockWrap> path = new Stack<BlockWrap>();
        while (blockHash != null) {
            BlockWrap blockEval = store.getBlockWrap(blockHash);
            path.add(blockEval);
            List<Sha256Hash> approverHashes = blockService.getSolidApproverBlockHashes(blockHash);

            // Low-pass filter
            approverHashes.removeIf(b -> {
                try {
                    return blockService.getBlockEvaluation(b).getInsertTime() > maxTime;
                } catch (BlockStoreException e) {
                    return false;
                }
            });

            if (approverHashes.size() == 0) {
                return path;
            } else if (approverHashes.size() == 1) {
                blockHash = approverHashes.get(0);
            } else {
                Sha256Hash[] blockApprovers = approverHashes.toArray(new Sha256Hash[approverHashes.size()]);
                double[] transitionWeights = new double[blockApprovers.length];
                double transitionWeightSum = 0;
                long currentCumulativeWeight = blockEval.getBlockEvaluation().getCumulativeWeight();

                // Calculate the unnormalized transition weights
                // TODO eventually set alpha according to ideal orphan rates as
                // found in parameter tuning simulations and papers (see
                // POLB.pdf)
                for (int i = 0; i < blockApprovers.length; i++) {
                    // transitionWeights[i] = Math.pow(currentCumulativeWeight -
                    // blockService.getBlockEvaluation(blockApprovers[i]).getCumulativeWeight(),
                    // -3);
                    double alpha = 0.5;
                    transitionWeights[i] = Math.exp(-alpha * (currentCumulativeWeight
                            - blockService.getBlockEvaluation(blockApprovers[i]).getCumulativeWeight()));
                    transitionWeightSum += transitionWeights[i];
                }

                // Randomly select one of the approvers weighted by their
                // transition probabilities
                double transitionRealization = seed.nextDouble() * transitionWeightSum;
                for (int i = 0; i < blockApprovers.length; i++) {
                    transitionRealization -= transitionWeights[i];
                    if (transitionRealization <= 0) {
                        blockHash = blockApprovers[i];
                        break;
                    }
                }
            }
        }

        return path;
    }

    private Stack<BlockWrap> walkBackwardsUntilContained(Stack<BlockWrap> blockEvals, Set<Sha256Hash> winners)
            throws Exception {
        // Pop stack until top hash is not contained anymore
        while (blockEvals.size() > 1) {
            if (winners.contains(blockEvals.peek().getBlock().getHash())
                    || blockEvals.peek().getBlockEvaluation().isMilestone())
                return blockEvals;
            else
                blockEvals.pop();
        }

        // Only one block left, if it is winning keep it
        if (winners.contains(blockEvals.peek().getBlock().getHash())
                || blockEvals.peek().getBlockEvaluation().isMilestone())
            return blockEvals;

        // Else repeatedly perform random transitions backwards until genesis
        // block
        BlockWrap blockEval;
        while ((blockEval = blockEvals.peek()) != null) {
            if (blockEval.getBlock().getHash().equals(networkParameters.getGenesisBlock().getHash())) {
                return blockEvals;
            }

            if (winners.contains(blockEval.getBlock().getHash()) || blockEval.getBlockEvaluation().isMilestone()) {
                return blockEvals;
            }

            // Randomly select one of the two approved blocks to go to
            Block block = blockEval.getBlock();
            double transitionRealization = seed.nextDouble();
            blockEvals.pop();
            if (transitionRealization <= 0.5) {
                blockEvals.push(store.getBlockWrap(block.getPrevBlockHash()));
            } else {
                blockEvals.push(store.getBlockWrap(block.getPrevBranchBlockHash()));
            }
        }

        throw new IllegalStateException("Failed to find non-losing blocks.");
    }
}

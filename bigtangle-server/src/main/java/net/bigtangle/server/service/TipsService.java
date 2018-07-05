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

    public List<Sha256Hash> getRatingTips(int count) throws Exception {
        Stopwatch watch = Stopwatch.createStarted();
        SecureRandom seed = new SecureRandom();

        List<Sha256Hash> entryPoints = getRatingEntryPoints(count, seed);
        List<Sha256Hash> results = new ArrayList<>();
        long latestImportTime = store.getMaxImportTime();

        for (int i = 0; i < entryPoints.size(); i++) {
            results.add(getMCMCResultBlock(entryPoints.get(i), seed, latestImportTime).peek().getBlock().getHash());
            // TODO eventually use low-pass filter here, e.g. latestImportTime -
            // 1000*i
        }

        watch.stop();
        log.info("getRatingTips time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

        return results;
    }

    public Pair<Sha256Hash, Sha256Hash> getValidatedBlockPair() throws Exception {
        Stopwatch watch = Stopwatch.createStarted();
        List<Pair<Sha256Hash, Sha256Hash>> pairs = getValidatedBlockPairs(1);
        watch.stop();
        log.info("getValidatedBlockPair time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

        return pairs.get(0);
    }

    public List<Pair<Sha256Hash, Sha256Hash>> getValidatedBlockPairs(int count) throws Exception {
        Stopwatch watch = Stopwatch.createStarted();
        SecureRandom seed = new SecureRandom();

        List<Pair<Stack<BlockWrap>, HashSet<BlockWrap>>> blocks = getValidatedBlocks(2 * count, seed);
        List<Pair<Sha256Hash, Sha256Hash>> results = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            Pair<Stack<BlockWrap>, HashSet<BlockWrap>> b1 = blocks.get(index);
            Pair<Stack<BlockWrap>, HashSet<BlockWrap>> b2 = blocks.get(count + index);
            BlockEvaluation e1 = b1.getLeft().peek().getBlockEvaluation();
            BlockEvaluation e2 = b2.getLeft().peek().getBlockEvaluation();
            Stack<BlockWrap> selectedBlock1 = b1.getLeft();
            Stack<BlockWrap> selectedBlock2 = b2.getLeft();

            // Get all blocks that are approved together
            HashSet<BlockWrap> approvedNonMilestoneBlockEvaluations = new HashSet<>(b1.getRight());
            approvedNonMilestoneBlockEvaluations.addAll(b2.getRight());

            // Remove blocks that are conflicting
            validatorService.resolveValidityConflicts(approvedNonMilestoneBlockEvaluations, false);

            // If the selected blocks were in conflict, walk backwards until we
            // find a non-conflicting block
            if (!approvedNonMilestoneBlockEvaluations.contains(e1))
                selectedBlock1 = walkBackwardsUntilContained(b1.getLeft(), seed, approvedNonMilestoneBlockEvaluations
                        .stream().map(block -> block.getBlock().getHash()).collect(Collectors.toSet()));

            if (!approvedNonMilestoneBlockEvaluations.contains(e2))
                selectedBlock2 = walkBackwardsUntilContained(b2.getLeft(), seed, approvedNonMilestoneBlockEvaluations
                        .stream().map(block -> block.getBlock().getHash()).collect(Collectors.toSet()));

            results.add(Pair.of(selectedBlock1.peek().getBlock().getHash(), selectedBlock2.peek().getBlock().getHash()));
        }

        watch.stop();
        log.info("getValidatedBlockPairs time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

        return results;
    }

    // Specifically, we check for milestone-candidate-conflicts +
    // candidate-candidate-conflicts and reverse until there are no such
    // conflicts
    // Also returns all approved non-milestone blocks in topological ordering
    private List<Pair<Stack<BlockWrap>, HashSet<BlockWrap>>> getValidatedBlocks(int count, Random seed)
            throws Exception {
        List<Pair<Stack<BlockWrap>, HashSet<BlockWrap>>> results = new ArrayList<>();
        List<Sha256Hash> entryPoints = getValidationEntryPoints(count, seed);

        for (Sha256Hash entryPoint : entryPoints) {
            Stack<BlockWrap> selectedBlock = getMCMCResultBlock(entryPoint, seed);
            BlockWrap selectedBlockEvaluation = selectedBlock.peek();

            // Get all non-milestone blocks that are to be approved by this
            // selection
            HashSet<BlockWrap> approvedNonMilestoneBlockEvaluations = new HashSet<>();
            blockService.addApprovedNonMilestoneBlocksTo(approvedNonMilestoneBlockEvaluations, selectedBlockEvaluation); //empty for approving milestones

            // Remove blocks that are conflicting
            validatorService.resolveValidityConflicts(approvedNonMilestoneBlockEvaluations, false);

            // If the selected block is in conflict, walk backwards until we
            // find a non-conflicting block
            if (!approvedNonMilestoneBlockEvaluations.contains(selectedBlockEvaluation)) {
                selectedBlock = walkBackwardsUntilContained(selectedBlock, seed, approvedNonMilestoneBlockEvaluations
                        .stream().map(block -> block.getBlock().getHash()).collect(Collectors.toSet()));
                selectedBlockEvaluation = selectedBlock.peek();
                approvedNonMilestoneBlockEvaluations.clear();
                blockService.addApprovedNonMilestoneBlocksTo(approvedNonMilestoneBlockEvaluations,
                        selectedBlockEvaluation);
            }

            results.add(Pair.of(selectedBlock, approvedNonMilestoneBlockEvaluations));
        }

        return results;
    }

    private Stack<BlockWrap> getMCMCResultBlock(Sha256Hash entryPoint, Random seed) throws Exception {
        return randomWalk(entryPoint, seed, Long.MAX_VALUE);
    }

    private Stack<BlockWrap> getMCMCResultBlock(Sha256Hash entryPoint, Random seed, long maxTime) throws Exception {
        return randomWalk(entryPoint, seed, maxTime);
    }

    private List<Sha256Hash> getRatingEntryPoints(int count, Random seed) throws Exception {
        List<BlockEvaluation> candidates = blockService.getRatingEntryPointCandidates();
        return getRandomsByCumulativeWeight(candidates, count, seed);
    }

    private List<Sha256Hash> getValidationEntryPoints(int count, Random seed) throws Exception {
        List<BlockEvaluation> candidates = blockService.getValidationEntryPointCandidates();
        return getRandomsByCumulativeWeight(candidates, count, seed);
    }

    private List<Sha256Hash> getRandomsByCumulativeWeight(List<BlockEvaluation> candidates, int count, Random seed) {
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
                        results.add(selectedBlock.getBlockhash());
                        break;
                    }
                }
            }
        }

        return results;
    }

    private Stack<BlockWrap> randomWalk(Sha256Hash blockHash, Random seed, long maxTime) throws Exception {
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

    private Stack<BlockWrap> walkBackwardsUntilContained(Stack<BlockWrap> blockEvals, Random seed,
            Set<Sha256Hash> winners) throws Exception {
        // Pop stack until top hash is not contained anymore
        while (blockEvals.size() > 1) {
            if (winners.contains(blockEvals.peek().getBlock().getHash()) || blockEvals.peek().getBlockEvaluation().isMilestone())
                return blockEvals;
            else
                blockEvals.pop();
        }
        
        // Only one block left, if it is winning keep it
        if (winners.contains(blockEvals.peek().getBlock().getHash()) || blockEvals.peek().getBlockEvaluation().isMilestone())
            return blockEvals;

        // Else repeatedly perform random transitions backwards until genesis block
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

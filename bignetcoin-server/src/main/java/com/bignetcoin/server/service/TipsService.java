/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.Stack;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bignetcoin.server.model.TipsViewModel;

@Service
public class TipsService {

    private final Logger log = LoggerFactory.getLogger(TipsService.class);
    @Autowired
    private MilestoneService milestone;

	@Autowired
	protected FullPrunedBlockStore store;
    @Autowired
    private BlockService blockService;
    @Autowired
    private TipsViewModel tipsViewModel;
    @Autowired
    private BlockValidator blockValidator;
    @Autowired
    protected NetworkParameters networkParameters;

    private int RATING_THRESHOLD = 75; // Must be in [0..100] range

    private int RESCAN_TX_TO_REQUEST_INTERVAL = 750;
    private int maxDepth;

    public void setRATING_THRESHOLD(int value) {
        if (value < 0)
            value = 0;
        if (value > 100)
            value = 100;
        RATING_THRESHOLD = value;
    }

    private void scanTipsForSolidity() throws Exception {
        int size = tipsViewModel.nonSolidSize();
        if (size != 0) {
            Sha256Hash hash = tipsViewModel.getRandomNonSolidTipHash();
            boolean isTip = true;
            if (hash != null && blockService.getApproverBlocks(hash).size() != 0) {
                tipsViewModel.removeTipHash(hash);
                isTip = false;
            }
            if (hash != null && isTip && blockValidator.checkSolidity(hash, false)) {
                // if(hash != null &&
                // BlockViewModel.fromHash(hash).isSolid() && isTip) {
                tipsViewModel.setSolid(hash);
            }
        }
    }

    public Sha256Hash blockToApprove(final Sha256Hash reference, final Sha256Hash extraTip, final int depth,
            final int iterations, Random seed) throws Exception {
        final int msDepth;
        if (depth > maxDepth) {
            msDepth = maxDepth;
        } else {
            msDepth = depth;
        }
        Map<Sha256Hash, Long> cumulativeweights = new HashMap<Sha256Hash, Long>();
        Set<Sha256Hash> analyzedTips = new HashSet<Sha256Hash>();
        Set<Sha256Hash> maxDepthOk = new HashSet<Sha256Hash>();
        try {
            Sha256Hash entryPointTipSha256Hash = entryPoint(reference);
            // serialUpdateCumulativeweights(entryPointTipSha256Hash,
            // cumulativeweights,
            // analyzedTips, extraTip);
            // recursiveUpdateCumulativeweights(entryPointTipSha256Hash,
            // cumulativeweights,
            // analyzedTips);
//            System.out.println(cumulativeweights);
            analyzedTips.clear();
            return markovChainMonteCarlo(entryPointTipSha256Hash, extraTip, cumulativeweights, iterations,
                    milestone.latestSolidSubtangleMilestoneIndex - depth * 2, maxDepthOk, seed);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Encountered error: " + e.getLocalizedMessage());
            throw e;
        }
    }

    Sha256Hash entryPoint(final Sha256Hash sha256Hash) throws Exception {
        // Block block = this.blockService.getBlock(sha256Hash);
        // return block.getHash();
        return networkParameters.getGenesisBlock().getHash();

    }

    Sha256Hash markovChainMonteCarlo(final Sha256Hash tip, final Sha256Hash extraTip,
            final Map<Sha256Hash, Long> cumulativeweights, final int iterations, final int maxDepth,
            final Set<Sha256Hash> maxDepthOk, final Random seed) throws Exception {
        Map<Sha256Hash, Integer> monteCarloIntegrations = new HashMap<>();
        Sha256Hash tail;
        for (int i = iterations; i-- > 0;) {
            tail = randomWalk(tip, extraTip, cumulativeweights, maxDepth, maxDepthOk, seed);
            if (monteCarloIntegrations.containsKey(tail)) {
                monteCarloIntegrations.put(tail, monteCarloIntegrations.get(tail) + 1);
            } else {
                monteCarloIntegrations.put(tail, 1);
            }
        }
        return monteCarloIntegrations.entrySet().stream().reduce((a, b) -> {
            if (a.getValue() > b.getValue()) {
                return a;
            } else if (a.getValue() < b.getValue()) {
                return b;
            } else if (seed.nextBoolean()) {
                return a;
            } else {
                return b;
            }
        }).map(Map.Entry::getKey).orElse(null);
    }

    Sha256Hash randomWalk(final Sha256Hash start, final Sha256Hash extraTip,
            final Map<Sha256Hash, Long> cumulativeweights, final int maxDepth, final Set<Sha256Hash> maxDepthOk,
            Random rnd) throws Exception {
        Sha256Hash tip = start;
        Sha256Hash[] tipApprovers;
        Set<Sha256Hash> analyzedTips = new HashSet<>();
        int approverIndex;
        double cumulativeweightWeight;
        double[] walkCumulativeweights;

        while (tip != null) {

            List<Sha256Hash> approvers = blockService.getApproverBlockHashes(tip);
//            if (belowMaxDepth(tip, maxDepth, maxDepthOk)) {
//                log.info("Reason to stop: belowMaxDepth" + tip);
//                break;
//            }
            if (approvers.size() == 0) { // TODO check solidity
                //log.info("Reason to stop:  is a tip =" + tip);
                break;
            } else if (approvers.size() == 1) {
                tip = approvers.get(0);
            } else {
                tipApprovers = approvers.toArray(new Sha256Hash[approvers.size()]);
                if (!cumulativeweights.containsKey(tip)) {
                    // serialUpdateCumulativeweights(tip, cumulativeweights,
                    // analyzedTips,
                    // extraTip);
                    recursiveUpdateCumulativeweights(tip, cumulativeweights, analyzedTips);
                    analyzedTips.clear();
                }

                walkCumulativeweights = new double[tipApprovers.length];
                double maxCumulativeweight = 0;
                long tipCumulativeweight = cumulativeweights.get(tip);
                for (int i = 0; i < tipApprovers.length; i++) {
                    // transition probability = ((Hx-Hy)^-3)/maxCumulativeweight
                    walkCumulativeweights[i] = Math
                            .pow(tipCumulativeweight - cumulativeweights.getOrDefault(tipApprovers[i], 0L), -3);
                    maxCumulativeweight += walkCumulativeweights[i];
                }
                cumulativeweightWeight = rnd.nextDouble() * maxCumulativeweight;
                for (approverIndex = tipApprovers.length; approverIndex-- > 1;) {
                    cumulativeweightWeight -= walkCumulativeweights[approverIndex];
                    if (cumulativeweightWeight <= 0) {
                        break;
                    }
                }
                tip = tipApprovers[approverIndex];

            }
        }
        return tip;
    }

    static long capSum(long a, long b, long max) {
        if (a + b < 0 || a + b > max) {
            return max;
        }
        return a + b;
    }

    void serialUpdateCumulativeweights(final Sha256Hash txHash, final Map<Sha256Hash, Long> cumulativeweights,
            final Set<Sha256Hash> analyzedTips, final Sha256Hash extraTip) throws Exception {
        Stack<Sha256Hash> hashesToRate = new Stack<Sha256Hash>();
        hashesToRate.push(txHash);
        Sha256Hash currentHash;
        boolean addedBack;
        while (!hashesToRate.empty()) {
            currentHash = hashesToRate.pop();
            List<Sha256Hash> approvers = blockService.getApproverBlockHashes(txHash);
            addedBack = false;
            for (Sha256Hash approver : approvers) {
                if (cumulativeweights.get(approver) == null && !approver.equals(currentHash)) {
                    if (!addedBack) {
                        addedBack = true;
                        hashesToRate.push(currentHash);
                    }
                    hashesToRate.push(approver);
                }
            }
            if (!addedBack && analyzedTips.add(currentHash)) {
                long cumulativeweight = approvers.stream().map(cumulativeweights::get).filter(Objects::nonNull)
                        .reduce((a, b) -> capSum(a, b, Long.MAX_VALUE / 2)).orElse(0L);
                cumulativeweights.put(currentHash, cumulativeweight);
            }
        }
    }

    public Set<Sha256Hash> updateHashCumulativeweights(Sha256Hash txHash,
            Map<Sha256Hash, Set<Sha256Hash>> cumulativeweights, Set<Sha256Hash> analyzedTips) throws Exception {
        Set<Sha256Hash> cumulativeweight;
        if (analyzedTips.add(txHash)) {
            List<Sha256Hash> approvers = blockService.getApproverBlockHashes(txHash);
            cumulativeweight = new HashSet<>(Collections.singleton(txHash));
            for (Sha256Hash approver : approvers) {
                cumulativeweight.addAll(updateHashCumulativeweights(approver, cumulativeweights, analyzedTips));
            }
            cumulativeweights.put(txHash, cumulativeweight);
        } else {
            if (cumulativeweights.containsKey(txHash)) {
                cumulativeweight = cumulativeweights.get(txHash);
            } else {
                cumulativeweight = new HashSet<>();
            }
        }
        return cumulativeweight;
    }

    /*
     * cumulative weight of a block: it is defined as the own weight of this
     * block plus the sum of own weights of all blocks that approve our block
     * directly or indirectly.
     */
    public long recursiveUpdateCumulativeweights(Sha256Hash txHash, Map<Sha256Hash, Long> cumulativeweights,
            Set<Sha256Hash> analyzedTips) throws Exception {
        long cumulativeweight = 1;
        if (analyzedTips.add(txHash)) {

            List<Sha256Hash> approvers = blockService.getApproverBlockHashes(txHash);

            for (Sha256Hash approver : approvers) {
                cumulativeweight = capSum(cumulativeweight,
                        recursiveUpdateCumulativeweights(approver, cumulativeweights, analyzedTips),
                        Long.MAX_VALUE / 2);
            }
            cumulativeweights.put(txHash, cumulativeweight);
        } else {
            if (cumulativeweights.containsKey(txHash)) {
                cumulativeweight = cumulativeweights.get(txHash);
            } else {
                cumulativeweight = 0;
            }
        }
        return cumulativeweight;
    }

    /*
     * the length of the longest reverse-oriented path to some tip.
     */
    public void recursiveUpdateDepth(Sha256Hash start, Map<Sha256Hash, Long> depths) throws Exception {
        Map<Sha256Hash, Set<Sha256Hash>> blockCumulativeweights1 = new HashMap<Sha256Hash, Set<Sha256Hash>>();
        updateHashCumulativeweights(start, blockCumulativeweights1, new HashSet<>());

        Iterator it = blockCumulativeweights1.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Sha256Hash, Set<Sha256Hash>> pair = (Map.Entry<Sha256Hash, Set<Sha256Hash>>) it.next();
            depths.put(pair.getKey(), new Long(pair.getValue().size()));
            // System.out.println(
            // "hash : " + pair.getKey() + " \n size " + pair.getValue().size()
            // + "-> " + pair.getValue());
        }

    }

    /*
     * the length of the longest reverse-oriented path to some tip.
     */
    public void recursiveUpdateDepth(Sha256Hash tip, Map<Sha256Hash, Long> depths, Set<Sha256Hash> analyzedBlocks)
            throws Exception {

        Queue<Sha256Hash> nonAnalyzedBlocks = new LinkedList<>(Collections.singleton(tip));
        Sha256Hash txHash;
        while ((txHash = nonAnalyzedBlocks.poll()) != null) {
            Long depth = depths.get(txHash);
            if (depth != null) {
                depths.put(txHash, depth + 1l);
            } else {
                depths.put(txHash, 1l);
            }
            if (analyzedBlocks.add(txHash) && !txHash.equals(Sha256Hash.ZERO_HASH)) {
                Block b = blockService.getBlock(txHash);
                nonAnalyzedBlocks.offer(b.getPrevBlockHash());
                nonAnalyzedBlocks.offer(b.getPrevBranchBlockHash());

            }
        }
    }

    boolean belowMaxDepth(Sha256Hash tip, int depth, Set<Sha256Hash> maxDepthOk) throws Exception {
        // if tip is confirmed stop
        /*
         * if (BlockViewModel.fromHash(tangle, tip).snapshotIndex() >= depth) {
         * return false; }
         */
        // if tip unconfirmed, check if any referenced tx is confirmed below
        // maxDepth
        Queue<Sha256Hash> nonAnalyzedBlocks = new LinkedList<>(Collections.singleton(tip));
        Set<Sha256Hash> analyzeds = new HashSet<>();
        Sha256Hash hash;
        while ((hash = nonAnalyzedBlocks.poll()) != null) {
            if (analyzeds.add(hash)) {
                BlockEvaluation b = blockService.getBlockEvaluation(hash);
                if (b == null)
                    return false;
                if (b.getDepth() < depth) {
                    return true;
                } else {
                    nonAnalyzedBlocks.offer(blockService.getBlock(b.getBlockhash()).getPrevBlockHash());
                    nonAnalyzedBlocks.offer(blockService.getBlock(b.getBlockhash()).getPrevBranchBlockHash());

                }
            }
        }
        maxDepthOk.add(tip);
        return false;
    }

	public void addTip(Sha256Hash blockhash) throws BlockStoreException {
		StoredBlock block = store.get(blockhash);
		store.deleteTip(block.getHeader().getPrevBlockHash());
		store.deleteTip(block.getHeader().getPrevBranchBlockHash());
		 store.deleteTip(blockhash); 
		store.insertTip(blockhash);
	}
}

/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.Stack;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
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
    private BlockService blockService;
    @Autowired
    private TipsViewModel tipsViewModel;
    @Autowired
    private BlockValidator blockValidator;
    @Autowired
    protected NetworkParameters networkParameters;

    private int RATING_THRESHOLD = 75; // Must be in [0..100] range

    private boolean shuttingDown = false;
    private int RESCAN_TX_TO_REQUEST_INTERVAL = 750;
    private int maxDepth;
    private Thread solidityRescanHandle;

    public void setRATING_THRESHOLD(int value) {
        if (value < 0)
            value = 0;
        if (value > 100)
            value = 100;
        RATING_THRESHOLD = value;
    }

    public void init() {
        solidityRescanHandle = new Thread(() -> {

            while (!shuttingDown) {
                try {
                    scanTipsForSolidity();
                } catch (Exception e) {
                    log.error("Error during solidity scan : {}", e);
                }
                try {
                    Thread.sleep(RESCAN_TX_TO_REQUEST_INTERVAL);
                } catch (InterruptedException e) {
                    log.error("Solidity rescan interrupted.");
                }
            }
        }, "Tip Solidity Rescan");
        solidityRescanHandle.start();
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
                // TransactionViewModel.fromHash(hash).isSolid() && isTip) {
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
        Map<Sha256Hash, Long> ratings = new HashMap<Sha256Hash, Long>();
        Set<Sha256Hash> analyzedTips = new HashSet<Sha256Hash>();
        Set<Sha256Hash> maxDepthOk = new HashSet<Sha256Hash>();
        try {
            Sha256Hash entryPointTipSha256Hash = entryPoint(reference);
            serialUpdateRatings(entryPointTipSha256Hash, ratings, analyzedTips, extraTip);
            System.out.println(ratings);
            analyzedTips.clear();
            return markovChainMonteCarlo(entryPointTipSha256Hash, extraTip, ratings, iterations, milestone.latestSolidSubtangleMilestoneIndex - depth * 2, maxDepthOk, seed);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Encountered error: " + e.getLocalizedMessage());
            throw e;
        }
    }

    Sha256Hash entryPoint(final Sha256Hash sha256Hash) throws Exception {
        Block block = this.blockService.getBlock(sha256Hash);
        return block.getHash();
    }

    Sha256Hash markovChainMonteCarlo(final Sha256Hash tip, final Sha256Hash extraTip,
            final Map<Sha256Hash, Long> ratings, final int iterations, final int maxDepth,
            final Set<Sha256Hash> maxDepthOk, final Random seed) throws Exception {
        Map<Sha256Hash, Integer> monteCarloIntegrations = new HashMap<>();
        Sha256Hash tail;
        for (int i = iterations; i-- > 0;) {
            tail = randomWalk(tip, extraTip, ratings, maxDepth, maxDepthOk, seed);
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

    Sha256Hash randomWalk(final Sha256Hash start, final Sha256Hash extraTip, final Map<Sha256Hash, Long> ratings,
            final int maxDepth, final Set<Sha256Hash> maxDepthOk, Random rnd) throws Exception {
        Sha256Hash tip = start, tail = tip;
        Sha256Hash[] tips;

        Set<Sha256Hash> analyzedTips = new HashSet<>();
        int traversedTails = 0;

        int approverIndex;
        double ratingWeight;
        double[] walkRatings;

        while (tip != null) {

            List<StoredBlock> approvers = blockService.getApproverBlocks(tip);

            if (approvers.size() == 0) { // TODO check solidity
                log.info("Reason to stop:  is a tip =" + tip);
                break;
            } else if (approvers.size() == 1) {

                tip = approvers.get(0).getHeader().getHash();

            } else {
                tips = approvers.toArray(new Sha256Hash[approvers.size()]);
                if (!ratings.containsKey(tip)) {
                    serialUpdateRatings(tip, ratings, analyzedTips, extraTip);
                    analyzedTips.clear();
                }

                walkRatings = new double[tips.length];
                double maxRating = 0;
                long tipRating = ratings.get(tip);
                for (int i = 0; i < tips.length; i++) {
                    // transition probability = ((Hx-Hy)^-3)/maxRating
                    walkRatings[i] = Math.pow(tipRating - ratings.getOrDefault(tips[i], 0L), -3);
                    maxRating += walkRatings[i];
                }
                ratingWeight = rnd.nextDouble() * maxRating;
                for (approverIndex = tips.length; approverIndex-- > 1;) {
                    ratingWeight -= walkRatings[approverIndex];
                    if (ratingWeight <= 0) {
                        break;
                    }
                }
                tip = tips[approverIndex];

            }
        }
        log.info("Tx traversed to find tip: " + traversedTails);
        // messageQ.publish("mctn %d", traversedTails);
        return tail;
    }

    static long capSum(long a, long b, long max) {
        if (a + b < 0 || a + b > max) {
            return max;
        }
        return a + b;
    }

    void serialUpdateRatings(final Sha256Hash txHash, final Map<Sha256Hash, Long> ratings, final Set<Sha256Hash> analyzedTips, final Sha256Hash extraTip) throws Exception {
        Stack<Sha256Hash> hashesToRate = new Stack<Sha256Hash>();
        hashesToRate.push(txHash);
        Sha256Hash currentHash;
        boolean addedBack;
        while (!hashesToRate.empty()) {
            currentHash = hashesToRate.pop();
            List<StoredBlock> approvers = blockService.getApproverBlocks(txHash);
            addedBack = false;
            for (StoredBlock approver : approvers) {
                if (ratings.get(approver.getHeader().getHash()) == null
                        && !approver.getHeader().getHash().equals(currentHash)) {
                    if (!addedBack) {
                        addedBack = true;
                        hashesToRate.push(currentHash);
                    }
                    hashesToRate.push(approver.getHeader().getHash());
                }
            }
            if (!addedBack && analyzedTips.add(currentHash)) {
                long rating = approvers.stream().map(ratings::get).filter(Objects::nonNull)
                        .reduce((a, b) -> capSum(a, b, Long.MAX_VALUE / 2)).orElse(0L);
                ratings.put(currentHash, rating);
            }
        }
    }

    public Set<Sha256Hash> updateHashRatings(Sha256Hash txHash, Map<Sha256Hash, Set<Sha256Hash>> ratings,
            Set<Sha256Hash> analyzedTips) throws Exception {
        Set<Sha256Hash> rating;
        if (analyzedTips.add(txHash)) {
            List<StoredBlock> approvers = blockService.getApproverBlocks(txHash);
            rating = new HashSet<>(Collections.singleton(txHash));
            for (StoredBlock approver : approvers) {
                rating.addAll(updateHashRatings(approver.getHeader().getHash(), ratings, analyzedTips));
            }
            ratings.put(txHash, rating);
        } else {
            if (ratings.containsKey(txHash)) {
                rating = ratings.get(txHash);
            } else {
                rating = new HashSet<>();
            }
        }
        return rating;
    }

    long recursiveUpdateRatings(Sha256Hash txHash, Map<Sha256Hash, Long> ratings, Set<Sha256Hash> analyzedTips)
            throws Exception {
        long rating = 1;
        if (analyzedTips.add(txHash)) {

            List<StoredBlock> approvers = blockService.getApproverBlocks(txHash);

            for (StoredBlock approver : approvers) {
                rating = capSum(rating, recursiveUpdateRatings(approver.getHeader().getHash(), ratings, analyzedTips),
                        Long.MAX_VALUE / 2);
            }
            ratings.put(txHash, rating);
        } else {
            if (ratings.containsKey(txHash)) {
                rating = ratings.get(txHash);
            } else {
                rating = 0;
            }
        }
        return rating;
    }

}

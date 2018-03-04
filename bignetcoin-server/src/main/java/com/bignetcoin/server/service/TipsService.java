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

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bignetcoin.server.model.BlockEvaluation;
import com.bignetcoin.server.model.BlockEvaluation;
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

    Sha256Hash blockToApprove(final Snapshot referenceSnapshot, final Sha256Hash reference, final Sha256Hash extraTip,
            final int depth, final int iterations, Random seed) throws Exception {

        long startTime = System.nanoTime();
        final int msDepth;
        if (depth > maxDepth) {
            msDepth = maxDepth;
        } else {
            msDepth = depth;
        }

        if (milestone.latestSolidSubtangleMilestoneIndex > MilestoneService.MILESTONE_START_INDEX
                || milestone.latestMilestoneIndex == MilestoneService.MILESTONE_START_INDEX) {

            Map<Sha256Hash, Long> ratings = new HashMap<>();
            Set<Sha256Hash> analyzedTips = new HashSet<>();
            Set<Sha256Hash> maxDepthOk = new HashSet<>();
            try {
                Sha256Hash tip = entryPoint(reference, extraTip, msDepth);
                serialUpdateRatings(referenceSnapshot, tip, ratings, analyzedTips, extraTip);
                analyzedTips.clear();

            } catch (Exception e) {
                e.printStackTrace();
                log.error("Encountered error: " + e.getLocalizedMessage());
                throw e;
            } finally {
                // API.incEllapsedTime_getTxToApprove(System.nanoTime() -
                // startTime);
            }
        }
        return null;
    }

    Sha256Hash entryPoint(final Sha256Hash reference, final Sha256Hash extraTip, final int depth) throws Exception {

        if (extraTip == null) {
            // trunk
            return reference != null ? reference : milestone.latestSolidSubtangleMilestone;
        }

        // branch (extraTip)
        int milestoneIndex = Math.max(milestone.latestSolidSubtangleMilestoneIndex - depth - 1, 0);

        return milestone.latestSolidSubtangleMilestone;
    }

    Sha256Hash markovChainMonteCarlo(final Snapshot referenceSnapshot, final Sha256Hash tip, final Sha256Hash extraTip,
            final Map<Sha256Hash, Long> ratings, final int iterations, final int maxDepth,
            final Set<Sha256Hash> maxDepthOk, final Random seed) throws Exception {
        Map<Sha256Hash, Integer> monteCarloIntegrations = new HashMap<>();
        Sha256Hash tail;
        for (int i = iterations; i-- > 0;) {
            tail = randomWalk(new Snapshot(referenceSnapshot), tip, extraTip, ratings, maxDepth, maxDepthOk, seed);
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

    Sha256Hash randomWalk(final Snapshot snapshot, final Sha256Hash start, final Sha256Hash extraTip,
            final Map<Sha256Hash, Long> ratings, final int maxDepth, final Set<Sha256Hash> maxDepthOk, Random rnd)
            throws Exception {
        Sha256Hash tip = start, tail = tip;
        Sha256Hash[] tips;

        Set<Sha256Hash> analyzedTips = new HashSet<>();
        int traversedTails = 0;
        BlockEvaluation blockViewModel;
        int approverIndex;
        double ratingWeight;
        double[] walkRatings;
        List<Sha256Hash> extraTipList = null;
        if (extraTip != null) {
            extraTipList = Collections.singletonList(extraTip);
        }

        while (tip != null) {
            blockViewModel = blockService.getBlockEvaluation(tip);
            List<StoredBlock> tipSet = blockService.getApproverBlocks(blockViewModel.getBlockhash());

            if (tipSet.size() == 0) {
                log.info("Reason to stop: TransactionViewModel is a tip");
                // messageQ.publish("rtst %s", tip);
                break;
            } else if (tipSet.size() == 1) {

                tip = tipSet.get(0).getHeader().getHash();

            } else {
                // walk to the next approver
                tips = tipSet.toArray(new Sha256Hash[tipSet.size()]);
                if (!ratings.containsKey(tip)) {
                    serialUpdateRatings(snapshot, tip, ratings, analyzedTips, extraTip);
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
                if (blockViewModel.getBlockhash().equals(tip)) {
                    log.info("Reason to sthp: blockViewModel==itself");
                    // messageQ.publish("rtsl %s", blockViewModel.getHash());
                    break;
                }
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

    void serialUpdateRatings(final Snapshot snapshot, final Sha256Hash txHash, final Map<Sha256Hash, Long> ratings,
            final Set<Sha256Hash> analyzedTips, final Sha256Hash extraTip) throws Exception {
        Stack<Sha256Hash> hashesToRate = new Stack<>();
        hashesToRate.push(txHash);
        Sha256Hash currentHash;
        boolean addedBack;
        while (!hashesToRate.empty()) {
            currentHash = hashesToRate.pop();
            BlockEvaluation blockViewModel = blockService.getBlockEvaluation(currentHash);
            List<StoredBlock> approvers = blockService.getApproverBlocks(blockViewModel.getBlockhash());

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

    Set<Sha256Hash> updateHashRatings(Sha256Hash txHash, Map<Sha256Hash, Set<Sha256Hash>> ratings,
            Set<Sha256Hash> analyzedTips) throws Exception {
        Set<Sha256Hash> rating;
        if (analyzedTips.add(txHash)) {

            BlockEvaluation blockViewModel = blockService.getBlockEvaluation(txHash);
            List<StoredBlock> approvers = blockService.getApproverBlocks(blockViewModel.getBlockhash());

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
            BlockEvaluation blockViewModel = blockService.getBlockEvaluation(txHash);
            List<StoredBlock> approvers = blockService.getApproverBlocks(blockViewModel.getBlockhash());

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

    public List<Sha256Hash> blockToApprove(int depth, String reference, int numWalks) {
        // TODO Auto-generated method stub
        return null;
    }

}

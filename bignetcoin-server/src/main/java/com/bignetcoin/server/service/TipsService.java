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
import java.util.Random;
import java.util.Set;
import java.util.Stack;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class TipsService {

    private final Logger log = LoggerFactory.getLogger(TipsService.class);
    @Autowired
    private   MilestoneService milestone;

    @Autowired
    private   BlockService blockService;
    
    private int RATING_THRESHOLD = 75; // Must be in [0..100] range
 
    int maxDepth;

    public void setRATING_THRESHOLD(int value) {
        if (value < 0)
            value = 0;
        if (value > 100)
            value = 100;
        RATING_THRESHOLD = value;
    }

    public Sha256Hash blockToApprove(final Set<Sha256Hash> visitedHashes, final Map<Sha256Hash, Long> diff, final Sha256Hash reference,
            final Sha256Hash extraTip, int depth, final int iterations, Random seed) throws Exception {

 
        return null;
    }


    Sha256Hash markovChainMonteCarlo(final Set<Sha256Hash> visitedHashes, final Map<Sha256Hash, Long> diff, final Sha256Hash tip,
            final Sha256Hash extraTip, final Map<Sha256Hash, Long> ratings, final int iterations, final int maxDepth,
            final Set<Sha256Hash> maxDepthOk, final Random seed) throws Exception {
        Map<Sha256Hash, Integer> monteCarloIntegrations = new HashMap<>();
        Sha256Hash tail;
        for (int i = iterations; i-- > 0;) {
            tail = randomWalk(visitedHashes, diff, tip, extraTip, ratings, maxDepth, maxDepthOk, seed);
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

    Sha256Hash randomWalk(final Set<Sha256Hash> visitedHashes, final Map<Sha256Hash, Long> diff, final Sha256Hash start, final Sha256Hash extraTip,
            final Map<Sha256Hash, Long> ratings, final int maxDepth, final Set<Sha256Hash> maxDepthOk, Random rnd)
            throws Exception {
        Sha256Hash tip = start, tail = tip;
        Sha256Hash[] tips;
    
        Set<Sha256Hash> analyzedTips = new HashSet<>();
        int traversedTails = 0;
      
        int approverIndex;
        double ratingWeight;
        double[] walkRatings;
        List<Sha256Hash> extraTipList = null;
        if (extraTip != null) {
            extraTipList = Collections.singletonList(extraTip);
        }
        Map<Sha256Hash, Long> myDiff = new HashMap<>(diff);
        Set<Sha256Hash> myApprovedHashes = new HashSet<>(visitedHashes);

        while (tip != null) {
            Block block = blockService.getBlock(  tip);
            StoredBlock prev = blockService.getPrevBlock(tip);
            if (prev  == null) { 
                    break;
                }
                // set the tail here!
                tail = tip;
                traversedTails++; 
            /*
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
                if (block.getHash().equals(tip)) {
                    log.info("Reason to stop: block==itself");
                   
                    break;
                }
                */
            }
         
        log.info("Tx traversed to find tip: " + traversedTails);
     
        return tail;
    }

    static long capSum(long a, long b, long max) {
        if (a + b < 0 || a + b > max) {
            return max;
        }
        return a + b;
    }

    void serialUpdateRatings(final Set<Sha256Hash> visitedHashes, final Sha256Hash txHash, final Map<Sha256Hash, Long> ratings,
            final Set<Sha256Hash> analyzedTips, final Sha256Hash extraTip) throws Exception {
        Stack<Sha256Hash> hashesToRate = new Stack<>();
        hashesToRate.push(txHash);
        Sha256Hash currentHash;
        boolean addedBack;
        while (!hashesToRate.empty()) {
            currentHash = hashesToRate.pop();
            Block block = blockService.getBlock(  currentHash);
           
            addedBack = false;
            /*
            Set<Sha256Hash> approvers = block.getApprovers(tangle).getHashes();
            for (Sha256Hash approver : approvers) {
                if (ratings.get(approver) == null && !approver.equals(currentHash)) {
                    if (!addedBack) {
                        addedBack = true;
                        hashesToRate.push(currentHash);
                    }
                    hashesToRate.push(approver);
                }
            }
            if (!addedBack && analyzedTips.add(currentHash)) {
                long rating = (extraTip != null && visitedHashes.contains(currentHash) ? 0 : 1)
                        + approvers.stream().map(ratings::get).filter(Objects::nonNull)
                                .reduce((a, b) -> capSum(a, b, Long.MAX_VALUE / 2)).orElse(0L);
                ratings.put(currentHash, rating);
            }
            */
        }
        
    }

  
    long recursiveUpdateRatings(Sha256Hash txHash, Map<Sha256Hash, Long> ratings, Set<Sha256Hash> analyzedTips) throws Exception {
        long rating = 1;
        if (analyzedTips.add(txHash)) {
            Block block = blockService.getBlock(  txHash);
            Set<Sha256Hash> approverHashes = new HashSet<Sha256Hash>();
            approverHashes.add( block.getPrevBlockHash());
            approverHashes.add( block.getPrevBranchBlockHash());
            for (Sha256Hash approver : approverHashes) {
                rating = capSum(rating, recursiveUpdateRatings(approver, ratings, analyzedTips), Long.MAX_VALUE / 2);
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

    public int getMaxDepth() {
        return maxDepth;
    }

    boolean belowMaxDepth(Sha256Hash tip, int depth, Set<Sha256Hash> maxDepthOk) throws Exception {
        // if tip is confirmed stop
       
        // if tip unconfirmed, check if any referenced tx is confirmed below
        // maxDepth
  
        maxDepthOk.add(tip);
        return false;
    }
}

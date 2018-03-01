/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.util.HashSet;
import java.util.Set;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.Sha256Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
@Service
public class MilestoneService {

    enum Validity {
        VALID, INVALID, INCOMPLETE
    }

    private final Logger log = LoggerFactory.getLogger(MilestoneService.class);

    public Snapshot latestSnapshot;

    public Sha256Hash latestMilestone = Sha256Hash.ZERO_HASH;
    public Sha256Hash latestSolidSubtangleMilestone = latestMilestone;

    public static final int MILESTONE_START_INDEX = 338000;
    private static final int NUMBER_OF_KEYS_IN_A_MILESTONE = 20;

    public int latestMilestoneIndex = MILESTONE_START_INDEX;
    public int latestSolidSubtangleMilestoneIndex = MILESTONE_START_INDEX;

    private final Set<Sha256Hash> analyzedMilestoneCandidates = new HashSet<>();

    private Validity validateMilestone(Block block) throws Exception {
        return Validity.VALID;
    }

    void updateLatestSolidSubtangleMilestone() throws Exception {

    }

}

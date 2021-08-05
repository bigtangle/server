/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.CutoffException;
import net.bigtangle.core.exception.VerificationException.InfeasiblePrototypeException;
import net.bigtangle.core.response.GetTXRewardListResponse;
import net.bigtangle.core.response.GetTXRewardResponse;
import net.bigtangle.server.config.ScheduleConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.core.ConflictCandidate;
import net.bigtangle.server.data.LockObject;
import net.bigtangle.server.data.OrderMatchingResult;
import net.bigtangle.server.data.SolidityState;
import net.bigtangle.server.service.ValidatorService.RewardBuilderResult;
import net.bigtangle.store.FullBlockGraph;
import net.bigtangle.store.FullBlockStore;

/**
 * <p>
 * A RewardService provides service for create and validate the reward chain.
 * </p>
 */
@Service
public class AVGPriceService {

    @Autowired
    protected FullBlockGraph blockGraph;
    @Autowired
    protected ServerConfiguration serverConfiguration;

    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    private StoreService storeService;
    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private  final   String  LOCKID = this.getClass().getName();

    /**
     * Scheduled update function that updates the Tangle
     * 
     * @throws BlockStoreException
     */

    // createReward is time boxed and can run parallel.
    public void startSingleProcessCalAdd() throws BlockStoreException {

        FullBlockStore store = storeService.getStore();

        try {
            // log.info("create Reward started");
            LockObject lock = store.selectLockobject(LOCKID);
            boolean canrun = false;
            if (lock == null) {
                store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
                canrun = true;
            } else if (lock.getLocktime() < System.currentTimeMillis() - scheduleConfiguration.getMiningrate()) {
                store.deleteLockobject(LOCKID);
                store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
                canrun = true;
            } else {
                log.info("AVGPriceService running return:  " + Utils.dateTimeFormat(lock.getLocktime()));
            }
            if (canrun) {
                store.batchAddAvgPrice();
                store.deleteLockobject(LOCKID);
            }

        } catch (Exception e) {
            log.error("create Reward end  ", e);
            store.deleteLockobject(LOCKID);
        } finally {
            store.close();
        }

    }

}

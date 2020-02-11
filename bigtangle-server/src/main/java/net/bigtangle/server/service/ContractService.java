/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.Threading;

/**
 * <p>
 * A ContractService provides service for create and validate the contract execution chain.
 * Contract is a token with code can be executed with inputs
 *  contract balance  +  contract event 
 *  Execution Result is new  contract balance + remain contract event 
 * </p>
 */
@Service
public class ContractService {

    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    protected FullPrunedBlockGraph blockGraph;
    @Autowired
    private BlockService blockService;
    @Autowired
    protected TipsService tipService;
    @Autowired
    protected ServerConfiguration serverConfiguration;
    @Autowired
    private ValidatorService validatorService;
    @Autowired
    protected NetworkParameters networkParameters;
    private final Logger log = LoggerFactory.getLogger(this.getClass());
  

    /**
     * Scheduled update function that updates the Tangle
     * 
     * @throws BlockStoreException
     */

    protected final ReentrantLock lock = Threading.lock("RewardService");

    // createReward is time boxed and can run parallel.
    public void startSingleProcess() {
        if (lock.isHeldByCurrentThread() || !lock.tryLock()) {
            log.debug(this.getClass().getName() + "  RewardService running. Returning...");
            return;
        }
     
        try {
            // log.info("create Reward started");
        //    createReward();
        } catch (Exception e) {
            log.error("create Reward end  ", e);
        } finally {
             lock.unlock();
        }

    }

    
}

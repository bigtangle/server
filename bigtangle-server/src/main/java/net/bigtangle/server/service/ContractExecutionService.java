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
 *  contract account  +  contract event 
 *  Execution Result is new  contract account
 *  Contract execution is recoded in contractexection and every execution is point to prev execution and forms a blockchain.
 *   
 * </p>
 */
@Service
public class ContractExecutionService {

 
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

    protected final ReentrantLock lock = Threading.lock("ContractExecutionService");
 
    public void startSingleProcess() {
        if (lock.isHeldByCurrentThread() || !lock.tryLock()) {
            log.debug(this.getClass().getName() + "  ContractExecutionService running. Returning...");
            return;
        }
     
        try {
            // log.info("create Reward started");
        	//startContractExecution();
        } catch (Exception e) {
            log.error(" ContractExecution  end  ", e);
        } finally {
             lock.unlock();
        }

    }

    
}

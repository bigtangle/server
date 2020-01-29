/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.blockconfirm.schedule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.bigtangle.blockconfirm.config.ScheduleConfiguration;
import net.bigtangle.blockconfirm.config.ServerConfiguration;
import net.bigtangle.blockconfirm.store.FullPrunedBlockStore;
import net.bigtangle.core.Block;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.GetBlockEvaluationsResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.Threading;

@Component
@EnableAsync
public class BlockRetryService {

    private static final Logger logger = LoggerFactory.getLogger(BlockRetryService.class);

    @Autowired
    protected FullPrunedBlockStore store;

    @Autowired
    private NetworkParameters networkParameters;
  
    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Autowired
    ServerConfiguration serverConfiguration;
    private static   String CHECHNUMBER = "2000";
    private static   String server="https://p.bigtangle.org:8088";
    @Scheduled(fixedRate = 10000)

    public void batch() {
             startSingleProcess();
       
    }

    protected final ReentrantLock lock = Threading.lock("BlockRetryService");

    public void startSingleProcess() {
        if (lock.isHeldByCurrentThread() || !lock.tryLock()) {
            logger.debug(this.getClass().getName() + "  Update already running. Returning...");
            return;
        }

        logger.info("BlockRetryService start");
        try {
            retryBlocks();
        } catch (Exception e) {
            logger.info("BlockBatchService error", e);
        } finally {
            lock.unlock();
        }

    }

    /*
     * if a block is failed due to rating without conflict, it can be saved by
     * setting new BlockPrototype.
     */
    private void retryBlocks() throws BlockStoreException, Exception {
        List<Block> bs= getBlockInfos(server);
  
        
   
    }
    private List<Block> getBlockInfos(String server) throws Exception {
        String CONTEXT_ROOT = server;
 
        Map<String, Object> requestParam = new HashMap<String, Object>(); 
        String response = OkHttp3Util.postString(CONTEXT_ROOT + "/" + ReqCmd.findRetryBlocks.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
          getBlockEvaluationsResponse.getEvaluations();
          return new ArrayList<Block>();
    }
 
}

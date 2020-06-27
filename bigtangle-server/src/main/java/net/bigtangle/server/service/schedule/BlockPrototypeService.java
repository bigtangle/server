/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.schedule;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.server.config.ScheduleConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.StoreService;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.Threading;

@Component
@EnableAsync
public class BlockPrototypeService {

    private static final Logger logger = LoggerFactory.getLogger(BlockPrototypeService.class);

    @Autowired
    protected  StoreService storeService;

    @Autowired
    private BlockService blockService;

    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Autowired
    ServerConfiguration serverConfiguration;

   // @Scheduled(fixedRate = 1000)
    public void batch() {
        if (scheduleConfiguration.isMilestone_active() && serverConfiguration.checkService()) {
            startSingleProcess();
        }

    }

    protected final ReentrantLock lock = Threading.lock("BlockPrototypeService");

    public void startSingleProcess() {
        if (lock.isHeldByCurrentThread() || !lock.tryLock()) {
            logger.debug(this.getClass().getName() + "  Update already running. Returning...");
            return;
        }

        logger.info("BlockPrototypeService start");
        try {
           // storeService.getStore().deleteBlockPrototypeTimeout();
           //TODO too many DB connections timeboxed();
            timeboxed();
        } catch (Exception e) {
            logger.info("BlockPrototypeService error", e);
        } finally {
            lock.unlock();
        }

    }

    private void blockprototype(FullPrunedBlockStore store) throws BlockStoreException, Exception {
        this.blockService.createBlockPrototypeCache(store  );
    }
    
    private void timeboxed( )
            throws InterruptedException, ExecutionException, BlockStoreException {
        final Duration timeout = Duration.ofSeconds(serverConfiguration.getSolveRewardduration());
        ExecutorService executor = Executors.newSingleThreadExecutor();
         FullPrunedBlockStore store= storeService.getStore();
         store .deleteBlockPrototypeTimeout();
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final Future<String> handler = executor.submit(new Callable() {
            @Override
            public String call() throws Exception {
                logger.debug(" blockprototype  started  : "  );
                blockService.createBlockPrototypeCache(store);
                return "";
            }
        });
        Stopwatch watch = Stopwatch.createStarted();
        try {
            handler.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.debug(" blockprototype Timeout  ");
            handler.cancel(true);
         
        } finally {
            executor.shutdownNow();
            store.close();
        }
        logger.debug("blockprototype time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
        
    }

}

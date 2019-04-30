/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.kafka.common.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Service;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.health.KafkaHealthIndicator;
import net.bigtangle.kafka.BlockStreamHandler;
import net.bigtangle.lifecycle.LifecycleStatus;
import net.bigtangle.lifecycle.StatusCollector;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.Threading;

/**
 * <p>
 * Provides services for blocks.
 * </p>
 */
@Service
public class HeathCheckService {
    private static final Logger log = LoggerFactory.getLogger(HeathCheckService.class);

    protected static final String BlockStreamHandler_STREAM_HDL = "blockStreamHandler";
    protected static final String DATABASE_NAME = "database";

    @Autowired
    ServerConfiguration serverConfiguration;
    @Autowired
    protected FullPrunedBlockStore store;

    @Autowired
    protected NetworkParameters networkParameters;

    @Autowired
    protected BlockStreamHandler blockStreamHandler;

    @Autowired
    protected KafkaHealthIndicator kafkaHealthIndicator;

    protected final ReentrantLock lock = Threading.lock("HeathCheckService");

    public void startSingleProcess() {
        if (!lock.tryLock()) {
            log.debug(this.getClass().getName() + "  HeathCheckService running. Returning...");
            return;
        }

        try {

            StatusCollector status = checkStatus();
            if (!status.isStatus()) {
                Thread.sleep(1000);
                // second check then set the server to not running
                if (!checkStatus().isStatus()) {

                    log.error("  HeathCheckService  status is not running setServiceWait " + status.getFailedMessage());

                    serverConfiguration.setServiceWait();
                }
                ;
            }

        } catch (Exception e) {
            log.warn("HeathCheckService ", e);
        } finally {
            lock.unlock();

        }
    }

    public StatusCollector checkStatus() {
        StatusCollector statusCollector = new StatusCollector();

        checkDB(statusCollector);
        if (kafkaHealthIndicator.kafkaStart()) {
            // checkKafkaStream(statusCollector);
            checkKafka(statusCollector);
        }
        return statusCollector;

    }

    private StatusCollector checkDB(StatusCollector status) {

        try {
            store.getSettingValue("version");
            status.setOkMessage(DATABASE_NAME);

        } catch (Exception e) {
            log.error("database is down:" + e.getMessage(), e);
            status.setFailedMessage(DATABASE_NAME);
        }
        return status;
    }

    private StatusCollector checkKafka(StatusCollector status) {
        try {

            Node controllerFound = kafkaHealthIndicator.check();

            if (controllerFound != null) {

                status.setFailedMessage("Kafka cluster node down");
            } else {
                status.setOkMessage("kafka");
            }
        } catch (Exception e) {
            log.error("Kafka down:" + e.getMessage(), e);
            status.setFailedMessage("kafka");
        }
        return status;
    }

    private StatusCollector checkKafkaStream(StatusCollector status) {
        try {
            final StringBuilder st = new StringBuilder();

            if (blockStreamHandler == null || !blockStreamHandler.isRunning()) {
                log.debug("checkKafka: OUT-ERR1");
                st.append(BlockStreamHandler_STREAM_HDL);
            }
            if (st.length() > 0) {
                log.error("Kafka ndown:" + st.toString());
                status.setFailedMessage("Kafka(" + st.toString() + ")");
            } else {
                status.setOkMessage("kafka");
            }
        } catch (Exception e) {
            log.error("Kafka down:" + e.getMessage(), e);
            status.setFailedMessage("kafka");
        }
        return status;
    }

}

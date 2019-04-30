package net.bigtangle.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import net.bigtangle.kafka.AbstractStreamHandler;
import net.bigtangle.store.FullPrunedBlockStore;

public abstract class LifecycleBasisController {
    private static final Logger log = LoggerFactory.getLogger(LifecycleBasisController.class);
    protected static final String BlockStreamHandler_STREAM_HDL = "blockStreamHandler";
    protected static final String DATABASE_NAME = "database";

    private final ApplicationContext appContext;

    protected LifecycleBasisController(ApplicationContext appContext) {
        this.appContext = appContext;
    }

    public LifecycleStatus checkStatus() {
        StatusCollector statusCollector = new StatusCollector();
        try {
            checkDefault(statusCollector);
            checkCassandra(statusCollector);
            checkKafka(statusCollector);
            return buildLifecycleStatus(statusCollector);
        } catch (Exception exp) {
            log.error("Liveness/Readiness Check failed:" + exp.getMessage(), exp);
            statusCollector.setFailedMessage("Liveness/Readiness Check failed:" + exp.getMessage());
            return buildLifecycleStatus(statusCollector);
        }
    }

    public LifecycleStatus afterStartContainer() {
        StatusCollector statusCollector = new StatusCollector();
        statusCollector.setOkMessage("afterStartContainer");
        return buildLifecycleStatus(statusCollector);
    }

    private StatusCollector checkDefault(StatusCollector status) {
        status.setOkMessage("default");
        return status;
    }

    private StatusCollector checkCassandra(StatusCollector status) {

        try {
            FullPrunedBlockStore repository = findStore();
            // only a generic access-test ...
            if (repository == null) {
                status.setFailedMessage(DATABASE_NAME);
            } else {
                repository.getSettingValue("version");
                status.setOkMessage(DATABASE_NAME);
            }
        } catch (Exception e) {
            log.error("database is down:" + e.getMessage(), e);
            status.setFailedMessage(DATABASE_NAME);
        }
        return status;
    }

    private StatusCollector checkKafka(StatusCollector status) {
        try {
            final StringBuilder st = new StringBuilder();
            final AbstractStreamHandler blockStreamHandler = findKafka(BlockStreamHandler_STREAM_HDL);
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

    private AbstractStreamHandler findKafka(String beanName) {
        AbstractStreamHandler bean = (AbstractStreamHandler) appContext.getBean(beanName);
        if (bean == null) {
            log.error(beanName + " - bean not found");
        }
        return bean;
    }

    private FullPrunedBlockStore findStore() {
        FullPrunedBlockStore bean = appContext.getBean(FullPrunedBlockStore.class);
        if (bean == null) {
            log.error("FullPrunedBlockStore - bean not found");
        }
        return bean;
    }

    private LifecycleStatus buildLifecycleStatus(StatusCollector collected) {
        LifecycleStatus lifecycleStatus;
        if (collected.isStatus()) {
            lifecycleStatus = new LifecycleStatus(LifecycleStatus.STATUS_OK,
                    "OK:" + collected.getOkMessage().toString());
        } else {
            lifecycleStatus = new LifecycleStatus(LifecycleStatus.STATUS_NOTOK,
                    "OK:" + collected.getOkMessage().toString() + ";NOT_OK:" + collected.getFailedMessage().toString());
        }
        log.debug(lifecycleStatus.getStatusText());
        return lifecycleStatus;
    }
}

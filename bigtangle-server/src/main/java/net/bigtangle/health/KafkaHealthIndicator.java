package net.bigtangle.health;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

import javax.annotation.PostConstruct;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import net.bigtangle.kafka.KafkaConfiguration;

@Component
public class KafkaHealthIndicator implements HealthIndicator {
    private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);
    private AdminClient adminClient;

    private final KafkaConfiguration kafkaProperties;

    @Autowired
    public KafkaHealthIndicator(KafkaConfiguration kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @PostConstruct
    public void initAdminClient() {
        if (kafkaStart())
            this.adminClient = AdminClient.create(prepareConfiguration());
    }

    @Override
    public Health health() {
        Node controllerFound = null;
        Health.Builder health = Health.up();
        if (kafkaStart()) {
            controllerFound = check();
            health = controllerFound != null ? Health.up() : Health.down();
        }
        return health.withDetail("controller", String.valueOf(controllerFound)).build();
    }

    public Node check() {
        
        try {
            if (kafkaStart()) {
            return adminClient.describeCluster().controller().get();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted  describe cluster", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignored) {
            // Controller not found, do nothing
        }

        return null;
    }

    private Properties prepareConfiguration() {
        Properties config = new Properties();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000);
        return config;
    }

    public boolean kafkaStart() {
        if (kafkaProperties.getBootstrapServers() != null && !"".equals(kafkaProperties.getBootstrapServers())) {
            return true;
        }
        return false;
    }

}

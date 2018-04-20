package net.bigtangle.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="kafka")
public class KafkaConfiguration {
 

    private String topicOutName;

 
    private String broker;

   
    private String zooKeeperServer;

 
    private String bootstrapServers;


    public String getTopicOutName() {
        return topicOutName;
    }

 
    private String consumerIdSuffix;

 
    private long commitInterval;
    
    public void setTopicOutName(String topicOutName) {
        this.topicOutName = topicOutName;
    }


    public String getBroker() {
        return broker;
    }


    public void setBroker(String broker) {
        this.broker = broker;
    }


    public String getZooKeeperServer() {
        return zooKeeperServer;
    }


    public void setZooKeeperServer(String zooKeeperServer) {
        this.zooKeeperServer = zooKeeperServer;
    }


    public String getBootstrapServers() {
        return bootstrapServers;
    }


    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }


    public String getConsumerIdSuffix() {
        return consumerIdSuffix;
    }


    public void setConsumerIdSuffix(String consumerIdSuffix) {
        this.consumerIdSuffix = consumerIdSuffix;
    }


    public long getCommitInterval() {
        return commitInterval;
    }


    public void setCommitInterval(long commitInterval) {
        this.commitInterval = commitInterval;
    }

  
}
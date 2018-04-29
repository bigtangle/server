package net.bigtangle.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="kafka")
public class KafkaConfiguration {
 

    private String topicOutName;

    private String topicOut2Name;
    
 
    private String bootstrapServers;


    public String getTopicOutName() {
        return topicOutName;
    }

 
    private String consumerIdSuffix;

 
    private long commitInterval;
    
    public void setTopicOutName(String topicOutName) {
        this.topicOutName = topicOutName;
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


    public String getTopicOut2Name() {
        return topicOut2Name;
    }


    public void setTopicOut2Name(String topicOut2Name) {
        this.topicOut2Name = topicOut2Name;
    }



    @Override
    public String toString() {
        return "KafkaConfiguration [topicOutName=" + topicOutName + ", topicOut2Name=" + topicOut2Name
                + ", bootstrapServers=" + bootstrapServers + ", consumerIdSuffix=" + consumerIdSuffix
                + ", commitInterval=" + commitInterval + "]";
    }

 

  
}
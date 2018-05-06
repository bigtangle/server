/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.kafka;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaMessageProducer {

    // public String topic;

    private String kafkaserver;
    private String topic;
    private boolean binaryMessageKey = true;

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageProducer.class);

    public KafkaMessageProducer(String kafkaserver, String topic, boolean binaryMessageKey) {
        super();
        this.kafkaserver = kafkaserver;
        this.topic = topic;
        this.binaryMessageKey = binaryMessageKey;
    }

    public KafkaMessageProducer(KafkaConfiguration kafkaConfiguration) {
        super();
        this.kafkaserver = kafkaConfiguration.getBootstrapServers();
        this.topic = kafkaConfiguration.getTopicOutName();

    }

    public boolean sendMessage(byte[] data) throws InterruptedException, ExecutionException {
        if (!"".equalsIgnoreCase(kafkaserver))
            return false;
        final String key = UUID.randomUUID().toString();
        KafkaProducer<String, byte[]> messageProducer = new KafkaProducer<String, byte[]>(producerConfig());
        ProducerRecord<String, byte[]> producerRecord = null;
        producerRecord = new ProducerRecord<String, byte[]>(topic, key, data);
        final Future<RecordMetadata> result = messageProducer.send(producerRecord);
        RecordMetadata mdata = result.get();
        // log.debug(" sendMessage " + key);
        messageProducer.close();
        return mdata != null;

    }

    public Properties producerConfig() {
        Properties producerConfig = new Properties();
        producerConfig.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaserver);
        producerConfig.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.setProperty(ProducerConfig.RETRIES_CONFIG, "0");
        producerConfig.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                binaryMessageKey ? ByteArraySerializer.class.getName() : StringSerializer.class.getName());
        producerConfig.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerConfig.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        // producerConfig.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        // KafkaAvroSerializer.class.getName());
        // producerConfig.setProperty(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
        // configuration.getSchemaRegistryUrl());
        return producerConfig;
    }

    /*
    
     */
}

package net.bigtangle.tools.kafka;

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

    private boolean binaryMessageKey = false;

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageProducer.class);

    private static final KafkaMessageProducer instance = new KafkaMessageProducer();

    public static KafkaMessageProducer getInstance() {
        return instance;
    }

    public boolean sendMessage(byte[] data) throws InterruptedException, ExecutionException {
        final String key = UUID.randomUUID().toString();
        KafkaProducer<String, byte[]> messageProducer = new KafkaProducer<String, byte[]>(producerConfig());
        ProducerRecord<String, byte[]> producerRecord = null;
        producerRecord = new ProducerRecord<String, byte[]>("bigtangle", key, data);
        final Future<RecordMetadata> result = messageProducer.send(producerRecord);
        RecordMetadata mdata = result.get();
        log.debug(" sendMessage " + key);
        messageProducer.close();
        return mdata != null;
    }

    public Properties producerConfig() {
        Properties producerConfig = new Properties();
        producerConfig.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka2.bigtangle.net:9092");
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
}

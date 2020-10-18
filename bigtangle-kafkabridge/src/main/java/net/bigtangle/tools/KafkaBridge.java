/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ser.std.StringSerializer;

/*
 * read the topic from each kafka server and sent it to all other kafka servers on topic 2
 * 
 */
public class KafkaBridge {

    private static final Logger log = LoggerFactory.getLogger(KafkaBridge.class);

    String kafkaServerFrom = "10.1.70.12:9092,10.1.70.13:9092";
    String kafkaServerTo = "test.kafka.bigtangle.de:9092";
    String topicFrom ="bigtangle";
    String topicTo ="tokens";
    public static void main(String[] args) {
        KafkaBridge kafkaBridge = new KafkaBridge();
        // TODO auto discover

        kafkaBridge.runStream();
    }

    public void runStream() {

        runStream(kafkaServerFrom, topicFrom);

    }

    public void runStream(String bootstrapServers, String topic) {
        KafkaStreams streams;
        // log.info("KafkaConfiguration {} ", kafkaConfiguration.toString());
        log.info("start stream {} handler", bootstrapServers);
        Properties props = prepareConfiguration(bootstrapServers);

        StreamsBuilder streamBuilder = new StreamsBuilder();

        try {
            run(streamBuilder, bootstrapServers, topic);
        } catch (Exception e) {
            log.error(" run(streamBuilder);  ", e);

        }

        streams = new KafkaStreams(streamBuilder.build(), props);
        streams.setUncaughtExceptionHandler((thread, exception) -> {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            log.error("uncaught exception handler {} {}", exception, exception.getMessage());
            log.error(sw.toString());

        });
        streams.start();
    }

    public void run(StreamsBuilder streamBuilder, String bootstrapServers, String topic) {
        final KStream<byte[], byte[]> input = streamBuilder.stream(topic);
        input.map((key, bytes) -> KeyValue.pair(key, broadcast(bytes, kafkaServerTo)));

    }

    public boolean broadcast(byte[] data, String bootstrapServers) {

        try {
            sendMessage(data, topicTo, bootstrapServers);
            return true;
        } catch (InterruptedException e) { 
            log.error("",e);
        } catch (ExecutionException e) {
            log.error("",e);
        }
        return false;
    }

    private Properties prepareConfiguration(String bootstrapServers) {
        Properties streamsConfiguration = new Properties();
        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, getApplicationId());
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        streamsConfiguration.put(StreamsConfig.CONSUMER_PREFIX, bootstrapServers + getApplicationId());

        streamsConfiguration.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        streamsConfiguration.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        streamsConfiguration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG,
        // kafkaConfiguration.getCommitInterval());
        return streamsConfiguration;
    }

    private String getApplicationId() {
        return this.getClass().getSimpleName();
    }

    public boolean sendMessage(byte[] data, String topic, String bootstrapServers)
            throws InterruptedException, ExecutionException {
        final String key = UUID.randomUUID().toString();
        KafkaProducer<String, byte[]> messageProducer = new KafkaProducer<String, byte[]>(
                producerConfig(bootstrapServers, true));
        ProducerRecord<String, byte[]> producerRecord = null;
        producerRecord = new ProducerRecord<String, byte[]>(topic, key, data);
        final Future<RecordMetadata> result = messageProducer.send(producerRecord);
        RecordMetadata mdata = result.get();
        // System.out.println(" sendMessage " + key + "kafka server " +
        // bootstrapServers);
        messageProducer.close();
        return mdata != null;

    }

    public Properties producerConfig(String bootstrapServers, boolean binaryMessageKey) {
        Properties producerConfig = new Properties();
        producerConfig.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
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

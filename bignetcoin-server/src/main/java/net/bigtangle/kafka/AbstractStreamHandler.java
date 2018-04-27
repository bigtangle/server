package net.bigtangle.kafka;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStreamBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;


public abstract class AbstractStreamHandler {

	@Autowired
	protected KafkaConfiguration kafkaConfiguration;

 
	protected KafkaStreams streams;
	   private static final Logger log = LoggerFactory.getLogger(KafkaMessageProducer.class);

	@PostConstruct
	public void runStream() {
	    log.info("KafkaConfiguration {} ", kafkaConfiguration.toString());
		log.info("start stream {} handler", this.getClass().getSimpleName());
		Properties props = prepareConfiguration();
		KStreamBuilder streamBuilder = new KStreamBuilder();

		try {
            run(streamBuilder);
        } catch (Exception e) {
            log.error(" run(streamBuilder);  ",e);
    
        }

		streams = new KafkaStreams(streamBuilder, props);
		streams.setUncaughtExceptionHandler((thread, exception) -> {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			exception.printStackTrace(pw);
			log.error("uncaught exception handler {} {}", exception, exception.getMessage());
			log.error(sw.toString());
 
		});
		streams.start();
	}


	public abstract void run(final KStreamBuilder streamBuilder) throws Exception;


	private Properties prepareConfiguration() {
		Properties streamsConfiguration = new Properties();
		 streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, getApplicationId());
		streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfiguration.getBootstrapServers());
		 
		streamsConfiguration.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		streamsConfiguration.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
		streamsConfiguration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		//streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, kafkaConfiguration.getCommitInterval());
		return streamsConfiguration;
	}
	@PreDestroy
	public void closeStream() {
		streams.close();
	}
	 private String getApplicationId() {
	        return BlockStreamHandler.class.getCanonicalName() + "_" + this.getClass().getSimpleName() + "_" + kafkaConfiguration.getConsumerIdSuffix();
	    }
}

package net.bigtangle.kafka;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KStreamBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.server.service.TransactionService;

 @Service
public class BlockStreamHandler extends AbstractStreamHandler {

  
    @Autowired TransactionService transactionService;

    @Override
    public void run(KStreamBuilder streamBuilder) {
        final KStream<byte[], byte[]> input = streamBuilder.stream(kafkaConfiguration.getTopicOutName());

        input.map((key, bytes) -> KeyValue.pair(key, transactionService.addConnected(bytes)));

    }
   

}

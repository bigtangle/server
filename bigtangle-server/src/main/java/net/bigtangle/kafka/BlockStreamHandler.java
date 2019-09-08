/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.kafka;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.server.DispatcherController;
import net.bigtangle.server.service.TransactionService;

@Service
public class BlockStreamHandler extends AbstractStreamHandler {

    @Autowired
    TransactionService transactionService;

    @Override
    public void run(StreamsBuilder streamBuilder) {

        dorun(streamBuilder);

    }

    public void dorun(StreamsBuilder streamBuilder) {

        final KStream<byte[], byte[]> input = streamBuilder.stream(kafkaConfiguration.getTopicOutName());

        input.map((key, bytes) -> KeyValue.pair(key,
                transactionService.addConnected(DispatcherController.decompress(bytes), true, false)));

    }

}

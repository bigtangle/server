package net.bigtangle.spark.stream

import org.apache.spark.SparkConf
import org.apache.spark.streaming.Seconds
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.kafka010.ConsumerStrategies
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.Properties
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.ByteArrayDeserializer

object BlockKafka {

  def main(args: Array[String]) {
    if (args.length < 2) {
      System.err.println(s"""
        |Usage: DirectKafkaWordCount <brokers> <topics>
        |  <brokers> is a list of one or more Kafka brokers
        |  <topics> is a list of one or more kafka topics to consume from
        |
        """.stripMargin)
      System.exit(1)
    }

    Logs.setStreamingLogLevels()

    val Array(brokers, topics) = args

    // Create context with 2 second batch interval
    val sparkConf = new SparkConf().setAppName("DirectKafkaWordCount").setMaster("local[2]")
    val ssc = new StreamingContext(sparkConf, Seconds(2))

    // Create direct kafka stream with brokers and topics
    val topicsSet = topics.split(",").toSet
    //    val kafkaParams = Map[String, String]("bootstrap.servers" -> brokers, "key.serializer" -> "org.apache.kafka.common.serialization.StringSerializer",
    //      "value.serializer" -> "org.apache.kafka.common.serialization.ByteArraySerializer")
    //

    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> "de.kafka.bigtangle.net:9092",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[ByteArrayDeserializer],
      "group.id" -> "use_a_separate_group_id_for_each_stream",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean))

    val messages = KafkaUtils.createDirectStream[String,  Array[Byte]](
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String,  Array[Byte]](topicsSet, kafkaParams))

    // Get the lines, split them into words, count the words and print
//    val lines = messages.map(_.value)
//    val words = lines.flatMap(_.split(" "))
//    val wordCounts = words.map(x => (x, 1L)).reduceByKey(_ + _)
  //  wordCounts.print()

    // Start the computation
    ssc.start()
    ssc.awaitTermination()

  }
}
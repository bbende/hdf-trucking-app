/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hortonworks.trucking.storm;

import com.hortonworks.trucking.storm.bolt.AverageSpeedBolt;
import com.hortonworks.trucking.storm.bolt.ParseSpeedEventBolt;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.kafka.bolt.selector.DefaultTopicSelector;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.kafka.spout.KafkaSpoutRetryExponentialBackoff;
import org.apache.storm.kafka.spout.KafkaSpoutRetryService;
import org.apache.storm.kafka.spout.KafkaSpoutStreams;
import org.apache.storm.kafka.spout.KafkaSpoutTupleBuilder;
import org.apache.storm.kafka.spout.KafkaSpoutTuplesBuilder;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.base.BaseWindowedBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.apache.storm.kafka.spout.KafkaSpoutConfig.FirstPollOffsetStrategy.*;

public class SpeedTopology implements Serializable {

    static final long serialVersionUID = 42L;

    private static final String STREAM = "truck_speed_stream";
    private static final String TOPIC = "truck_speed_events";
    private static final String RESULT_TOPIC = "truck_average_speed";

    private static final int WINDOW_SIZE_MS = 10000;


    public static void main(String[] args) throws Exception {
        new SpeedTopology().runMain(args);
    }

    protected void runMain(String[] args) throws Exception {
        if (args.length == 0) {
            submitTopologyLocalCluster(getSpeedTopolgy(), getConfig());
        } else {
            submitTopologyRemoteCluster(args[0], getSpeedTopolgy(), getConfig());
        }

    }

    protected void submitTopologyLocalCluster(StormTopology topology, Config config) throws InterruptedException {
        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology("test", config, topology);
        stopWaitingForInput();
    }

    protected void submitTopologyRemoteCluster(String arg, StormTopology topology, Config config) throws Exception {
        StormSubmitter.submitTopology(arg, config, topology);
    }

    protected void stopWaitingForInput() {
        try {
            System.out.println("PRESS ENTER TO STOP");
            new BufferedReader(new InputStreamReader(System.in)).readLine();
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected Config getConfig() {
        Config config = new Config();
        config.setDebug(true);
        config.setMessageTimeoutSecs((WINDOW_SIZE_MS/1000) * 2);
        return config;
    }

    /**
     * @return the topology to run
     */
    protected StormTopology getSpeedTopolgy() {
        final TopologyBuilder tp = new TopologyBuilder();

        // consume from the truck_speed_events topic
        tp.setSpout("kafka_spout", new KafkaSpout<>(getKafkaSpoutConfig(getKafkaSpoutStreams())), 1);

        // parse pipe-delimited speed events into a POJO
        tp.setBolt("parse_speed_event", new ParseSpeedEventBolt(STREAM))
                .shuffleGrouping("kafka_spout", STREAM);

        // calculate the average speed for driver-route over a 10 second window
        tp.setBolt("average_speed", new AverageSpeedBolt(STREAM).withTumblingWindow(new BaseWindowedBolt.Duration(WINDOW_SIZE_MS, TimeUnit.MILLISECONDS)))
                .shuffleGrouping("parse_speed_event", STREAM);
                        //new Fields(ParseSpeedEventBolt.FIELD_DRIVER_ID, ParseSpeedEventBolt.FIELD_ROUTE_ID));

        // send results back to Kafka results topic
        tp.setBolt("kakfa_bolt", getKafkaBolt())
                .shuffleGrouping("average_speed", STREAM);

        return tp.createTopology();
    }

    /**
     * @param kafkaSpoutStreams the streams coming from Kafka
     * @return the overall config for the KafkaSpout
     */
    protected KafkaSpoutConfig<String,String> getKafkaSpoutConfig(KafkaSpoutStreams kafkaSpoutStreams) {
        return new KafkaSpoutConfig.Builder<>(getKafkaConsumerProps(), kafkaSpoutStreams, getTuplesBuilder(), getRetryService())
                .setOffsetCommitPeriodMs(10_000)
                .setFirstPollOffsetStrategy(LATEST)
                .setMaxUncommittedOffsets(250)
                .build();
    }

    /**
     * @return the Kafka retry service
     */
    protected KafkaSpoutRetryService getRetryService() {
        return new KafkaSpoutRetryExponentialBackoff(
                KafkaSpoutRetryExponentialBackoff.TimeInterval.microSeconds(500),
                KafkaSpoutRetryExponentialBackoff.TimeInterval.milliSeconds(2), Integer.MAX_VALUE,
                KafkaSpoutRetryExponentialBackoff.TimeInterval.seconds(10));
    }

    /**
     * @return the properties for the Kafka Consumer.
     */
    protected Map<String,Object> getKafkaConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        // props.put(KafkaSpoutConfig.Consumer.ENABLE_AUTO_COMMIT, "true");
        props.put(KafkaSpoutConfig.Consumer.BOOTSTRAP_SERVERS, "localhost:6667");
        props.put(KafkaSpoutConfig.Consumer.GROUP_ID, "speedTopologyGroup");
        props.put(KafkaSpoutConfig.Consumer.KEY_DESERIALIZER, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(KafkaSpoutConfig.Consumer.VALUE_DESERIALIZER, "org.apache.kafka.common.serialization.StringDeserializer");
        return props;
    }

    /**
     * Creates the Tuple builders. This topology has a single Tuple builder that takes ConsumerRecord<String,String> and
     * produces Tuples with topic, partition, offset, key, and value.
     *
     * @return the Tuple builders for this topology
     */
    protected KafkaSpoutTuplesBuilder<String, String> getTuplesBuilder() {
        final KafkaSpoutTupleBuilder<String,String> tupleBuilder = new KafkaSpoutTupleBuilder<String, String>(TOPIC) {
            @Override
            public List<Object> buildTuple(ConsumerRecord<String, String> consumerRecord) {
                return new Values(consumerRecord.topic(),
                        consumerRecord.partition(),
                        consumerRecord.offset(),
                        consumerRecord.key(),
                        consumerRecord.value());
            }
        };

        return new KafkaSpoutTuplesBuilder.Builder<>(tupleBuilder).build();
    }

    /**
     * Declare the streams coming from Kafka. This example has a single stream where each tuple contains
     * the topic, partition, offset, key, and value.
     *
     * @return the KafkaSpoutStreams instance for this topology
     */
    protected KafkaSpoutStreams getKafkaSpoutStreams() {
        final Fields outputFields = new Fields("topic", "partition", "offset", "key", "value");
        return new KafkaSpoutStreams.Builder(outputFields, STREAM, new String[] {TOPIC}).build();
    }

    protected KafkaBolt<String,String> getKafkaBolt() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:6667");
        props.put("acks", "1");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        final KafkaBolt bolt = new KafkaBolt()
                .withProducerProperties(props)
                .withTopicSelector(new DefaultTopicSelector(RESULT_TOPIC))
                .withTupleToKafkaMapper(new FieldNameBasedTupleToKafkaMapper<>());
        return bolt;
    }

}

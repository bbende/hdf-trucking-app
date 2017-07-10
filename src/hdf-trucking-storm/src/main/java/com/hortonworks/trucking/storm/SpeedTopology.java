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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.kafka.bolt.selector.DefaultTopicSelector;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.base.BaseWindowedBolt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.apache.storm.kafka.spout.KafkaSpoutConfig.FirstPollOffsetStrategy.LATEST;

public class SpeedTopology implements Serializable {

    static final long serialVersionUID = 42L;

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
        tp.setSpout("kafka_spout", new KafkaSpout<>(getKafkaSpoutConfig()), 1);

        // parse pipe-delimited speed events into a POJO
        tp.setBolt("parse_speed_event", new ParseSpeedEventBolt())
                .shuffleGrouping("kafka_spout");

        // calculate the average speed for driver-route over a 10 second window
        tp.setBolt("average_speed", new AverageSpeedBolt().withTumblingWindow(new BaseWindowedBolt.Duration(WINDOW_SIZE_MS, TimeUnit.MILLISECONDS)))
                .shuffleGrouping("parse_speed_event");
                        //new Fields(ParseSpeedEventBolt.FIELD_DRIVER_ID, ParseSpeedEventBolt.FIELD_ROUTE_ID));

        // send results back to Kafka results topic
        tp.setBolt("kakfa_bolt", getKafkaBolt())
                .shuffleGrouping("average_speed");

        return tp.createTopology();
    }

    protected KafkaSpoutConfig<String,String> getKafkaSpoutConfig() {
        return KafkaSpoutConfig.builder("localhost:6667", TOPIC)
                .setGroupId("speedTopologyGroup")
                .setKey(StringDeserializer.class)
                .setValue(StringDeserializer.class)
                .setOffsetCommitPeriodMs(10_000)
                .setFirstPollOffsetStrategy(LATEST)
                .setMaxUncommittedOffsets(259)
                .build();
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

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
package com.hortonworks.trucking.storm.bolt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hortonworks.trucking.storm.data.AverageSpeed;
import com.hortonworks.trucking.storm.data.SpeedEvent;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseWindowedBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.windowing.TupleWindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces the average speed over a window for a given driver + route.
 */
public class AverageSpeedBolt extends BaseWindowedBolt {

    private final String streamId;
    private OutputCollector collector;
    private ObjectMapper mapper;

    public AverageSpeedBolt(final String streamId) {
        this.streamId = streamId;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        this.mapper = new ObjectMapper();
    }

    @Override
    public void execute(TupleWindow inputWindow) {
        final Map<String,AverageSpeedWithTuples> averageSpeeds = new HashMap<>();

        // aggregate events by "route id" + "driver id"
        for(Tuple tuple: inputWindow.get()) {
            final SpeedEvent speedEvent = (SpeedEvent) tuple.getValueByField("speed_event");
            final String avgKey = speedEvent.getRouteId() + speedEvent.getDriverId();

            AverageSpeedWithTuples avgSpeedWithTuples = averageSpeeds.get(avgKey);
            if (avgSpeedWithTuples == null) {
                AverageSpeed avgSpeed = new AverageSpeed();
                avgSpeed.setRouteName(speedEvent.getRouteName());
                avgSpeed.setRouteId(speedEvent.getRouteId());
                avgSpeed.setDriverName(speedEvent.getDriverName());
                avgSpeed.setDriverId(speedEvent.getDriverId());
                avgSpeed.setTruckId(speedEvent.getTruckId());

                avgSpeedWithTuples = new AverageSpeedWithTuples(avgSpeed);
                averageSpeeds.put(avgKey, avgSpeedWithTuples);
            }
            avgSpeedWithTuples.incrementTotalSpeed(speedEvent.getSpeed(), tuple);
        }

        // emit average events
        for (Map.Entry<String,AverageSpeedWithTuples> entry : averageSpeeds.entrySet()) {
            final AverageSpeedWithTuples avgSpeedWithTuples = entry.getValue();
            try {
                // marshall to json and emit a new tuple with "key" and "message" fields
                final String avgSpeedJson = mapper.writeValueAsString(avgSpeedWithTuples.averageSpeed);
                collector.emit(streamId, new Values("key", avgSpeedJson));

                // ack the tuples that create the avg speed
                for (Tuple tuple : avgSpeedWithTuples.getTuples()) {
                    collector.ack(tuple);
                }

            } catch (JsonProcessingException e) {
                // report the error and fail the tuples that created the avg speed
                collector.reportError(e);
                for (Tuple tuple : avgSpeedWithTuples.getTuples()) {
                    collector.fail(tuple);
                }
            }
        }

    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(streamId, new Fields("key", "message"));
    }

    private static class AverageSpeedWithTuples {

        private AverageSpeed averageSpeed;
        private List<Tuple> tuples = new ArrayList<>();

        public AverageSpeedWithTuples(AverageSpeed averageSpeed) {
            this.averageSpeed = averageSpeed;
        }

        public synchronized void incrementTotalSpeed(int amount, Tuple tuple) {
            this.averageSpeed.incrementTotalSpeed(amount);
            this.tuples.add(tuple);
        }

        public List<Tuple> getTuples() {
            return Collections.unmodifiableList(tuples);
        }
    }

}

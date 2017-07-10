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

import com.hortonworks.trucking.storm.data.SpeedEvent;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

/**
 * Parses a SpeedEvent of the format:
 *
 * 2016-10-19 14:54:38.197|truck_speed_event|40|23|Jeff Markham|1090292248|Peoria to Ceder Rapids Route 2|73|
 */
public class ParseSpeedEventBolt extends BaseRichBolt {

    public static final String FIELD_DRIVER_ID = "driver_id";
    public static final String FIELD_ROUTE_ID = "route_id";
    public static final String FIELD_SPEED_EVENT = "speed_event";

    private OutputCollector collector;

    public ParseSpeedEventBolt() {
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {
        final String value = input.getStringByField("value");

        SpeedEvent speedEvent = null;
        if (value != null) {
            String[] parts = value.split("[|]");
            if (parts.length == 8) {
                speedEvent = new SpeedEvent();
                speedEvent.setEventTime(parts[0]);
                speedEvent.setEventSource(parts[1]);
                speedEvent.setTruckId(parts[2]);
                speedEvent.setDriverId(parts[3]);
                speedEvent.setDriverName(parts[4]);
                speedEvent.setRouteId(parts[5]);
                speedEvent.setRouteName(parts[6]);
                speedEvent.setSpeed(Integer.valueOf(parts[7]));
            }
        }

        if (speedEvent == null) {
            collector.reportError(new Exception("Unable to parse SpeedEvent for value: " + value));
            collector.fail(input);
        } else {
            collector.emit(new Values(speedEvent.getDriverId(), speedEvent.getRouteId(), speedEvent));
            collector.ack(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(FIELD_DRIVER_ID, FIELD_ROUTE_ID, FIELD_SPEED_EVENT));
    }

}
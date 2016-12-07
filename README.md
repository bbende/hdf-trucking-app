# hdf-trucking-app

Example application demonstrating how to integrate all of the components of Hortonworks DataFlow.

![Image](https://github.com/bbende/hdf-trucking-app/blob/vagrant/images/hdf-trucking-app.png?raw=true)

This is repository is a work in progress.

## Pre-Requisites

A working installation of HDF, including all services.

## Setup

### Solr/Banana

  - Add Banana (https://github.com/lucidworks/banana) into the Ambari Infra Solr (/usr/lib/ambari-infra-solr/)
  - Create a new collection in Solr called 'truck_average_speed' (see scripts/create_collection.sh)
  - Create required fields in the new Solr collection (see scripts/create_fields.sh)

### Storm

  - Build hdf-trucking-storm using 'mvn clean package'
  - Deploy the Storm topology:
    
        storm jar /shared/storm/iot-trucking-storm-1.0-SNAPSHOT.jar com.hortonworks.trucking.storm.SpeedTopology speed-topology

### NiFi

  - Deploy the flow that is in conf/nifi/flow.xml.gz to NiFi under /var/lib/nifi/conf

### Trucking Simulator
  
  - Clone and build https://github.com/georgevetticaden/hdp/tree/master/reference-apps/iot-trucking-app/trucking-data-simulator
  - Generate data to a file: 
        
        nohup java -cp stream-simulator-jar-with-dependencies.jar hortonworks.hdp.refapp.trucking.simulator.SimulationRunnerSingleDriverApp -1 hortonworks.hdp.refapp.trucking.simulator.impl.domain.transport.Truck hortonworks.hdp.refapp.trucking.simulator.impl.collectors.FileEventCollector 1 /shared/stream-simulator/routes/midwest/ 500 /tmp/truck-sensor-data/truck-1.txt 10 'Saint Louis To Tulsa' > nohup-truck-1.out &

### MiNiFi

  - Download the latest version of MiNiFi - https://nifi.apache.org/minifi/download.html
  - Extract it and copy in conf/minifi/config.yml from this repository to the MiNiFi conf 
  - Start MiNiFi








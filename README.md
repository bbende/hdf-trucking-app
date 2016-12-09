# Hortonworks DataFlow (HDF) Trucking Application

Example application demonstrating how to integrate all of the components of Hortonworks DataFlow:

* Apache NiFi/MiNiFi
* Apache Kafka
* Apache Storm

![Image](https://github.com/bbende/hdf-trucking-app/blob/vagrant/images/hdf-trucking-app.png?raw=true)

## Setup

1. Install VirtualBox

    [https://www.virtualbox.org/wiki/Downloads](https://www.virtualbox.org/wiki/Downloads)

2. Install Vagrant

    [https://www.vagrantup.com/](https://www.vagrantup.com/)

3. Clone this repository

        git clone https://github.com/bbende/hdf-trucking-app.git

4. Start the Vagrant VM

        cd hdf-trucking-app
        vagrant up

    NOTE: This step could take a little while, at the end there should be a line that shows something like:

        Cluster build status at: http://localhost:8080/api/v1/clusters/HDF/requests/1

5. Wait until all HDF services have been installed and started

   Go to [http://localhost:8080](http://localhost:8080) in your browser and login with admin/admin.

   Wait until you see all services running:

   ![Image](https://github.com/bbende/hdf-trucking-app/blob/vagrant/images/hdf-ambari-services.png?raw=true)

6. Setup the demo application

        vagrant ssh
        sudo su -
        /home/vagrant/sync/scripts/setup_hdf_trucking_app.sh

7. Install Banana Dashboard

        Go to [http://localhost:8886/solr/banana/src/index.html](http://localhost:8886/solr/banana/src/index.html)
        Click Load icon in top-right
        Choose Local File
        Select hdp-trucking-app/conf/banana/HDF_Truck_Events-1478197521141
        Save & Set As Browser Default

NOTE: To gracefully shutdown the VM, make sure to exit out of your SSH session and execute:

        vagrant halt

      To completely destroy the VM and start over, execute:

        vagrant destroy 

## Overview

This section contains an overview of the demo trucking application.

### Trucking Data Simulator

The trucking data simulator is responsible for writing truck events to a file. Events look like the following:

        2016-12-09 16:07:24.211|truck_geo_event|47|10|George Vetticaden|1390372503|Saint Louis to Tulsa|Normal|36.18|-95.76|1|
        2016-12-09 16:07:24.212|truck_speed_event|47|10|George Vetticaden|1390372503|Saint Louis to Tulsa|66|

The source code for the simulator is on the VM at:

        /root/hdp-bbende/reference-apps/iot-trucking-app/trucking-data-simulator/

The running simulator is installed on the VM at:

        /opt/hdf-trucking-app/simulator/

The simulator is writing events to a file on the VM at:

        /tmp/truck-sensor-data/truck-1.txt

### MiNiFi

The MiNiFi Java distribution is installed on the VM at:

        /opt/hdf-trucking-app/minifi-0.1.0/

MiNiFi is tailing the file of truck events described above and sending the events to NiFi via site-to-site.

### NiFi

The NiFi console is available at [http://localhost:9090/nifi](http://localhost:9090/nifi).

NiFi is receiving the truck events from MiNiFi and publishing them to a Kafka topic.

NiFi is also consuming from a separate Kafka topic where results are being written by a Storm topology.

### Kafka

There are two Kafka topics:

* truck_speed_events_test - Where speed events are published by NiFi
* truck_average_speed - Where average speed events are published by Storm

### Storm

The Storm console is available at [http://localhost:8744/index.html](http://localhost:8744/index.html).

The source code for the average speed topology is at:

        /home/vagrant/sync/src/hdf-trucking-storm/

### Solr/Banana

The Solr Admin UI is available at [http://localhost:8886/solr/](http://localhost:8886/solr/).

There is a single collection created called 'truck_average_speed' to hold the average speed events computer by Storm.

NiFi is responsible for consuming those events from Kafka and ingesting them to Solr.

The Banana dashboard is available at [http://localhost:8886/solr/banana/src/index.html](http://localhost:8886/solr/banana/src/index.html).

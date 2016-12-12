#!/usr/bin/env bash

echo "Setting up HDF Trucking App Demo..."
CURR_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SRC_HOME=$CURR_DIR/..

HDF_TRUCK_HOME=/opt/hdf-trucking-app
mkdir $HDF_TRUCK_HOME

echo "Installing Banana into Ambari Infra Solr..."
git clone https://github.com/lucidworks/banana
cp -R banana /usr/lib/ambari-infra-solr/server/solr-webapp/webapp/

echo "Creating new collection in Ambari Infra Solr..."
$CURR_DIR/solr/create_collection.sh

echo "Adding fields to new collection in Ambari Infra Solr..."
$CURR_DIR/solr/create_fields.sh

echo "Creating Kafka Topics..."
/usr/hdf/current/kafka-broker/bin/kafka-topics.sh --create --zookeeper localhost.localdomain:2181 --replication-factor 1 --partitions 1 --topic truck_speed_events
/usr/hdf/current/kafka-broker/bin/kafka-topics.sh --create --zookeeper localhost.localdomain:2181 --replication-factor 1 --partitions 1 --topic truck_average_speed

echo "Deploying NiFi Flow & Restarting NiFi..."
cd $SRC_HOME
/usr/hdf/current/nifi/bin/nifi.sh stop
/bin/cp -f conf/nifi/flow.xml.gz /var/lib/nifi/conf/
/usr/hdf/current/nifi/bin/nifi.sh start

echo "Installing Maven 3.0.5..."
wget http://mirrors.gigenet.com/apache/maven/maven-3/3.0.5/binaries/apache-maven-3.0.5-bin.tar.gz
tar -zxvf apache-maven-3.0.5-bin.tar.gz -C /opt/
export M2_HOME=/opt/apache-maven-3.0.5
export M2=$M2_HOME/bin
PATH=$M2:$PATH
echo "export M2_HOME=/opt/apache-maven-3.0.5" >> ~/.bashrc
echo "export M2=$M2_HOME/bin" >> ~/.bashrc
echo "PATH=$M2:$PATH" >> ~/.bashrc
source ~/.bashrc
source ~/.bashrc

echo "Building & Deploying Storm Average Speed Topology..."
cd $SRC_HOME/src
mvn clean install
storm jar $SRC_HOME/src/hdf-trucking-storm/target/hdf-trucking-storm-1.0-SNAPSHOT.jar com.hortonworks.trucking.storm.SpeedTopology speed-topology

echo "Building Trucking Data Simulator..."
cd ~
git clone https://github.com/bbende/hdp.git hdp-bbende
cd hdp-bbende
git checkout -b hdf-trucking-app origin/hdf-trucking-app
cd ~/hdp-bbende/app-utils/hdp-app-utils/
mvn clean install -DskipTests
cd ~/hdp-bbende/reference-apps/iot-trucking-app/
mvn clean install -DskipTests
cd ~/hdp-bbende/reference-apps/iot-trucking-app/trucking-data-simulator
mvn clean package assembly:single

echo "Deploying Trucking Data Simulator..."
mkdir $HDF_TRUCK_HOME/simulator
/bin/cp -f target/stream-simulator-jar-with-dependencies.jar $HDF_TRUCK_HOME/simulator/
/bin/cp -R src/main/resources/routes $HDF_TRUCK_HOME/simulator/
/bin/cp -f $SRC_HOME/conf/simulator/generate-data.sh $HDF_TRUCK_HOME/simulator/
chmod ugo+x $HDF_TRUCK_HOME/simulator/generate-data.sh

echo "Starting Trucking Data Simulator..."
cd $HDF_TRUCK_HOME/simulator/
./generate-data.sh

echo "Downloading MiNiFi..."
cd ~
wget http://www.trieuvan.com/apache/nifi/minifi/0.1.0/minifi-0.1.0-bin.tar.gz
tar xzf minifi-0.1.0-bin.tar.gz -C $HDF_TRUCK_HOME/

echo "Deploying MiNiFi config & starting..."
/bin/cp -f $SRC_HOME/conf/minifi/config.yml $HDF_TRUCK_HOME/minifi-0.1.0/conf/
$HDF_TRUCK_HOME/minifi-0.1.0/bin/minifi.sh start

echo "Done setting up HDF Trucking App!"
echo "Go to Banana at http://localhost:8886/solr/banana/src/index.html and import the dashboard from hdf-trucking-app/conf/banana/HDF_Truck_Events-1478197521141"

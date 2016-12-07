#!/usr/bin/env bash

echo "Creating truckId field..."

curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field":{
     "name":"truckId",
     "type":"string",
     "stored":true,
     "indexed":true}
}' http://localhost:8886/solr/truck_average_speed/schema

echo "Creating driverId field..."

curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field":{
     "name":"driverId",
     "type":"string",
     "stored":true,
     "indexed":true}
}' http://localhost:8886/solr/truck_average_speed/schema

echo "Creating driverName field..."

curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field":{
     "name":"driverName",
     "type":"string",
     "stored":true,
     "indexed":true}
}' http://localhost:8886/solr/truck_average_speed/schema

echo "Creating routeId field..."

curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field":{
     "name":"routeId",
     "type":"string",
     "stored":true,
     "indexed":true}
}' http://localhost:8886/solr/truck_average_speed/schema

echo "Creating routeName field..."

curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field":{
     "name":"routeName",
     "type":"string",
     "stored":true,
     "indexed":true}
}' http://localhost:8886/solr/truck_average_speed/schema

echo "Creating eventTimestamp field..."

curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field":{
     "name":"eventTimestamp",
     "type":"tdate",
     "stored":true,
     "indexed":true}
}' http://localhost:8886/solr/truck_average_speed/schema

echo "Creating averageSpeed field..."

curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field":{
     "name":"averageSpeed",
     "type":"tint",
     "stored":true,
     "indexed":true}
}' http://localhost:8886/solr/truck_average_speed/schema
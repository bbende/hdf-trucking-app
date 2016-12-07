#!/usr/bin/env bash

/usr/lib/ambari-infra-solr/bin/solr create_collection -c truck_average_speed -d data_driven_schema_configs -shards 1 -replicationFactor 1

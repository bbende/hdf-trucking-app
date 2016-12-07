#!/usr/bin/env bash

CURR_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "Installing Banana into Ambari Infra Solr..."
cd ~
git clone https://github.com/lucidworks/banana
cp -R banana /usr/lib/ambari-infra-solr/server/solr-webapp/webapp/

echo "Creating new collection in Ambari Infra Solr..."
$CURR_DIR/solr/create_collection.sh

echo "Adding fields to new collection in Ambari Infra Solr..."
$CURR_DIR/solr/create_fields.sh

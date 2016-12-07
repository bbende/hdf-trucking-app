# Sample script to deploy HDF via blueprint
# - Installs Ambari server/agents
# - Installs HDF mpack
# - Uses ambari-bootstrap to generate blueprint based on stack advisor recommendation and starts cluster install
# - Optionally: installs KDC, sets up postgres for Ranger, allows customizations of config properties and number of Nifi nodes
#
# Usage: su as root and run below to invoke this script on a host where CentOS/RHEL has been freshly installed (do NOT run this on HDP sandbox!). You can customize the functionality by setting env vars e.g.
#  export host_count=3; export install_nifi_on_all_nodes=true; curl -sSL https://gist.github.com/abajwa-hw/ae4125c5154deac6713cdd25d2b83620/raw | sudo -E sh ;

# Note for multi-node install, you will need to install/register agents on other nodes first using below (replace <AMBARI_SERVER_HOSTNAME>)
# export ambari_server=<AMBARI_SERVER_HOSTNAME>; curl -sSL https://raw.githubusercontent.com/seanorama/ambari-bootstrap/master/ambari-bootstrap.sh | sudo -E sh ;
# e.g.
# export ambari_server=abajwa-hdf-qe-bp-1.openstacklocal; export ambari_version=2.4.1.0; curl -sSL https://raw.githubusercontent.com/seanorama/ambari-bootstrap/master/ambari-bootstrap.sh | sudo -E sh ;
# see for more details: https://community.hortonworks.com/articles/56849/automate-deployment-of-hdf-20-clusters-using-ambar.html

#clean previous setup
rm -rf ~/ambari-bootstrap
rm -f *_payload
rm -rf ~/hdf_ambari_mp
rm ~/apache-maven-3.0.5-bin.tar.gz

set -e -x

export install_nifi_on_all_nodes="${install_nifi_on_all_nodes:-true}"
export use_default_configs="${use_default_configs:-false}"
#export ambari_services=${ambari_services:-ZOOKEEPER NIFI KAFKA STORM LOGSEARCH AMBARI_METRICS AMBARI_INFRA}
export ambari_services=${ambari_services:-ZOOKEEPER NIFI KAFKA STORM AMBARI_INFRA}
export ambari_password=${ambari_password:-admin}
export cluster_name=${cluster_name:-HDF}
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-1.8.0-openjdk.x86_64/}
export ranger_user="${ranger_user:-rangeradmin}"
export ranger_pass="${ranger_pass:-BadPass#1}"
export host_count=${host_count:-ask}
export setup_kdc="${setup_kdc:-false}"
export setup_postgres_for_ranger="${setup_postgres_for_ranger:-true}"
export host_os=${host_os:-centos6}


export ambari_version=2.4.1.0
export hdf_ambari_mpack_url="http://public-repo-1.hortonworks.com/HDF/${host_os}/2.x/updates/2.0.0.0/tars/hdf_ambari_mp/hdf-ambari-mpack-2.0.0.0-579.tar.gz"
export ambari_repo="http://public-repo-1.hortonworks.com/ambari/${host_os}/2.x/updates/${ambari_version}/ambari.repo"
export hdf_repo_url="http://public-repo-1.hortonworks.com/HDF/${host_os}/2.x/updates/2.0.0.0"
export host_count=1

yum install -y git python-argparse
cd ~
sudo git clone https://github.com/seanorama/ambari-bootstrap.git

echo "Bootstrapping ambari-server..."
export install_ambari_server=true
chmod +x ~/ambari-bootstrap/ambari-bootstrap.sh
~/ambari-bootstrap/ambari-bootstrap.sh

ambari-server stop

echo "Installing HDF mpack..."
ambari-server install-mpack --mpack=${hdf_ambari_mpack_url} --purge --verbose

#Optional - modify stack advisor to recommend installing Nifi on all nodes
if [ "${install_nifi_on_all_nodes}" = true ]; then
  cp /var/lib/ambari-server/resources/stacks/HDF/2.0/services/stack_advisor.py /var/lib/ambari-server/resources/stacks/HDF/2.0/services/stack_advisor.py.bak
  sed -i.bak  "s#return \['ZOOKEEPER_SERVER', 'METRICS_COLLECTOR'\]#return \['ZOOKEEPER_SERVER', 'METRICS_COLLECTOR', 'NIFI_MASTER'\]#" /var/lib/ambari-server/resources/stacks/HDF/2.0/services/stack_advisor.py
  sed -i.bak  "s#\('ZOOKEEPER_SERVER': {\"min\": 3},\)#\1\n      'NIFI_MASTER': {\"min\": $host_count},#g"  /var/lib/ambari-server/resources/stacks/HDF/2.0/services/stack_advisor.py
fi

#start Ambari
ambari-server start
sleep 30

cd ~

#any customizations?
cd ~/ambari-bootstrap/deploy/

#whether to test with default configs or custom
if [ "${use_default_configs}" = true ]; then
  tee configuration-custom.json > /dev/null << EOF
{
  "configurations" : {
    "nifi-ambari-config" : {
        "blah" : "blah"
    }
  }
}
EOF

else

  tee configuration-custom.json > /dev/null << EOF
{
  "configurations" : {
    "nifi-ambari-config": {
        "nifi.web.http.host": "",
        "nifi.remote.input.socket.port" : "8731"
    }
  }
}
EOF
fi

echo "Deploying HDF..."
export ambari_stack_name=HDF
export ambari_stack_version=2.0
./deploy-recommended-cluster.bash

#To reset and start over:
#python /usr/lib/python2.6/site-packages/ambari_agent/HostCleanup.py -s
#ambari-server stop
#ambari-server reset
#  ##type yes twice
#ambari-agent stop
#yum remove -y ambari-server ambari-agent
#rm -rf /root/*
#rm -rf /var/lib/ambari-server/resources/host_scripts/nifi-certs
#kdb5_util destroy
#  ##type yes

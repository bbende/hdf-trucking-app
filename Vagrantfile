# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure(2) do |config|

  config.vm.box = "centos/6"
  config.vm.provision :shell, path: "scripts/setup.sh"
  config.vm.network "forwarded_port", guest: 8080, host: 8080, auto_correct: true
  config.vm.network "forwarded_port", guest: 9090, host: 9090, auto_correct: true
  config.vm.network "forwarded_port", guest: 3000, host: 3000, auto_correct: true
  config.vm.network "forwarded_port", guest: 8744, host: 8744, auto_correct: true
  config.vm.network "forwarded_port", guest: 9091, host: 9091, auto_correct: true
  config.vm.network "forwarded_port", guest: 8886, host: 8886, auto_correct: true
  config.vm.network "forwarded_port", guest: 61888, host: 61888, auto_correct: true
  config.vm.network "forwarded_port", guest: 6378, host: 6378, auto_correct: true

  # Create a private network, which allows host-only access to the machine
  # using a specific IP.
  #config.vm.network "private_network", ip: "192.168.33.10"

  # Create a public network, which generally matched to bridged network.
  # Bridged networks make the machine appear as another physical device on
  # your network.
  # config.vm.network "public_network"

  # Share an additional folder to the guest VM. The first argument is
  # the path on the host to the actual folder. The second argument is
  # the path on the guest to mount the folder. And the optional third
  # argument is a set of non-required options.
  #config.vm.synced_folder "./shared", "/shared"

  config.vm.provider "virtualbox" do |vb|
     #vb.gui = true
     vb.memory = "8192"
     vb.cpus="4"
  end

end

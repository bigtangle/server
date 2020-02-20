#!/bin/bash 
cd demo
x-terminal-emulator -T "Peer 1" -e java -cp ./etc_lib/*:etc.jar au.edu.unimelb.cs.culnane.protocols.distkeygen.DistKeyGenRunner Peer1 8091 ./peers.json &
x-terminal-emulator -T "Peer 2" -e java -cp ./etc_lib/*:etc.jar au.edu.unimelb.cs.culnane.protocols.distkeygen.DistKeyGenRunner Peer2 8092 ./peers.json &
x-terminal-emulator -T "Peer 3" -e java -cp ./etc_lib/*:etc.jar au.edu.unimelb.cs.culnane.protocols.distkeygen.DistKeyGenRunner Peer3 8093 ./peers.json &

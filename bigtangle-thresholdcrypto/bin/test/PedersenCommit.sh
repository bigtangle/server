#!/bin/bash 

if [ -t 0 ]
then
	cd demo
	echo -n "Do you wish to create a commit or open a previous commit (1=create, 2=open): "
	read answer

	if [ "$answer" = "1" ]; then
		echo -n "in here"
 		x-terminal-emulator -T "Peer 1" -e java -cp ./etc_lib/*:etc.jar au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenRunner Peer1 8091 ./peers.json true 
		x-terminal-emulator -T "Peer 2" -e java -cp ./etc_lib/*:etc.jar au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenRunner Peer2 8092 ./peers.json true 
		x-terminal-emulator -T "Peer 3" -e java -cp ./etc_lib/*:etc.jar au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenRunner Peer3 8093 ./peers.json true 
	else
		x-terminal-emulator -T "Peer 1" -e java -cp ./etc_lib/*:etc.jar au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenRunner Peer1 8091 ./peers.json false 
		x-terminal-emulator -T "Peer 2" -e java -cp ./etc_lib/*:etc.jar au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenRunner Peer2 8092 ./peers.json false 
		x-terminal-emulator -T "Peer 3" -e java -cp ./etc_lib/*:etc.jar au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenRunner Peer3 8093 ./peers.json false 
	fi
else
 	x-terminal-emulator -e $0

fi


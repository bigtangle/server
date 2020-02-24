cd demo
@echo off
set /p CREATE="Do you wish to create a commit or open a previous commit (1=create, 2=open): "
IF "%CREATE%" =="1" (
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenRunner Peer1 8091 ./peers.json true
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenRunner Peer2 8092 ./peers.json true
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenRunner Peer3 8093 ./peers.json true
) ELSE (
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenRunner Peer1 8091 ./peers.json false
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenRunner Peer2 8092 ./peers.json false
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenRunner Peer3 8093 ./peers.json false
)

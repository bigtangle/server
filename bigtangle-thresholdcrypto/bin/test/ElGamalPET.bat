cd demo

call java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.threshold.pet.CreateMatchingEncryptions ./peer1.js /wait
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.threshold.pet.ThresholdPETRunner Peer1 8091 ./peers.json
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.threshold.pet.ThresholdPETRunner Peer2 8092 ./peers.json
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.threshold.pet.ThresholdPETRunner Peer3 8093 ./peers.json
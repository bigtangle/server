

cd demo
call java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.threshold.decryption.CreateSampleEncryption ./peer1.js /wait

start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.threshold.decryption.ThresholdDecryptionRunner Peer1 8091 ./peers.json
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.threshold.decryption.ThresholdDecryptionRunner Peer2 8092 ./peers.json
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.threshold.decryption.ThresholdDecryptionRunner Peer3 8093 ./peers.json
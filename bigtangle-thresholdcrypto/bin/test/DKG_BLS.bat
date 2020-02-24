cd demo
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.distkeygen.bls.BLSDistKeyGenRunner Peer1 8091 ./peers.json
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.distkeygen.bls.BLSDistKeyGenRunner Peer2 8092 ./peers.json
start cmd /k java -cp ./etc_lib/*;etc.jar au.edu.unimelb.cs.culnane.protocols.distkeygen.bls.BLSDistKeyGenRunner Peer3 8093 ./peers.json
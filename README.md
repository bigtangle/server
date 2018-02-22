test of client needs the server to be started
IRI -p 14265  --testnet

#Design
The application is based on spring boot and using the following components and service
The Transaction and block are from bitcoinj project for the  security and scripting.
 


Transaction (bitcoinj)
Block (bitcoinj) BlockChain as BlockGraph (two previous Blocks) 
Tip management (  iota)
snapshot and milestone (iota)
Web Service ( Spring)
validate service (bitcoinj)
Storage (bitcoinj) 
all data in database Full SQL using mysql or postgres first, then to Hbase or Spark Job server
data sync process using kafka

Unit test and docker container test
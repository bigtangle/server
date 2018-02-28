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

 

Basic Service Framework for Spring:

1) Transaction Service 
Transaction Service  provides service for record of transactions that send and receive value from given keys. Using these,
it is able to create new transactions that spend the recorded transactions, and this is the fundamental operation
of the  protocol

2) Tip Service
 Tip Service provides MCMC algorithm to select two previous blocks.
 
3) StorageService
Database Service access.

4) DataSyncService
public/subscribe kafka stream for blocks and transactions.

5) WalletService 
Key management  and  related transactions

6) MilestoneService  
Milestone Service creates snapshot for calculation of incentive for mining.


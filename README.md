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



<p>A Wallet stores keys and a record of transactions that send and receive value from those keys. Using these,
it is able to create new transactions that spend the recorded transactions, and this is the fundamental operation
of the  protocol.</p>

<p>To learn more about this class, read <b><a href="https://bitcoinj.github.io/working-with-the-wallet">
    working with the wallet.</a></b></p>

<p>To fill up a Wallet with transactions, you need to use it in combination with a {@link BlockGraph} and various
other objects, see the <a href="https://bitcoinj.github.io/getting-started">Getting started</a> tutorial
on the website to learn more about how to set everything up.</p>
  
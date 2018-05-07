# Installing

You have two options, the preferred option is that you compile yourself. The second option is that you utilize the provided jar, which is released regularly (when new updates occur) here: [ Releases](https://).



#### Locally

```
java -jar bigtangle-server.jar -Xms512m -Xmx1g 
```

### Compiling yourself  

Make sure to have Java 8 installed on your computer.

#### To compile & package
```
$ git clone https://git.dasimi.com/digi/bigtangle.git
$ cd bigtangle
$ ./gradlew build 

```


#For development using the latest eclipse
checkout this project and import this project. It may require the configure of the  project as gradle project.

use the format preference-> java -> code style -> formatter import the file designdoc/eclipse.xml



#test of client needs the server to be started
com.bigtangle.server.ServerStart



#Design


## Overview

1)client prepares new transactions and asks node server for account balance and unspent transaction. (input public key, toadress, amount)

2)server node does select two previous blocks (MCMC) and approve those only if all transactions in it are valid and  not conflicting and do not approve conflicting blocks.

3)server node returns the prepared transaction block with all client transactions. 
return Block with the  transaction 
4)client node must compute the nonce of the transaction block as proof of work and signs the transaction

5) server get the block and goto 6a)
(input Block, return true/false) save block, broadcast
? add block in two parts, 
6a)when receiving blocks, only check static formal, save data in header table and broadcast to all server nodes. no update outputs)

6b)during milestone creation, when confirmation rate > 75% and no conflicts, add block data to milestone, update outputs


## Server 
The server application is based on spring boot and using the following components and service.
 
1) Transaction Service 
Transaction Service  provides service for record of transactions that send and receive value from given keys. Using these,
it is able to create new transactions that spend the recorded transactions, and this is the fundamental operation
of the  protocol.
Transactions is defined by TransactionInput and TransactionOutput
TransactionInput has a point to the original output
TransactionOutPoint:  Hash of the transaction to which we refer and index 
TransactionOutput:
value and scriptPubKey

2) Tip Service
 Tip Service provides MCMC algorithm to select two previous blocks.
 
3) StorageService
Database Service access.

4) DataSyncService
public/subscribe kafka stream for blocks and transactions.

5) Block Service
validation of blocks and block evaluation 

6) MilestoneService  
Milestone Service creates snapshot for calculation of incentive for mining.

7) broadcast
First step: use the existing p2p broadcasting.
Different to Peer to Peer broadcast is the clustered and quick as 
Kafka Stream:
dynamic discovery and swicht of Kafka cluster 
1)publish block to kafka
2)consumer block from kafka
3)relay kafka topic to all other cluster. (no cycle)



## Client
There is client UI appliction based on JavaFX. The wallet manages the keys and provides interface to server.


 1) coin balance overview client
 list of all balance per token
 API:input key(prikey or other string),return lists(map:key tokenid,value): per token list and utxo list
 2) transfer of coin
 transfer X coins from account A to account B
 3) create genesis block for new token 
 as example: IPO for new stock: Genesis Block of Stocks: XXX as token with number Y 
 4) transaction between token and coin
 Account A  buy stock YY with price XX  from  account B 
 Account A  tranfer  YY to   account B
 Account B tranfer  XX price to   account A



Performance Test



##  block evaluation:

mining rewards:

height interval c ( for example) 10000 = 3 hours  :


select number of   blocks pro miner with condition the block is not used for last reward and good rating
1) select last reward block and get the last height 
max height from list (select   from block evaluation where milestone = true and blocktype = reward)

2) select all blocks with height > last height + all blocks in block evaluation with rating < 75 

3) calculation of those  blocks  with:
coin= number * amount / total
  
create block with coinbase transaction for each miner coin 
 validation of reward block number* >  
 
(each miner for own reward or for all miner reward ?)
 
 
 Execute order
 
http://web.archive.org/web/20110310171841/http://www.quantcup.org/home/spec
 
 select all order for each token
 
 calculate the price for execution: price= max volume of orders
 max(buy limit) <  min(  sell limit)
 
 example:
 	EURO Token
 		1) buy  5  limit  7
 		2)					sell 8 limit 6
 		3) buy  8  limit  7.1
 		4)					sell 10 limit 6.5	
 	execution price = 6.5 with volume= 8+5
 					remainder 			
 	
 clearing
  
  seller exchange buyer	
  seller exchange system user, buyer does same.
  
  
  $ docker run -it --rm --net vnet smizy/apache-phoenix:4.13.1-alpine sh
> bin/sqlline-thin.py http://61.181.128.236:8765
bin/sqlline-thin.py http://42.51.129.106:8765


master hbase-site.xml

vi /usr/local/hbase-1.3.1/conf/hbase-site.xml.mustache

<property>
  <name>hbase.master.loadbalancer.class</name>                                     
  <value>org.apache.phoenix.hbase.index.balancer.IndexLoadBalancer</value>
</property>

<property>
  <name>hbase.coprocessor.master.classes</name>
  <value>org.apache.phoenix.hbase.index.master.IndexMasterObserver</value>
</property>


RegionServer  hbase-site.xml
docker exec -it hmaster-1 bash
docker exec -it regionserver-1 bash
vi /usr/local/hbase-1.3.1/conf/hbase-site.xml

docker exec hmaster-1 more  /usr/local/hbase-1.3.1/conf/hbase-site.xml
docker exec regionserver-1 more  /usr/local/hbase-1.3.1/conf/hbase-site.xml
docker exec queryserver-1 more  /usr/local/hbase-1.3.1/conf/hbase-site.xml

!indexs
docker restart zookeeper-1 namenode-1  datanode-1 hmaster-1 regionserver-1 queryserver-1 

docker logs -f  hmaster-1
 docker logs -f regionserver-1
<property> 
  <name>hbase.regionserver.wal.codec</name> 
  <value>org.apache.hadoop.hbase.regionserver.wal.IndexedWALEditCodec</value> 
</property>

<property> 
  <name>hbase.region.server.rpc.scheduler.factory.class</name>
  <value>org.apache.hadoop.hbase.ipc.PhoenixRpcSchedulerFactory</value> 
  <description>Factory to create the Phoenix RPC Scheduler that uses separate queues for index and metadata updates</description> 
</property>

<property>
  <name>hbase.rpc.controllerfactory.class</name>
  <value>org.apache.hadoop.hbase.ipc.controller.ServerRpcControllerFactory</value>
  <description>Factory to create the Phoenix RPC Scheduler that uses separate queues for index and metadata updates</description>
</property>

<property>
  <name>hbase.coprocessor.regionserver.classes</name>
  <value>org.apache.hadoop.hbase.regionserver.LocalIndexMerger</value> 
</property>

Given the schema shown here:

CREATE TABLE my_table (k VARCHAR PRIMARY KEY, v1 VARCHAR, v2 BIGINT);

you'd create an index on the v1 column like this:

CREATE INDEX my_index ON my_table (v1);


 CREATE TABLE headerstest  (  hash BINARY(32) not null,   height bigint ,  header VARBINARY(4000) ,   wasundoable boolean ,
prevblockhash  BINARY(32) ,     prevbranchblockhash  BINARY(32) ,
 mineraddress VARBINARY(255),     tokenid VARBINARY(255),     blocktype bigint ,
 CONSTRAINT headers_pk PRIMARY KEY (hash)   );
                
CREATE LOCAL INDEX headers_prevblockhash_idx ON headers (prevblockhash);
CREATE LOCAL INDEX headers_prevbranchblockhash_idx ON headers (prevbranchblockhash);


test of client needs the server to be started
 ServerStart
without arg is the default regnet for unit test
 --spring.profiles.active=testnet
--spring.profiles.active=mainnet
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

#For development using the latest eclipse
checkout this project and import this project. It may require the configure the project as gradle project.

use the format preference-> java -> code style -> formatter import the file designdoc/eclipse.xml
# design
Block is the class with list of transactions. It points to prevBlockHash and prevbranchBlockHash

persistence in db: block hash as key +  prevBlockHash and prevbranchBlockHash + ...

recursive select of the block DAG graph:

my child select * from block where prevBlockHash= :myblockhash
tip as block with no child, the select return empty


Transactions is defined by TransactionInput and TransactionOutput
TransactionInput has a point to the original output
TransactionOutPoint:  Hash of the transaction to which we refer and index 

TransactionOutput:
value and scriptPubKey




 class MySQLFullPrunedBlockStore:
 
   private static final String CREATE_HEADERS_TABLE = "CREATE TABLE headers (\n" +
            "    hash varbinary(28) NOT NULL,\n" +
            "    chainwork varbinary(12) NOT NULL,\n" +
            "    height integer NOT NULL,\n" +
            "    header varbinary(80) NOT NULL,\n" +
            "    wasundoable tinyint(1) NOT NULL,\n" +
            "    CONSTRAINT headers_pk PRIMARY KEY (hash) USING BTREE \n" +
            ")";

   private static final String CREATE_UNDOABLE_TABLE = "CREATE TABLE undoableblocks (\n" +
            "    hash varbinary(28) NOT NULL,\n" +
            "    height integer NOT NULL,\n" +
            "    txoutchanges mediumblob,\n" +
            "    transactions mediumblob,\n" +
            "    CONSTRAINT undoableblocks_pk PRIMARY KEY (hash) USING BTREE \n" +
            ")\n";
            
            
   private static final String CREATE_OPEN_OUTPUT_TABLE = "CREATE TABLE openoutputs (\n" +
            "    hash varbinary(32) NOT NULL,\n" +
            "    `index` integer NOT NULL,\n" +
            "    height integer NOT NULL,\n" +
            "    value bigint NOT NULL,\n" +
            "    scriptbytes mediumblob NOT NULL,\n" +
            "    toaddress varchar(35),\n" +
            "    addresstargetable tinyint(1),\n" +
            "    coinbase boolean,\n" +
            "    CONSTRAINT openoutputs_pk PRIMARY KEY (hash, `index`) USING BTREE \n" +
            ")\n";


==> changed tables 
	headers:  remove chainwork  
	          add  prevblockhash, prevbranchblockhash, mineraddress,tokenid, blocktype, time (UNIX time seconds)
	          change  header mediumblob
	


   private static final String CREATE_HEADERS_TABLE = "CREATE TABLE headers (\n" +
            "    hash varbinary(32) NOT NULL,\n" +
            "    height integer NOT NULL,\n" +
            "    header mediumblob NOT NULL,\n" +
            "    wasundoable tinyint(1) NOT NULL,\n" +
            "    prevblockhash  varbinary(32) NOT NULL,\n" +
            "    prevbranchblockhash  varbinary(32) NOT NULL,\n" +
            "    mineraddress varbinary(20),\n" +
            "    tokenid bigint,\n" +
            "    blocktype bigint NOT NULL,\n" +
            "    time bigint NOT NULL,\n" +
            "    CONSTRAINT headers_pk PRIMARY KEY (hash) USING BTREE \n" +
            ")";

openoutputs: add  blockhash, tokenid, spent, time (UNIX time seconds)

  private static final String CREATE_OPEN_OUTPUT_TABLE = "CREATE TABLE openoutputs (\n" +
            "    hash varbinary(32) NOT NULL,\n" +
            "    `index` integer NOT NULL,\n" +
            "    height integer NOT NULL,\n" +
            "    value bigint NOT NULL,\n" +
            "    scriptbytes mediumblob NOT NULL,\n" +
            "    toaddress varchar(35),\n" +
            "    addresstargetable tinyint(1),\n" +
            "    coinbase boolean,\n" +
            "    blockhash  varbinary(32)  NOT NULL,\n" +
            "    tokenid bigint,\n" +
            "    fromaddress varchar(35),\n" +
            "    description varchar(80),\n" +
            "    spent tinyint(1) NOT NULL,\n" +
            "    time bigint NOT NULL,\n" +
            "    CONSTRAINT openoutputs_pk PRIMARY KEY (hash, `index`) USING BTREE \n" +
            ")\n";


            

only keep undoable block  min (rating, days)       

? openoutouts delete,  no delete, set spent=true ??
? undoableblock delete, save to history data ?? 

1)client prepares new transactions and asks node server for account balance and unspent transaction. (input public key, toadress, amount)

2)server node does select two previous blocks (MCMC) and approve those only if all transactions in it are valid and  not conflicting and do not approve conflicting blocks.

3)server node returns the prepared transaction block with all client transactions. 

return Block with the  transaction 


4)client node must compute the nonce of the transaction block as proof of work and signs the transaction

. 
5) server get the block and goto 6a)
(input Block, return true/false) save block, broadcast
? add block in two parts, 
6a)when receiving blocks, only check static formal, save data in header table and broadcast to all server nodes. no update openoutputs)

6b)during milestone creation, when confirmation rate > 75% and no conflicts, add block data to milestone, 
update openoutputs

(API)


helper tables

 private static final String CREATE_BLOCK_TABLE = "CREATE TABLE tips (\n" +
            "    hash varbinary(32) NOT NULL,\n" +
            "    CONSTRAINT tips_pk PRIMARY KEY (hash) USING BTREE \n" +
            ")";
 
private static final String CREATE_BLOCKEVALUATION_TABLE = "CREATE TABLE BlockEvaluation (\n" +
            "    blockhash varbinary(32) NOT NULL,\n" +
            "    rating integer NOT NULL,\n" +
            "    depth integer \n" +
            "    cumulativeweight  integer ,\n" +      
            "    solid tinyint(1) NOT NULL,\n" +
            "    CONSTRAINT block_pk PRIMARY KEY (blockhash)  \n" +
            


add update, insert here DatabaseFullPrunedBlockStore and create table MySQLFullPrunedBlockStore

add service methods BlockService

 public BlockEvaluation  getBlockEvaluation(Sha256Hash hash) {
        // TODO Auto-generated method stub
        return null;
    }

public void updateSolidBlocks(Set<Sha256Hash> analyzedHashes) {
        // TODO Auto-generated method stub
        
    }

public void updateSolid(BlockEvaluation blockEvaluation, boolean b) {
        // TODO Auto-generated method stub
        
    }

add unit spring test for TipsServiceTest and BlockService


First step: use the existing p2p broadcasting.

Different to Peer to Peer broadcast is the clustered and quick as 

Kafka Stream:
dynamic discovery and swicht of Kafka cluster 
1)publish block to kafka
2)consumer block from kafka
3)relay kafka topic to all other cluster. (no cycle)


Performance Test



update block evaluation:

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



 1) coin balance overview client
 list of all balance per token
 2) transfer of coin
 transfer X coins from account A to account B
 3) create genesis block for new token 
 as example: IPO for new stock: Genesis Block of Stocks: XXX as token with number Y 
 4) transaction between token and coin
 Account A  buy stock YY with price XX  from  account B 
 Account A  tranfer  YY to   account B
 Account B tranfer  XX price to   account A



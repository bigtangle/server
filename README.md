test of client needs the server to be started
 ServerStart --spring.profiles.active=testnet

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


==> New tables for blocks and transactions:


   private static final String CREATE_BLOCK_TABLE = "CREATE TABLE block (\n" +
            "    hash varbinary(28) NOT NULL,\n" +
            "    prevblockhash  varbinary(28) NOT NULL,\n" +
            "    prevbranchblockhash  varbinary(28) NOT NULL,\n" +
            "    height integer NOT NULL,\n" +
            "    header varbinary(80) NOT NULL,\n" +
            "    mineraddress varchar(35),\n" +
            "    CONSTRAINT block_pk PRIMARY KEY (hash)  \n" +

  private static final String CREATE_TRANSACTION_OUTPUT_TABLE = "CREATE TABLE transaction (\n" +
            "    hash varbinary(32) NOT NULL,\n" +
            "    indexposition integer NOT NULL,\n" +
            "    blockhash  varbinary(28)  NOT NULL,\n" +
            "    prevtransactiohash  varbinary(28),\n" +
            "    value bigint NOT NULL,\n" +
            "    scriptbytes mediumblob NOT NULL,\n" +
            "    toaddress varchar(35),\n" +
            "    fromaddress varchar(35),\n" +
            "    addresstargetable tinyint(1),\n" +
            "    coinbase boolean,\n" +
            "    tokenid varchar(40),\n" +
            "    spent  tinyint(1) NOT NULL,\n" +
            "    CONSTRAINT transaction_pk PRIMARY KEY (hash, indexposition) \n" +
            ")\n";


helper tables

private static final String CREATE_MILESTONE_TABLE = "CREATE TABLE milestone (\n" +
            "    blockhash varbinary(28) NOT NULL,\n" +
            "    milestone integer NOT NULL,\n" +
            "    rating integer NOT NULL,\n" +
            "    CONSTRAINT block_pk PRIMARY KEY (blockhash)  \n" +

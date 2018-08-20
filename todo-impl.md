#TODO:
## Spark implementation of milestone:
1) load the init data for Hbase
2) build the graph 
3) evaluation of milestone
4) time refresh
5) read the new data from hbase or read it from kafka stream
6) milestone update and write evaluation data into hbase

## application monitoring health check and metrics

## Cleanup for source release
* Copyright notices fix
* Deprecated 'Bitcoin' mentions to be removed
* No StoredBlock
* BlockStatistics instead of BlockEvaluation
* clear irrelevant TODOs, e.g. rename old bitcoin todos
* no broken english, camelcase
* camelCase for at least
	** Tokens -> Token
	** TokenSerial
	** Uploadfile -> UploadFile
	** UploadfileInfo -> UploadFileInfo
	** UserData
	** UserdataInfo -> UserDataInfo
	** MultiSign
	** MultiSignAddress
	** MultiSignBy
	** PayMultiSign
	** PayMultiSignAddress
	** PayMultiSignExt
	** PayMultiSignInfo
	** UTXO
	** MultiSignService
	** all code using json "strings" to access fields
* remove unnecessary network parameters
* remove SnapShot.java
* Documentation
* refactor out AbstractBlockGraph and make it a service
* refactor blockservice, transactionservice methods

## Icebox:
* pruned validation process
* rating low pass filter 
* rebuild Tangle fcn, reattach tx fcn
* Reorg detection


## Smart Contracts
Let there be contract blocks with sequence numbers signifying state changes.
Let the reward be split among all miners in the next mining reward block.
Let all nodes validate all smart contracts that are approved.

Interface?  
Spam protection?  
Too much to calculate?  


## BigTangle Intranets

The BigTangle software can be deployed in private or other trusted environments, allowing one to run private, owned BigTangle networks with different rule sets. These BigTangle networks are arranged in a hierarchy, i.e. they possess a parent Tangle such as the Mainnet between which a transfer of values is facilitated. For this purpose, each new Tangle has its own interface accounts (addresses) from which it is possible to transfer funds into the parent Tangle and vice versa. 
A user interested in transferring funds from the parent Tangle into one of its registered child Tangles can transfer tokens to one of the child Tangle's interface accounts, at which points they are either accepted into the child Tangle or returned. Inside of such Intranets, the consensus protocol, transparency levels, permissiveness and other rules are set by the trusted Intranet owner and transfers of value can be performed internally as it is pleased. For example, in a work agency intranet it would be possible for clients to pay values to work forces in private and in arbitration of the owning work agency. 
In general, enterprises and governments can deploy the software internally and e.g. do KYC (Know Your Customer) as well as privacy protection while remaining compatible with BigTangle's Mainnet. This allows BigTangle to be a holistic approach to value management, enabling privacy, transparency and accountability whenever needed by banks, stock exchanges or enterprises.  



######Implementation notes:  

At nodes of the subnet, you can do value transfers as you can do in the Mainnet Tangle, but instead it all happens in the Subnet Tangle.  
You cannot do normal coinbases since they will not exist in the parent Tangle.  
(You can do normal coinbases if we allocate a separate token id address space for tokens unique to their Tangle (not transferrable to other Tangles))   
For now, there exists one master node that is in control of the master account in the parent Tangle. Per default, the master node defines consensus by signing off blocks as a coordinator.  
The master node (or other nodes if applicable) inside of the subnet do the following:   
They ask the parent Tangle nodes for the current UTXOs under the master account. If there are any new ones, they will create new corresponding UTXOs in the subnet under their account and forward the values to the intended recipient inside the subtangle if applicable.   

Define 

```
a 'parent Tangle' network id as the next higher hierarchy level 
a 'subnet registration block hash' that points to a block in the parent Tangle that contains information on:
	the Tangle network ID (Transaction bodies must contain network id in which they are valid)
	an empty genesis block (cannot have normal coinbase transactions in subtangles)
some reference 'parent Tangle nodes' to get information from
a 'master account' ?
and any other information necessary for a subnet such as consensus rules if applicable
```

new block types

```
'subnet transfer block' which additionally contains valid tx data that defines where to transfer to in the subnet + optional additional information.  
'parent transfer block' is added where the funds must be transferred to the master account analogously with valid tx data that defines where to transfer to in the parent Tangle.  
```

Default rules:  

```
When 'subnet transfer block' is CONFIRMED in the parent Tangle, we create a coinbase in the subnet and this block is now on a watchlist.  
When watched 'subnet transfer block' is UNCONFIRMED in the parent Tangle, we invalidate the coinbase block in the subnet.  
When 'parent transfer block' is CONFIRMED in the subnet, the master node sends value in parent Tangle and coinburn in subnet.  
MAY NOT HAPPEN: 'parent transfer block' is UNCONFIRMED in the subnet. (no influence on parent Tangle) -> responsibility of tangle owner -> no second public Tangles 
```

create master address as token 

create payment in parent
 1) address =master account
 2) subtangleid = target address

Subtangle
 find all new payment entry UTXO and find the relevant  block
 getBlock to read 
 create Multi  Token publish with  Coinbase transaction
 payment to target address


## Problems and proposed solutions
* Timing for mining tx generation

```
Schedule job: when job avg height surpasses a threshold, try to make one such tx.
Also, make sure it is valid. (Refactor mining tx generation)
```



* Reorganization process in case of network splits

```
Detection:
	Threshold of orphan percentage in time intervals (e.g. every 5 minutes) can allow us to detect significant network splits. Sample from all those new blocks do resolving with some of these blocks
	
Resolving:
	->Set maintenance depth:<- 
		Take the sampled recent blocks by descending height 
		Compute 'ratings' back until milestone block with over 50% is found before the bifurcation.
		Then set maintenance threshold back to the bifurcation - upper cutoff and perform normal milestone update
	Full Resync: Reset from zero and redownload
	Full Rebuild: Reset evaluations/statistics	
```



* Pruning process

```
z.B. Finalitätszeit 1 Tag im Milestone
+ Vorhaltezeit 3 Tage für manuelle Reorgs
Prune if no longer needed for sequentially ordered chains of blocks and ...
```



* Syncing from zero is unscalable, copying dbs is missing integrity

```
Options: 
	->Just sync from zero, should be possible after fixing consensus logic<-
	Synced Full-DB whose state can be checked afterwards
	Trusted Sync DB Copy 
```




* Tip selection algorithm

```
alpha selection by tx rate -> less tx left behind without fully compromising mcmc by allowing lazy random selections
DONE: depth alpha scaler -> Aviv Zohar Splitting Attack
?(penalize high height diff transition probability -> block subtangle prebuilder)
```


* Fuse logic for confirmation of TXOs (data of token issuances etc.), see mining reward as example

```
Use generalization of TXO logic:
Create entries in table on adding blocks.
Update the TXOs to be confirmed, spent if using TXOs are confirmed etc.
```



* Fuse logic for sequentially ordered chains of reward blocks (token issuance, smart contract), see mining reward as example

```
Use generalization of mining reward logic:
Let sequentially ordered blocks reference their predecessor. 
Let there be a solidity function that uses the previous block, the specified block and the Tangle.
Let the solidity function immediately be called upon adding the block (for solidity checks).
(It should be checked periodically since blocks can become valid after a while)
(or: when blocks can become valid later, add listener just like for cached blocks)
```


* Make milestone/etc. updates atomic

```
Needs changing the algorithm since it currently relies on getting updated blockevaluations to abort e.g. milestone update early.
```


* Smart Contract, Subdomains

```
See above
```

## setup of securiry of permissioned bigtangle
1) disable check of other wallet other then yours in wallet //K: should require valid signature for checking balance
2) restirct access of block transaction 
3) interface to add user access list as KYC




## add new api service for server batchBlock
Client send a block without solve

batchBlock (blockbyte)

write the  unsolved blocks in table BatchedBlock
   blockhash, block, inserttime
schedule job to create new Block
read the BatchedBlock with maximal size, extract all transactions from BatchedBlock
delete the entries
and add transactions to new Block , then  do a block solve 



## write serials article:


1) Is Bigtangle a true successor to Bitcoin?

2) Is Bigtangle with Subnet a protocol for internet of value

3) What is the Intrinsic Values of BigTangle?

4) Is the bigtangle exchange true decentralized?

5) Is the feeless better than model with fee? 
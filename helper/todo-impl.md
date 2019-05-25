#TODO:
## POW

## parameter tuning

## Spark implementation of milestone:
1) load the init data for Hbase
2) build the graph 
3) evaluation of milestone
4) time refresh
5) read the new data from hbase or read it from kafka stream
6) milestone update and write evaluation data into hbase

## application monitoring health check and metrics

## Cleanup for source release
before any release
* Copyright notices fix
* Remove private data such as passwords/keys from source release

eventually?
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

## setup of security of permissioned bigtangle
1) disable check of other wallet other then yours in wallet //K: should require valid signature for checking balance
2) restrict access of block transaction 
3) interface to add user access list as KYC User table as
4) define admin in config and setting 

* the block solve is done on the server only 
* constant block producer on each node, no race condition of hash power and no software manipulation
 
 changes needed:  
    1) configure the block solve and tip selection on the server only
    2) protect the kafka stream to connected with keys
    3) add filter for KYC public keys 



## add new api service for server batchBlock
Client send a block without solve

batchBlock (blockbyte)

write the  unsolved blocks in table BatchedBlock
   blockhash, block, inserttime
schedule job to create new Block
read the BatchedBlock with maximal size, extract all transactions from BatchedBlock
delete the entries
and add transactions to new Block , then  do a block solve 

add wallet pro Server config? or client config?


## write serials article:


1) Is Bigtangle a true successor to Bitcoin?

2) Is Bigtangle with Subnet a protocol for internet of value

3) What is the Intrinsic Values of BigTangle?

4) Is the bigtangle exchange true decentralized?

5) Is the feeless better than model with fee? 


## decentralized ordermatch
 1) order Block
 2) order value is locked for usage until the order deleted or cleared
 last price
  user order: buy gold X ,  price as caution
   * Buy side: User signs payment to special address: 111111* with amount = last price * order volume
      
   * sell side = USER SIGNS THE ORDER 
     User signs transfer order volume Y to special address
     
 3) ordermatching collect the order block and apply matching method, This method must run on all servers and produce some results.
 like reward block, the order matching block collect all order blocks by his referenced.
 
 Calculation: 1) determine the price, using the maximal volume
 			order execution with first in, first served 
 # clearing process:
 	User must pay the amount= max{ 0,  (Is price - Last Price)* order volume}
 	if user does not pay the additional amount, then the caution will be distributed to all selected seller 
 	transfer the order volume and BIG coin from special address to all users.
 	
 	unmatched order get the BIG and token back from 
 	     
 
## hbase does not work.


## fix problem of requester for missing previous block

## ask broadcast of blocks and condition

## optional remainder to new address after transfer (UTXO), problem with multi signature?


## KYC 
add possible to attach address at payment to KYC
** User upload the passport and encrypted with public key same as token
** user transfer the passport to a known address of verification
** User add post address +contact mobile number data in data windows
** verification is finished and transfer to public address 
** check of kyc: find the address as token in public address

 ** trusted center create token as trusted center for KYC.
 ** user transfers the uploaded passport
 ** trusted center checks the identity 
 ** trusted center transfer a token to the address 
 ** token can not be revoked, but should be a valid to date?
 
 ** user can define in his setting to trusted center as add the trusted center to his watched token
 ** user can set the acceptance for token transfer with only trusted center user
 ** user A   pay User B,  check solidity will be failed.
 
 ** exchange may define required trusted center for
 
## meta token with a list of token
To revoke given token, user can create a  token for external trusted token.
Example:
 Central bank create euro 2011, euro 2018
 meta token is euro with external URL
Call this URL will return of list of latest valid tokens: 
 euro version1= euro 2011, euro 2018
 euro version2 =  euro 2018
 This enable to full control of the valid token. Indirect the delete of token and in case of lost of key and control and user can create a new meta token.
 
This is an external dependency and will be not a part of BigTangle validation process.

## setup a list of token as same token 
** user load the same token list from a URL (token issuer) 
** mark the same token in display and mark the revoked token in color for help 
** watched one token will accept all same token.
** wallet pay meta token -> load latest list of token -> select a token from list and pay this token
** order match pro token, not meta token, otherwise the order match on chain will dependent on external resource.
 


## email add to airdrop -> done

## direct buy/sell from Wechat
## monitoring of application 


## permission bigTangle


    
## no empty block tip selection avoid conflicts  blocks
## empty block to select blocks in conflicts first

## display the history of spent transaction with 
   all my address or a given address
   select 1 Month, 6  or date from  date to 
   -> TODO 
## add restriction airdrop 
user must be activated via email and ask verification via mobile number after rewards > 10 millions

## rolling update of database table setting with a version and update the sql scripts against the version

## reentry of transaction in case of conflicts and low rating


## problem that schedule is down.  --> monitoring prometheus

## save kafka offset and write to database 


## add wallet option to pay fees for miner, miner has incentive to create real blocks, not only empty block. 
 1) server config and set price
 2)check mining address 
 3) miner set user table for fees in monthly
 
 
## wallet with two windows size 1000* 800 and 1600 * 1000  

## xmrig integration

https://github.com/xmrig/xmrig

  
## fiat payment and integration into bigtangle
* user pay the fiat money or any cryptocurrency with a given public address to a trusted account for example bank or paypal  account Inasset   --> cc.bigtangle.net start a payment via web app and check the payment, upload the public keys
* Inasset creates a multi sign token for the fiat money with same amount in BigTangle
* Inasset pay this token to the given public address.
* User pay this token to Token address and can ask transfer this token  to his bank account.

## mainnet start bigtangle and permissioned subtangle inasset exchange with KYC

## mainnet start with permissioned bigtangle and exchange with KYC
## start a permissioned subtangle for data blocks, with three cluster

## after permissioned phase, split mainnet in mainet and an exchange KYC subtangle

## central bank solution and demos

## fix first multi sign token must contain signature of token id (prevent duplicated attack )

## archtecture of bigtangle and subtangle

## javaFx to Mobile ? https://gluonhq.com/products/mobile/javafxports/

## shop cart problem after login missing data
 
## query of block as public service

## fix bug of add images in product  edit

## elk kibana for log monitor

## create kafka cluster and permission (protected)

## use spring jdbctemplate and c3po for pool. Problem with too many database connections.

## add permission to publish token

## add cache hazecast


## payment fiat to token

User start a payment with 80 yuan to yuan-token
 UI 1)
 -> Quantity: 80
 -> Address: 1Mxxxxx (set first as default, select one form wallet)
 -> Product: Yuan-Token
 
 Pay to product owner  80 yuan via bank or weixin
 Product owner confirm the payments.
 product owner create a yuan-token in BigTangle and pay the 80 Yuan-Token to the given address
 
 
 UI 2)
  -> Quantity: 80
 -> Address: 1Mxxxxx (set first as default, select one form wallet)
 -> Product: bc

 Pay to product owner  80 yuan via bank or weixin
 Product owner confirm the payments.
 
 product owner  pay the amount bc using the last exchange rate 80 Yuan-Token in bc  to the given address
 
 
 
 User get back the Yuan-Token in FIAT
 
 User refund the token:
 
 
 1) user pay the YUAN-Token to the product owner refund address
  -> write in field memo the account info (bank info?)
 3) product owner send 80 yuan to bank or wechat account:
 


## permission of token creation 

### new type of token with domain name
 server configuration parameter defines the root permission for single name as cn, com,  de etc.
 the creation of top name need the signature of root permission and user signature
 domain name is tree of permission
 the other domain need the signature of parent signature and user signature
 domain name is unique in system  -> ValidationService

example
 tokentype:domainname
 tokenname=de
 signatures: user + root 
 check: tokenname must be unique for tokentype domainname
 
 tokentype:domainname
 tokenname=bund.de
 signatures: user + domainame of de
 check: tokenname must be unique for tokentype domainname
 
 tokenname=gesund.bund.de
 
 
### other type of token must be have a domain name
   the token must be signed by domain name signature and user signature
example
 tokentype:token
 tokenname=product1
 domainname=bund.de
 signatures: user + domainname token
   

### display with tokenname + domainname +":"+ tokenid

### test of missing prev blocks and resync

### fix product token id save and check

### add keys check for correct 

### add cart and missing quantity and use one step for checkout and pay


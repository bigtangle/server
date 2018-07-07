#Test cases
Possible Participants:
* Normal User: Creates transactions every few seconds. Sometimes issues one of the following: (no BLOCKTYPE\_REWARD)  

	* BLOCKTYPE\_TRANSFER  
	* BLOCKTYPE\_TOKEN\_CREATION  
	* BLOCKTYPE\_USERDATA  
	* BLOCKTYPE\_VOS  
	* BLOCKTYPE\_GOVERNANCE  
	* BLOCKTYPE\_FILE  
	* BLOCKTYPE\_VOS\_EXECUTE  
	
	
* Conflicts: Create a conflicting block pair for all block types below. Check that no errors happen. 

	* BLOCKTYPE\_TRANSFER  
	* BLOCKTYPE\_TOKEN\_CREATION  
	
	 -> create token with block, but  publish first
	 -> 2. same token created and publish, save block
	 -> parallel save first block
	 
	* BLOCKTYPE\_USERDATA  
	* BLOCKTYPE\_VOS  
	* BLOCKTYPE\_GOVERNANCE  
	* BLOCKTYPE\_FILE  
	* BLOCKTYPE\_VOS\_EXECUTE  


* Conflict Issuer: Create a conflicting transaction. Then approve conflicting transactions at the same time.

	  Two parallel participants for e.g. 10 seconds:
	  * Setup: create conflicting transactions
	  * participant one creates 10 blocks per second normally (askTransaction --> EmptyBlocks)
	  * participant two creates 4 blocks per second without validating (and approving both conflicting transactions)		

* Selfish Subtangle: Approve your own Tangle only to gain mining rewards without validating anything.

	  Two parallel participants for e.g. 10 seconds:
	  * participant one creates 10 blocks per second normally (askTransaction --> EmptyBlocks)
	  * participant two creates 4 blocks per second without validation and by approving his own blocks only. 


* Network Spammer: Repeatedly create one of the following invalid blocks and check milestone update time does not become big

	* Transfer  Blocks  
	* Mining Reward Blocks  
	* Token Issuance Blocks  
	* Cross  Chain  Blocks  
	* Storage Blocks  
	* Governance Blocks  
	* Virtual OS Blocks   


* Subtangle Prebuilder: Create a Tangle of higher height than the Main Tangle by focusing PoW. Then issue reward block for the higher interval and try to push it.

	* Setup: big main tangle
	* Build thin, long, parallel tangle 
	  Two parallel participants for e.g. 30 seconds:
	  * participant one creates 10 blocks per second normally (askTransaction --> EmptyBlocks)
	  * participant two creates 4 blocks per second with one approval for main tangle, another for long parallel tangle 
	* Check that rating for long parallel tangle stays low
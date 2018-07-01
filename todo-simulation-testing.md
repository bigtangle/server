#Simulate attackers
Network Participants:
* Normal User: Creates transactions every few seconds. Sometimes issues one of the following: (no BLOCKTYPE\_REWARD)  
	* BLOCKTYPE\_TRANSFER  
	* BLOCKTYPE\_TOKEN\_CREATION  
	* BLOCKTYPE\_USERDATA  
	* BLOCKTYPE\_VOS  
	* BLOCKTYPE\_GOVERNANCE  
	* BLOCKTYPE\_FILE  
	* BLOCKTYPE\_VOS\_EXECUTE  
	
* Double Spender: Create a conflicting transaction when the other old transaction has already been approved. Then approve the new transaction and its tips but not the old transaction.


* Conflict Issuer: Create a conflicting transaction. Then approve conflicting transactions at the same time.
	simulation of decoupled network    30% transaction rate and 70 %transaction rate 

* Selfish Subtangle: Approve your own Tangle only to gain mining rewards without validating anything.

  case 1 + two Participants
  ** create 100 blocks without validation and use add only.
  ** create transaction to spent 
  Case 2
  ** askTransaction --> EmptyBlocks 50 
  ** use same transaction to double spent
  
  check rating case 2 > 1

* Partially Selfish Subtangle: Approve your own Tangle most of the time to gain mining rewards. This Tangle is made of empty blocks such that no conflicts occur. When your Tangle falls out of consensus, relink it with the Main Tangle, validating the main Tangle sometimes only. 
* Subtangle Prebuilder: Create a Tangle of higher height than the Main Tangle by focusing PoW. Then issue reward block for the higher interval and try to push it.
* Network Spammer: Repeatedly create one of the following invalid blocks and  by doing so try to slow down other nodes:  
	* Transfer  Blocks  
	* Mining Reward Blocks  
	* Token Issuance Blocks  
	* Cross  Chain  Blocks  
	* Storage Blocks  
	* Governance Blocks  
	* Virtual OS Blocks  
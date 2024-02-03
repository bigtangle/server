/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

/**
 *
 */
public class ContractEventCancel extends SpentBlock {

    // this is the block hash of the Order Block, which should be canceled
    private Sha256Hash eventBlockHash;

    // JSON
    public ContractEventCancel() {
    }

  

    public Sha256Hash getEventBlockHash() {
		return eventBlockHash;
	}



	public void setEventBlockHash(Sha256Hash eventBlockHash) {
		this.eventBlockHash = eventBlockHash;
	}



	public ContractEventCancel(Sha256Hash eventBlockHash) {
        super();
        setDefault(); 
        this.eventBlockHash = eventBlockHash;
    }

}

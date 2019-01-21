/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

/* This object being part of a signed transaction's data legitimates it
* the reclaim is solid without checking if prevblock is not predecessor to prevordermatchblock, 
* since confirming this block already implies confirmation of prevordermatchblock not spending the prevblock
*/
public class OrderReclaimInfo implements java.io.Serializable {

	private static final long serialVersionUID = 5955604810374397496L;
	
	private int opIndex;
	private Sha256Hash blockHash;
    private Sha256Hash nonConfirmingMatcherBlockHash;

    public OrderReclaimInfo() {
		super();
	}

	public OrderReclaimInfo(int opIndex, Sha256Hash blockHash, Sha256Hash nonConfirmingMatcherBlockHash) {
		super();
		this.opIndex = opIndex;
		this.blockHash = blockHash;
		this.nonConfirmingMatcherBlockHash = nonConfirmingMatcherBlockHash;
	}

	public int getOpIndex() {
		return opIndex;
	}

	public void setOpIndex(int opIndex) {
		this.opIndex = opIndex;
	}

	public Sha256Hash getOrderBlockHash() {
		return blockHash;
	}

	public void setTxHash(Sha256Hash txHash) {
		this.blockHash = txHash;
	}

	public Sha256Hash getNonConfirmingMatcherBlockHash() {
		return nonConfirmingMatcherBlockHash;
	}

	public void setNonConfirmingMatcherBlockHash(Sha256Hash nonConfirmingMatcherBlockHash) {
		this.nonConfirmingMatcherBlockHash = nonConfirmingMatcherBlockHash;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public byte[] toByteArray() {
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(this);
            return jsonStr.getBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    public static OrderReclaimInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);
        OrderReclaimInfo tokenInfo = Json.jsonmapper().readValue(jsonStr, OrderReclaimInfo.class);
        return tokenInfo;
    }
    
}

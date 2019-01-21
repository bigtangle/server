/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

// This object being part of a signed transaction's data legitimates it
public class OrderOpInfo implements java.io.Serializable {

	private static final long serialVersionUID = 5955604810374397496L;

	public static enum OrderOp {
		CANCEL, REFRESH
	}
	
	private OrderOp op;
	private int opIndex;
	private Sha256Hash txHash;

    public OrderOpInfo() {
		super();
	}

	public OrderOpInfo(OrderOp op, int opIndex, Sha256Hash txHash) {
		super();
		this.op = op;
		this.opIndex = opIndex;
		this.txHash = txHash;
	}

	public OrderOp getOp() {
		return op;
	}

	public void setOp(OrderOp op) {
		this.op = op;
	}

	public int getOpIndex() {
		return opIndex;
	}

	public void setOpIndex(int opIndex) {
		this.opIndex = opIndex;
	}

	public Sha256Hash getTxHash() {
		return txHash;
	}

	public void setTxHash(Sha256Hash txHash) {
		this.txHash = txHash;
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

    public static OrderOpInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);
        OrderOpInfo tokenInfo = Json.jsonmapper().readValue(jsonStr, OrderOpInfo.class);
        return tokenInfo;
    }
    
}

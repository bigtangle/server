/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class OrderOpenInfo implements java.io.Serializable {

	private static final long serialVersionUID = 433387247051352702L;
	
	private long targetValue; 
    private String targetTokenid;
    private byte[] beneficiaryPubKey;

    public OrderOpenInfo(long targetValue, String targetTokenid, byte[] beneficiaryPubKey) {
		super();
		this.targetValue = targetValue;
		this.targetTokenid = targetTokenid;
		this.beneficiaryPubKey = beneficiaryPubKey;
	}

	public byte[] getBeneficiaryPubKey() {
		return beneficiaryPubKey;
	}

	public void setBeneficiaryPubKey(byte[] beneficiaryPubKey) {
		this.beneficiaryPubKey = beneficiaryPubKey;
	}

	public long getTargetValue() {
		return targetValue;
	}

	public void setTargetValue(long targetValue) {
		this.targetValue = targetValue;
	}

	public String getTargetTokenid() {
		return targetTokenid;
	}

	public void setTargetTokenid(String targetTokenid) {
		this.targetTokenid = targetTokenid;
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

    public static OrderOpenInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);
        OrderOpenInfo tokenInfo = Json.jsonmapper().readValue(jsonStr, OrderOpenInfo.class);
        return tokenInfo;
    }
    
}

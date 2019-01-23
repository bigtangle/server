/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class OrderRecordInfo implements java.io.Serializable {

	private static final long serialVersionUID = -105528517457854614L;
	
	private Sha256Hash initialBlockHash;
    private Sha256Hash issuingMatcherBlockHash;

    public OrderRecordInfo() {
		super();
	}
    
    public OrderRecordInfo(Sha256Hash initialBlockHash, Sha256Hash issuingMatcherBlockHash) {
		super();
		this.initialBlockHash = initialBlockHash;
		this.issuingMatcherBlockHash = issuingMatcherBlockHash;
	}

	public Sha256Hash getInitialBlockHash() {
		return initialBlockHash;
	}

	public void setInitialBlockHash(Sha256Hash initialBlockHash) {
		this.initialBlockHash = initialBlockHash;
	}

	public Sha256Hash getIssuingMatcherBlockHash() {
		return issuingMatcherBlockHash;
	}

	public void setIssuingMatcherBlockHash(Sha256Hash issuingMatcherBlockHash) {
		this.issuingMatcherBlockHash = issuingMatcherBlockHash;
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

    public static OrderRecordInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);
        OrderRecordInfo tokenInfo = Json.jsonmapper().readValue(jsonStr, OrderRecordInfo.class);
        return tokenInfo;
    }
    
}

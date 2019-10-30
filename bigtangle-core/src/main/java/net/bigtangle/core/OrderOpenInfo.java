/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class OrderOpenInfo extends DataClass implements java.io.Serializable {

    private static final long FROMTIME = System.currentTimeMillis() / 1000 - 5;
    private static final long serialVersionUID = 433387247051352702L;
    private static final Logger logger = LoggerFactory.getLogger(OrderOpenInfo.class);
    private long targetValue;
    private String targetTokenid;
    //public key is needed for verify 
    private byte[] beneficiaryPubKey;
    // valid until this date, maximum is set in Network parameter
    private Long validToTime;
    // valid from this date, maximum is set in Network parameter
    private Long validFromTime;

    // owner public address of the order for query
    private String beneficiaryAddress;
    
    public OrderOpenInfo() {
        super();
    }

    public OrderOpenInfo(long targetValue, String targetTokenid, byte[] beneficiaryPubKey, Long validToTimeMilli,
            Long validFromTimeMilli, Side side,  String beneficiaryAddress) {
        super();
        this.targetValue = targetValue;
        this.targetTokenid = targetTokenid;
        this.beneficiaryPubKey = beneficiaryPubKey;
        if (validFromTimeMilli == null) {
            this.validFromTime = FROMTIME;
        } else {
            this.validFromTime = validFromTimeMilli / 1000;
        }
		if (validToTimeMilli == null) {
            this.validToTime = validFromTime + NetworkParameters.ORDER_TIMEOUT_MAX;
        } else {
            this.validToTime = Math.min(validToTimeMilli / 1000, validFromTime + NetworkParameters.ORDER_TIMEOUT_MAX);
        }
        this.beneficiaryAddress = beneficiaryAddress;
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
            logger.error("Json.jsonmapper error" + this.toString(), e);
            throw new RuntimeException(e);
        }
    }

    public static OrderOpenInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);
        OrderOpenInfo tokenInfo = Json.jsonmapper().readValue(jsonStr, OrderOpenInfo.class);
        return tokenInfo;
    }

    
    
    @Override
    public String toString() {
        return "OrderOpenInfo  \n targetValue=" + targetValue + ", \n targetTokenid=" + targetTokenid 
                + ", \n validToTime=" + validToTime + ",  \n validFromTime="
                + validFromTime + ", \n beneficiaryAddress=" + beneficiaryAddress;
    }

    public Long getValidToTime() {
        return validToTime;
    }

    public void setValidToTime(Long validToTime) {
        this.validToTime = validToTime;
    }

    public Long getValidFromTime() {
        return validFromTime;
    }

    public void setValidFromTime(Long validFromTime) {
        this.validFromTime = validFromTime;
    }

    public String getBeneficiaryAddress() {
        return beneficiaryAddress;
    }

    public void setBeneficiaryAddress(String beneficiaryAddress) {
        this.beneficiaryAddress = beneficiaryAddress;
    }

}

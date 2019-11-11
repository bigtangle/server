/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class OrderOpenInfo extends DataClass implements java.io.Serializable {

    private static final long FROMTIME = System.currentTimeMillis() / 1000 - 5;
    private static final long serialVersionUID = 433387247051352702L;
    
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);
            
            dos.write(super.toByteArray());
            
            dos.writeLong(targetValue);
            dos.writeLong(validToTime);
            dos.writeLong(validFromTime);
            dos.writeInt(beneficiaryPubKey.length);
            dos.write(beneficiaryPubKey);

            dos.writeBoolean(targetTokenid != null);
            if (targetTokenid != null) {
                dos.writeInt(targetTokenid.getBytes("UTF-8").length);
                dos.write(targetTokenid.getBytes("UTF-8"));
            }

            dos.writeBoolean(beneficiaryAddress != null);
            if (beneficiaryAddress != null) {
                dos.writeInt(beneficiaryAddress.getBytes("UTF-8").length);
                dos.write(beneficiaryAddress.getBytes("UTF-8"));
            }
            
            dos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
    }
    
    public OrderOpenInfo parseDIS(DataInputStream dis) throws IOException {
        super.parseDIS(dis);

        targetValue = dis.readLong();
        validToTime = dis.readLong();
        validFromTime = dis.readLong();
        int size = dis.readInt();
        beneficiaryPubKey = new byte[size];
        dis.readFully(beneficiaryPubKey);
        targetTokenid = Utils.readNBytesString(dis); 
        beneficiaryAddress = Utils.readNBytesString(dis); 
        
        return this;
    }

    public OrderOpenInfo parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);

        parseDIS(dis);
        
        dis.close();
        bain.close();
        return this;
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

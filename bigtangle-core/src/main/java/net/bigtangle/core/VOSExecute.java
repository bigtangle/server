package net.bigtangle.core;

import java.util.Date;
import java.util.HashMap;

public class VOSExecute implements java.io.Serializable {
    
    public byte[] toByteArray() {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("vosKey", vosKey);
        map.put("pubKey", pubKey);
        map.put("execute", execute);
        map.put("startDate", startDate.getTime());
        map.put("endDate", endDate.getTime());
        map.put("dataHex", Utils.HEX.encode(this.data));
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(map);
            return jsonStr.getBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    private static final long serialVersionUID = -3458226955228069779L;

    private String vosKey;
    
    private String pubKey;
    
    private long execute;
    
    private byte[] data;
    
    private Date startDate;
    
    private Date endDate;

    public String getVosKey() {
        return vosKey;
    }

    public void setVosKey(String vosKey) {
        this.vosKey = vosKey;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
    }

    public long getExecute() {
        return execute;
    }

    public void setExecute(long execute) {
        this.execute = execute;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }
}

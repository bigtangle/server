/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.util.ArrayList;
import java.util.List;

public class VOSInfo implements java.io.Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -2322159290888507509L;
    private List<VOSdata> voSdatas = new ArrayList<VOSdata>();

    public List<VOSdata> getVoSdatas() {
        return voSdatas;
    }

    public void setVoSdatas(List<VOSdata> voSdatas) {
        this.voSdatas = voSdatas;
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

    public VOSInfo parse(byte[] buf) {
        String jsonStr = new String(buf);
        try {
            VOSInfo vosinfo = Json.jsonmapper().readValue(jsonStr, VOSInfo.class);
            if (vosinfo == null)
                return this;
            this.voSdatas = vosinfo.getVoSdatas();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

}

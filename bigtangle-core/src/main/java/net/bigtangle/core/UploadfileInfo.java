/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class UploadfileInfo implements java.io.Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 5417612440113027518L;
    private List<Uploadfile> fUploadfiles = new ArrayList<Uploadfile>();

    public byte[] toByteArray() {
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(this);
            return jsonStr.getBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    public UploadfileInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);

        UploadfileInfo uploadfileInfo = Json.jsonmapper().readValue(jsonStr, UploadfileInfo.class);
        if (uploadfileInfo == null)
            return this;
        this.fUploadfiles = uploadfileInfo.getfUploadfiles();
        return this;
    }

    public List<Uploadfile> getfUploadfiles() {
        return fUploadfiles;
    }

    public void setfUploadfiles(List<Uploadfile> fUploadfiles) {
        this.fUploadfiles = fUploadfiles;
    }

}

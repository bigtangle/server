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

public class WatchedInfo implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -8908923887095777610L;
    private List<UserSettingData> userSettingDatas = new ArrayList<UserSettingData>();

    public byte[] toByteArray() {
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(this);
            return jsonStr.getBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    public WatchedInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);

        WatchedInfo watchedInfo = Json.jsonmapper().readValue(jsonStr, WatchedInfo.class);
        if (watchedInfo == null)
            return this;
        this.userSettingDatas = watchedInfo.getUserSettingDatas();
        return this;
    }

    public List<UserSettingData> getUserSettingDatas() {
        return userSettingDatas;
    }

    public void setUserSettingDatas(List<UserSettingData> userSettingDatas) {
        this.userSettingDatas = userSettingDatas;
    }

}

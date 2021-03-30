/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.bigtangle.utils.Json;

public class UserSettingDataInfo extends DataClass implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -8908923887095777610L;
    private List<UserSettingData> userSettingDatas = new ArrayList<UserSettingData>();

    public byte[] toByteArray() {
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(this);
            return jsonStr.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public UserSettingDataInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);

        UserSettingDataInfo userSettingDataInfo = Json.jsonmapper().readValue(jsonStr, UserSettingDataInfo.class);
        if (userSettingDataInfo == null)
            return this;
        this.userSettingDatas = userSettingDataInfo.getUserSettingDatas();

        return this;
    }

    public List<UserSettingData> getUserSettingDatas() {
        return userSettingDatas;
    }

    public void setUserSettingDatas(List<UserSettingData> userSettingDatas) {
        this.userSettingDatas = userSettingDatas;
    }

}

/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.http.server.resp;

import net.bigtangle.core.http.AbstractResponse;

public class SettingResponse extends AbstractResponse {

    private String version;

    public static AbstractResponse create(String version) {
        SettingResponse res = new SettingResponse();
        res.version = version;
        return res;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}

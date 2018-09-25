/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.http.server.resp;

import java.util.List;

import net.bigtangle.core.http.AbstractResponse;

public class UserDataResponse extends AbstractResponse {

    private List<String> dataList;

    public static AbstractResponse createUserDataResponse(List<String> dataList) {
        UserDataResponse res = new UserDataResponse();
        res.dataList = dataList;
        return res;
    }

    public List<String> getDataList() {
        return dataList;
    }

    public void setDataList(List<String> dataList) {
        this.dataList = dataList;
    }
}

/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.http.server.resp;

import java.util.List;
import java.util.Map;

import net.bigtangle.core.http.AbstractResponse;

public class SubtangleResponse extends AbstractResponse {

    private List<Map<String, String>> subtanglePermissionList;

    public static AbstractResponse createUserDataResponse(List<Map<String, String>> subtanglePermissionList) {
        SubtangleResponse res = new SubtangleResponse();
        res.subtanglePermissionList = subtanglePermissionList;
        return res;
    }

    public List<Map<String, String>> getSubtanglePermissionList() {
        return subtanglePermissionList;
    }

    public void setSubtanglePermissionList(List<Map<String, String>> subtanglePermissionList) {
        this.subtanglePermissionList = subtanglePermissionList;
    }

}

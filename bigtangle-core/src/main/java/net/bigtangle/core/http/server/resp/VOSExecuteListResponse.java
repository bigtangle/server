/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.server.resp;

import java.util.List;

import net.bigtangle.core.VOSExecute;
import net.bigtangle.core.http.AbstractResponse;

public class VOSExecuteListResponse extends AbstractResponse {

    private List<VOSExecute> vosExecutes;

    public static AbstractResponse create(List<VOSExecute> vosExecutes) {
        VOSExecuteListResponse res = new VOSExecuteListResponse();
        res.vosExecutes = vosExecutes;
        return res;
    }

    public List<VOSExecute> getVosExecutes() {
        return vosExecutes;
    }
}

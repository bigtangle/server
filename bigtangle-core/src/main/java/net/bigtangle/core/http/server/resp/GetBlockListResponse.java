/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.server.resp;

import java.util.List;

import net.bigtangle.core.http.AbstractResponse;

public class GetBlockListResponse extends AbstractResponse {
    private List<byte[]> blockbytelist;

    public static GetBlockListResponse create(List<byte[]> blockbytelist) {
        GetBlockListResponse res = new GetBlockListResponse();
        res.blockbytelist = blockbytelist;
        return res;
    }

    public List<byte[]> getBlockbytelist() {
        return blockbytelist;
    }

    public void setBlockbytelist(List<byte[]> blockbytelist) {
        this.blockbytelist = blockbytelist;
    }

  
}

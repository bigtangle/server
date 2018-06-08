/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.response;

public class PayMultiSignResponse extends AbstractResponse {

    private boolean success;
    
    public static AbstractResponse create(boolean success) {
        PayMultiSignResponse res = new PayMultiSignResponse();
        res.success = success;
        return res;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}

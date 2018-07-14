/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http;

public class OkResponse extends AbstractResponse {

    public static AbstractResponse create() {
        OkResponse res = new OkResponse();
        res.setErrorcode(0);
        return res;
    }
}

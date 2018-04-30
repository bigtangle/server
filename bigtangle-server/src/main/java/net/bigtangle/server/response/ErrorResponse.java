/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.response;

public class ErrorResponse extends AbstractResponse {

    private String error;

    public static AbstractResponse create(String error) {
        ErrorResponse res = new ErrorResponse();
        res.error = error;
        return res;
    }

    public String getError() {
        return error;
    }
}

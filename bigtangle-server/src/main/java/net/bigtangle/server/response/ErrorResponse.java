/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.response;

public class ErrorResponse extends AbstractResponse {

  

    public static AbstractResponse create(int error) {
        ErrorResponse res = new ErrorResponse();
        res.setErrorcode(error);
        return res;
    }

   
}

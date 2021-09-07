/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.response;

public class BooleanResponse extends AbstractResponse {

    private Boolean  value;
 
    public static BooleanResponse create(Boolean  value) {
        BooleanResponse res = new BooleanResponse();
        res.value =  value;
        return res;
    }
 
    
     
}

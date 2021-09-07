/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.response;

public class LongResponse extends AbstractResponse {

    private Long  value;
 
    public static LongResponse create(Long  value) {
        LongResponse res = new LongResponse();
        res.value =  value;
        return res;
    }
 
    
     
}

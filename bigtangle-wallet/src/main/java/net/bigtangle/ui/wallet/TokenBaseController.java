/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.util.List;

import net.bigtangle.core.Token;

@SuppressWarnings("rawtypes")
public class TokenBaseController {
    //define only common parameter for Token Controller
   
    

    public Token getToken(List<Token> tokens, String tokenid) throws Exception {
        for (Token t : tokens) {
            if (t.getTokenid().equals(tokenid))
                return t;
        }
        return null;
    }


}

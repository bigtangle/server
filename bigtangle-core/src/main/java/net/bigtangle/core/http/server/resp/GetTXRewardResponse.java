/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.server.resp;

import net.bigtangle.core.TXReward;
import net.bigtangle.core.http.AbstractResponse;

public class GetTXRewardResponse extends AbstractResponse {
  
    private   TXReward txReward;
  
 

    public static GetTXRewardResponse create( TXReward txReward) {
        GetTXRewardResponse res = new GetTXRewardResponse();
        res.txReward = txReward;
        return res;
    }



    public TXReward getTxReward() {
        return txReward;
    }



    public void setTxReward(TXReward txReward) {
        this.txReward = txReward;
    }
 
}

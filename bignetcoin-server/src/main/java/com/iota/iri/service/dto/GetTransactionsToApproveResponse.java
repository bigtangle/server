/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.iota.iri.service.dto;

import org.bitcoinj.core.Sha256Hash;

public class GetTransactionsToApproveResponse extends AbstractResponse {

    private String trunkTransaction;
    private String branchTransaction;

    public static AbstractResponse create(Sha256Hash trunkTransactionToApprove, Sha256Hash branchTransactionToApprove) {
        GetTransactionsToApproveResponse res = new GetTransactionsToApproveResponse();
        res.trunkTransaction = trunkTransactionToApprove.toString();
        res.branchTransaction = branchTransactionToApprove.toString();
        return res;
    }

    public String getBranchTransaction() {
        return branchTransaction;
    }

    public String getTrunkTransaction() {
        return trunkTransaction;
    }

}

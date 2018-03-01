/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.iota.iri.service.dto;

import java.util.List;

public class GetNeighborsResponse extends AbstractResponse {

    private Neighbor[] neighbors;

    public Neighbor[] getNeighbors() {
        return neighbors;
    }

    static class Neighbor {

        private String address;
        public long numberOfAllTransactions, numberOfRandomTransactionRequests, numberOfNewTransactions, numberOfInvalidTransactions, numberOfSentTransactions;
        public String connectionType;

        public String getAddress() {
            return address;
        }

        public long getNumberOfAllTransactions() {
            return numberOfAllTransactions;
        }

        public long getNumberOfNewTransactions() {
            return numberOfNewTransactions;
        }

        public long getNumberOfInvalidTransactions() {
            return numberOfInvalidTransactions;
        }
        
        public long getNumberOfSentTransactions() {
            return numberOfSentTransactions;
        }

        public String getConnectionType() {
            return connectionType;
        }
    }

}

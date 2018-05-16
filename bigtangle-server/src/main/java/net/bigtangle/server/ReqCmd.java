/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

public enum ReqCmd {

    getBalances, askTransaction, saveBlock, getOutputs,

    createGenesisBlock, exchangeToken, getTokens, getAllEvaluations,

    outputsWiteToken, saveOrder, getOrders, exchangeInfo, batchGetBalances,

    saveExchange, getExchange, signTransaction, searchBlock, getBlock, streamBlocks, 
    
    getMultisignaddress, addMultisignaddress, delMultisignaddress, multiSign;
}

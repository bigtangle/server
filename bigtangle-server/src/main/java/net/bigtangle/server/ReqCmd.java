/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

public enum ReqCmd {

    getBalances, askTransaction, saveBlock, getOutputs,

    createGenesisBlock, exchangeToken, getTokens, getTokenById, getAllEvaluations,

    outputsWiteToken, saveOrder, getOrders, exchangeInfo, batchGetBalances,

    saveExchange, getExchange, signTransaction, searchBlock, getBlock, streamBlocks,

    getMultiSignWithAddress, getMultiSignWithTokenid, multiSign, getGenesisBlockLR, getTokenSerials, getCalTokenIndex, getCountSign;
}

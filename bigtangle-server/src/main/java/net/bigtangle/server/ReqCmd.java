/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

public enum ReqCmd {

    askTransaction, saveBlock, getOutputs, outpusWithHexStr,

    createGenesisBlock, exchangeToken, getTokens, getTokensNoMarket, getMarkets, getTokenById, getAllEvaluations,

    outputsWiteToken, batchGetBalances, searchBlock, getBlock, streamBlocks,

    getMultiSignWithAddress, getMultiSignWithTokenid,

    multiSign, getGenesisBlockLR, getTokenSerials, getCalTokenIndex, getCountSign, updateTokenInfo, getUserData,
    
    launchPayMultiSign, payMultiSign, getPayMultiSignList, getPayMultiSignAddressList, payMultiSignDetails;
}

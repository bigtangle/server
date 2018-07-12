/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

public enum ReqCmd {

    askTransaction, saveBlock, getOutputs, outpusWithHexStr,outputsWithHexStr,

     exchangeToken, getTokens, getTokensNoMarket, getMarkets, getTokenById, getAllEvaluations,

    outputsWiteToken, outputsWithToken, batchGetBalances, searchBlock, getBlock, streamBlocks,

    getMultiSignWithAddress, getMultiSignWithTokenid,

    multiSign, getTokenSerials, getCalTokenIndex, getCountSign, updateTokenInfo, getUserData, userDataList,
    
    launchPayMultiSign, payMultiSign, getPayMultiSignList, getPayMultiSignAddressList, payMultiSignDetails,
    
    getVOSExecuteList, version, submitLogResult;
}

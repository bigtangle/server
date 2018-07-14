/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.params;

public enum ReqCmd {

    askTransaction, saveBlock, getOutputs, outpusWithHexStr,

     exchangeToken, getTokens, getTokensNoMarket, getMarkets, getTokenById, getAllEvaluations,

    outputsWiteToken, outputsWithToken, batchGetBalances, searchBlock, getBlock, streamBlocks,

    getMultiSignWithAddress, getMultiSignWithTokenid,

    multiSign, getTokenSerials, getCalTokenIndex, getCountSign, updateTokenInfo, getUserData, userDataList,
    
    launchPayMultiSign, payMultiSign, getPayMultiSignList, getPayMultiSignAddressList, payMultiSignDetails,
    
    getVOSExecuteList, version, submitLogResult;
}

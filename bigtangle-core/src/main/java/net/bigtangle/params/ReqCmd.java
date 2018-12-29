/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.params;

public enum ReqCmd {

    getTip, saveBlock, getOutputs, getTokens, getTokensNoMarket, getMarkets, getTokenById,

    getBalances, searchBlock, getBlock, streamBlocks,

    getMultiSignWithAddress, getMultiSignWithTokenid,

    multiSign, getTokenSerials, getCalTokenIndex, getCountSign, updateTokenInfo, getUserData, userDataList,

    launchPayMultiSign, payMultiSign, getPayMultiSignList, getPayMultiSignAddressList, payMultiSignDetails,

    getVOSExecuteList, version, submitLogResult, getOutputWithKey, batchBlock, getOutputMultiList,getOutputsHistory,regSubtangle;
}

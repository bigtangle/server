/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.params;

public enum ReqCmd {
    // Block
    saveBlock, getBlockByHash, findBlockEvaluation, batchBlock,

    getTip, adjustHeight,
    // Chain
    getMaxConfirmedReward, getAllConfirmedReward, blocksFromChainLength,
    // Token
    searchTokens, getTokenById, getTokenIndex, getTokenSignByAddress,

    getTokenSignByTokenid, signToken, getTokenSigns, getTokenPermissionedAddresses, getDomainNameBlockHash,
    // Block Order
    getOrders, getOTCMarkets, getOrdersTicker, signOrder,
    // Outputs
    getOutputByKey, getOutputs, getOutputsHistory, outputsOfTokenid, getBalances,

    // payment
    launchPayMultiSign, payMultiSign,

    getPayMultiSignList, getPayMultiSignAddressList, payMultiSignDetails,
    // Direct exchange
    getExchangeByOrderid, saveExchange,

    getUserData, userDataList, getBatchExchange,
    // subtangle
    regSubtangle, updateSubtangle;

}

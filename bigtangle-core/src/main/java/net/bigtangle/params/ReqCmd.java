/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.params;

public enum ReqCmd {
    // Block
    saveBlock, getBlockByHash, findBlockEvaluation,searchBlockByBlockHashs, batchBlock, blockEvaluationFromHashs,

    getTip, adjustHeight,findRetryBlocks,
    // Chain
    getMaxConfirmedReward, getAllConfirmedReward, blocksFromChainLength,
    // Token
    searchTokens, getTokenById, getTokenIndex, getTokenSignByAddress, searchExchangeTokens,

    getTokenSignByTokenid, signToken, getTokenSigns, getTokenPermissionedAddresses, getDomainNameBlockHash,
    // Block Order
    getOrders, getOTCMarkets, getOrdersTicker, signOrder,
    // Outputs
    getOutputByKey, getOutputs, getOutputsHistory, outputsOfTokenid, getBalances,

    // payment
    launchPayMultiSign, payMultiSign,

    getPayMultiSignList, getPayMultiSignAddressList, payMultiSignDetails,
    // Direct exchange
    getExchangeByOrderid, saveExchange,deleteExchange,

    getUserData, userDataList, getBatchExchange,
    // subtangle
    regSubtangle, updateSubtangle;

}

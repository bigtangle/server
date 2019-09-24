/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.params;

public enum ReqCmd {

    getTip, saveBlock, getOutputs, getBalances, searchBlock, searchBlockByBlockHash, getBlock,
    
    blocksFromChainLength, streamBlocks, getOutputsHistory,adjustHeight, outputsbyToken,
    
    getMaxConfirmedReward,getAllConfirmedReward,

    getTokens, getTokensAmount, getTokenById, getToken, getTokenSerials, getCalTokenIndex, updateTokenInfo,

    getOrders, getOTCMarkets, getOrdersTicker,

    getMultiSignWithAddress, getMultiSignWithTokenid, getOutputWithKey,

    multiSign, getCountSign, launchPayMultiSign, payMultiSign, getPayMultiSignList, getPayMultiSignAddressList, payMultiSignDetails,

    getUserData, userDataList, getVOSExecuteList, version, batchBlock,

    regSubtangle, getSubtanglePermissionList, getAllSubtanglePermissionList,

    getSubtanglePermissionListByPubkeys, updateSubtangle, queryPermissionedAddresses, findDomainPredecessorBlockHash,

    signTransaction, exchangeInfo, exchangeMultiSignTransaction, saveExchange;
}

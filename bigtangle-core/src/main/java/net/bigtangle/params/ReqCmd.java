/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.params;

public enum ReqCmd {

    getTip, saveBlock, getOutputs, getBalances, findBlockEvaluation, getBlockByBlockHash, getBlock,
    
    blocksFromChainLength,  getOutputsHistory,adjustHeight, outputsOfTokenid,
    
    getMaxConfirmedReward,getAllConfirmedReward,

    searchTokens,  getTokenById,  getCalTokenIndex, 

    getOrders, getOTCMarkets, getOrdersTicker,signTransaction,

    getMultiSignWithAddress, getMultiSignWithTokenid, getOutputWithKey,

    multiSign, getCountSign, launchPayMultiSign, payMultiSign, getPayMultiSignList, getPayMultiSignAddressList, payMultiSignDetails,

    getUserData, userDataList, getVOSExecuteList, version, batchBlock,

    regSubtangle, getSubtanglePermissionList, getAllSubtanglePermissionList,

    getSubtanglePermissionListByPubkeys, updateSubtangle, queryPermissionedAddresses, findDomainNameBlockHash,

     exchangeInfo, exchangeMultiSignTransaction, saveExchange;
}

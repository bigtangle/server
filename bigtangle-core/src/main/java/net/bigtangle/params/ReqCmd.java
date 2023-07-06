/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.params;

public enum ReqCmd {
	// Block
	saveBlock, getBlockByHash, findBlockEvaluation, searchBlockByBlockHashs, batchBlock, blockEvaluationFromHashs,

	getTip, adjustHeight, findRetryBlocks,
	// Chain
	getChainNumber, getAllConfirmedReward, blocksFromChainLength,blocksFromNonChainHeight,
	// Token
	searchTokens, getTokenById, getTokenIndex, getTokenSignByAddress, searchExchangeTokens, searchTokenDomain,searchWebTokens,

	getTokenSignByTokenid, signToken, getTokenSigns, getTokenPermissionedAddresses, getDomainNameBlockHash,
	// Block Order
	getOrders, getOrdersTicker,
	// Outputs
	getOutputByKey, getOutputs, getOutputsHistory, outputsOfTokenid, getBalances,outputsByBlockhash,getAccountBalances,fixAccountBalance,

	// payment
	launchPayMultiSign, payMultiSign,

	getPayMultiSignList, getPayMultiSignAddressList, payMultiSignDetails,
	 
	//user data
	getUserData, userDataList,  
	// subtangle
	regSubtangle, updateSubtangle, getSessionRandomNum,
	// permissioned
	addAccessGrant, deleteAccessGrant,
	// Smart Contract
	getContractEvents,
    // check point value
    getCheckPoint,
    serverinfolist;
}

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
	searchTokens, getTokenById, getTokenIndex, getTokenSignByAddress, searchExchangeTokens, searchTokenDomain,

	getTokenSignByTokenid, signToken, getTokenSigns, getTokenPermissionedAddresses, getDomainNameBlockHash,
	// Block Order
	getOrders, getOTCMarkets, getOrdersTicker, signOrder,
	// Outputs
	getOutputByKey, getOutputs, getOutputsHistory, outputsOfTokenid, getBalances,outputsByBlockhash,

	// payment
	launchPayMultiSign, payMultiSign,

	getPayMultiSignList, getPayMultiSignAddressList, payMultiSignDetails,
	// Direct exchange
	getExchangeByOrderid, saveExchange, deleteExchange,

	getUserData, userDataList, getBatchExchange,
	// subtangle
	regSubtangle, updateSubtangle, getSessionRandomNum,
	// permissioned
	addAccessGrant, deleteAccessGrant,
	// Smart Contract
	getContractEvents,
    // check point value
    getCheckPoint,
	//addon service userpay
	saveUserpay, deleteUserpay
}

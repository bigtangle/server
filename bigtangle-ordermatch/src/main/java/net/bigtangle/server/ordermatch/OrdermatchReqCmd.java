/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch;

public enum OrdermatchReqCmd {

    saveOrder, getOrders, deleteOrder, exchangeInfo,

    saveExchange, getExchange, signTransaction, cancelOrder, getBatchExchange,multisign;
}

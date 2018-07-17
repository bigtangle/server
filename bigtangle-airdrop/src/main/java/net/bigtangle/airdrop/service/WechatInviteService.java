/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.airdrop.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.airdrop.store.FullPrunedBlockStore;

@Service
public class WechatInviteService {

    @Autowired
    protected FullPrunedBlockStore store;

}

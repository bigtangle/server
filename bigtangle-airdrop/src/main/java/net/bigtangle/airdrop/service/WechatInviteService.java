/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.airdrop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.airdrop.store.FullPrunedBlockStore;
import net.bigtangle.core.BlockStoreException;

@Service
public class WechatInviteService {

    @Autowired
    protected FullPrunedBlockStore store;

    public void clearWechatInviteStatus() {
        try {
            this.store.clearWechatInviteStatusZero();
            LOGGER.info("clear wechat invite status success");
            // clear order data
            this.store.resetDepositPaid();
            LOGGER.info(" reset deposit  status success");
        } catch (BlockStoreException e) {
            LOGGER.error(" reset status error", e);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(WechatInviteService.class);
}

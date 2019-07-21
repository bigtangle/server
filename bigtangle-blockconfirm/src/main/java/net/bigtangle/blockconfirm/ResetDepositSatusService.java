/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.blockconfirm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.blockconfirm.store.FullPrunedBlockStore;
import net.bigtangle.core.exception.BlockStoreException;

@Service
public class ResetDepositSatusService {

    @Autowired
    protected FullPrunedBlockStore store;

    public void clearStatus() {
        try {
               // clear order data
            this.store.resetDepositPaid();
            LOGGER.info(" reset deposit  status success");
        } catch (BlockStoreException e) {
            LOGGER.error(" reset status error", e);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ResetDepositSatusService.class);
}

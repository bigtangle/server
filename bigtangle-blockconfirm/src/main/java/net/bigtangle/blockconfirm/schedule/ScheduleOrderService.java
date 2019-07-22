/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.blockconfirm.schedule;

import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.bigtangle.blockconfirm.bean.Vm_deposit;
import net.bigtangle.blockconfirm.config.ScheduleConfiguration;
import net.bigtangle.blockconfirm.store.FullPrunedBlockStore;
import net.bigtangle.blockconfirm.utils.GiveMoneyUtils;
import net.bigtangle.core.Coin;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.BlockStoreException;

@Component
@EnableAsync
public class ScheduleOrderService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleOrderService.class);

    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Autowired
    private GiveMoneyUtils giveMoneyUtils;

    @Autowired
    private FullPrunedBlockStore store;

    @Scheduled(fixedRateString = "${service.giveMoneyService.rate:10000}")
    public void updateMilestoneService() throws Exception {
        if (scheduleConfiguration.isGiveMoneyServiceActive()) {

            logger.debug(" Start ScheduleGiveMoneyOrderService");

            // select all order not Status=PAID and Status=CONFIRM
            List<Vm_deposit> deposits = sendFromOrder();
            // if not paid then do transfer and pay
            if (giveMoneyUtils.batchGiveMoneyToECKeyList(giveMoneyResult(deposits))) {

                // only update, if money is given for order

                for (Vm_deposit d : deposits) {

                    this.store.updateDepositStatus(d.getUserid(), d.getUseraccount(), "PAID");
                    logger.info("  update deposit : " + d.getUserid() + ", success");

                }
            }

            // if Status=PAID then check block valuation with rating > 75, set
            // Status=CONFIRM
            // searchBlockByBlockHash
            deposits = this.store.queryDepositByStatus("PAID");
            for (Vm_deposit vm_deposit : deposits) {

            }
            // otherwise do again the giveMoneyUtils.batchGiveMoneyToECKeyList,
            // timeout = 60 minutes rating < 75

        }
    }

    private List<Vm_deposit> sendFromOrder() throws BlockStoreException {
        return this.store.queryDepositKeyFromOrderKey();

    }

    private HashMap<String, Long> giveMoneyResult(List<Vm_deposit> l) throws BlockStoreException {
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        for (Vm_deposit d : l) {
            giveMoneyResult.put(d.getPubkey(),
                    Coin.parseCoin(d.getAmount().longValue() + "", NetworkParameters.BIGTANGLE_TOKENID).getValue());
        }
        return giveMoneyResult;
    }

}

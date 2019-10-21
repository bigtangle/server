/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.blockconfirm.schedule;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.time.DateUtils;
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
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Json;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.GetBlockEvaluationsResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.OkHttp3Util;

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
    public void updatemcmcService() throws Exception {
        // if (scheduleConfiguration.isGiveMoneyServiceActive()) {

        logger.debug(" Start ScheduleGiveMoneyOrderService");

        // select all order not Status=PAID and Status=CONFIRM
        List<Vm_deposit> deposits = sendFromOrder();
        // if not paid then do transfer and pay
        Block b = giveMoneyUtils.batchGiveMoneyToECKeyList(giveMoneyResult(deposits));
        if (b != null) {
            // only update, if money is given for order

            for (Vm_deposit d : deposits) {

                this.store.updateDepositStatus(d.getUserid(), d.getUseraccount(), "PAID", b.getHashAsString());
                logger.info("  update deposit : " + d.getUserid() + ", success");

            }
        }

        // if Status=PAID then check block valuation with rating > 75, set
        // Status=CONFIRM
        // searchBlockByBlockHash
        deposits = this.store.queryDepositByStatus("PAID");
        List<Vm_deposit> subDeposits = new ArrayList<Vm_deposit>();
        for (Vm_deposit vm_deposit : deposits) {
            if (vm_deposit.getBlockhash() == null || vm_deposit.getBlockhash().trim().isEmpty()) {
                continue;
            }
            Map<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("blockhash", vm_deposit.getBlockhash());
            String response = OkHttp3Util.postString(
                    scheduleConfiguration.getServerURL() + ReqCmd.getBlockByHash.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                    GetBlockEvaluationsResponse.class);
            List<BlockEvaluationDisplay> blockEvaluations = getBlockEvaluationsResponse.getEvaluations();
            if (blockEvaluations != null && !blockEvaluations.isEmpty()) {
                if (blockEvaluations.get(0).getMilestone() >= 0) {
                    this.store.updateDepositStatus(vm_deposit.getUserid(), vm_deposit.getUseraccount(), "CONFIRM",
                            vm_deposit.getBlockhash());
                }
                // otherwise do again the
                // giveMoneyUtils.batchGiveMoneyToECKeyList,
                // timeout = 60 minutes rating < 75
                else {
                    Date date = new Date(blockEvaluations.get(0).getInsertTime());
                    if (DateUtils.addMinutes(date, 60).before(new Date())) {
                        subDeposits.add(vm_deposit);
                    }
                }
            }

        }
        giveMoneyUtils.batchGiveMoneyToECKeyList(giveMoneyResult(subDeposits));

    }

    private List<Vm_deposit> sendFromOrder() throws BlockStoreException {
        return this.store.queryDepositKeyFromOrderKey();

    }

    private HashMap<String, Long> giveMoneyResult(List<Vm_deposit> l) throws BlockStoreException {
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        for (Vm_deposit d : l) {
            if (giveMoneyResult.containsKey(d.getPubkey())) {
                long temp = giveMoneyResult.get(d.getPubkey());
                BigInteger my = MonetaryFormat.FIAT.noCode().parse(d.getAmount().longValue() + "").getValue();
                giveMoneyResult.put(d.getPubkey(), my.longValue() + temp);
            } else {

                giveMoneyResult.put(d.getPubkey(),
                        MonetaryFormat.FIAT.noCode().parse(d.getAmount().longValue() + "").getValue().longValue());

            }

        }
        return giveMoneyResult;
    }

}

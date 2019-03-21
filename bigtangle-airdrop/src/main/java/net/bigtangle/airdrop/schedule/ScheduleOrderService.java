/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.airdrop.schedule;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.bigtangle.airdrop.config.ScheduleConfiguration;
import net.bigtangle.airdrop.store.FullPrunedBlockStore;
import net.bigtangle.airdrop.utils.GiveMoneyUtils;
import net.bigtangle.core.Address;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.params.MainNetParams;

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
	public void updateMilestoneService() {
		if (scheduleConfiguration.isGiveMoneyServiceActive()) {
			try {
				logger.debug(" Start ScheduleGiveMoneyOrderService"); 
				HashMap<String, Integer> giveMoneyResult = new HashMap<String, Integer>();

				HashMap<String, Integer> orderMap = sendFromOrder(giveMoneyResult);

				if (giveMoneyUtils.batchGiveMoneyToECKeyList(giveMoneyResult)) {

					// only update, if money is given for order
					HashMap<Long, String> map = this.store.queryDepositKeyFromOrderKey();
					for (String string : orderMap.keySet()) {
						for (Map.Entry<Long, String> entry : map.entrySet()) {
							Long userid = entry.getKey();
							String useraccount = entry.getValue().split("-")[0];
							String pubkey = entry.getValue().split("-")[1];
							if (string.equals(pubkey)) {
								boolean flag = true;
								try {
									Address.fromBase58(MainNetParams.get(), pubkey);
								} catch (Exception e) {
									// logger.debug("", e);
									flag = false;
								}
								if (flag) {
									this.store.updateDepositStatus(userid, useraccount, "PAID");
									logger.info("wechat invite update status, id : " + userid + "," + useraccount
											+ ", success");
									break;
								}
							}

						}
					}

				}
			} catch (Exception e) {
				logger.warn("ScheduleGiveMoneyService", e);
			}
		}
	}

	private HashMap<String, Integer> sendFromOrder(HashMap<String, Integer> giveMoneyResult) {
		try {
			HashMap<String, Integer> map = this.store.queryFromOrder();

			giveMoneyResult.putAll(map);

		} catch (BlockStoreException e) {
			logger.warn("ScheduleGiveMoneyService", e);
		}
		return giveMoneyResult;
	}

}

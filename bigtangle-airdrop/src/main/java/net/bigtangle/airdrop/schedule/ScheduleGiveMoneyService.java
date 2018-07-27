/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.airdrop.schedule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mchange.v2.lang.StringUtils;

import net.bigtangle.airdrop.bean.WechatInvite;
import net.bigtangle.airdrop.config.ScheduleConfiguration;
import net.bigtangle.airdrop.store.DatabaseFullPrunedBlockStore;
import net.bigtangle.airdrop.utils.GiveMoneyUtils;
import net.bigtangle.core.ECKey;

@Component
@EnableAsync
public class ScheduleGiveMoneyService {

	private static final Logger logger = LoggerFactory.getLogger(ScheduleGiveMoneyService.class);

	@Autowired
	private ScheduleConfiguration scheduleConfiguration;

	@Autowired
	private GiveMoneyUtils giveMoneyUtils;

	@Autowired
	private DatabaseFullPrunedBlockStore store;

	@Scheduled(fixedRateString = "${service.giveMoneyService.rate:10000}")
	public void updateMilestoneService() {
		if (scheduleConfiguration.isGiveMoneyServiceActive()) {
			try {
				logger.debug(" Start ScheduleGiveMoneyService");
				List<WechatInvite> wechatInvites = this.store.queryByUnfinishedWechatInvite();
				for (Iterator<WechatInvite> iterator = wechatInvites.iterator(); iterator.hasNext(); ) {
					WechatInvite wechatInvite = iterator.next();
					if (!StringUtils.nonEmptyString(wechatInvite.getWechatinviterId())) {
						iterator.remove();
					}
				}
				HashMap<String, List<WechatInvite>> dataMap = new HashMap<String, List<WechatInvite>>();
				for (WechatInvite wechatInvite : wechatInvites) {
					List<WechatInvite> list = dataMap.get(wechatInvite.getWechatinviterId());
					if (list == null) {
						list = new ArrayList<WechatInvite>();
						dataMap.put(wechatInvite.getWechatinviterId(), list);
					}
					list.add(wechatInvite);
				}
				if (dataMap.isEmpty()) {
					return;
				}
				HashMap<String, String> wechatInviteResult = this.store.queryByUWechatInvitePubKeyMapping(dataMap.keySet());
				if (wechatInviteResult.isEmpty()) {
					return;
				}
				List<ECKey> ecKeys = new ArrayList<ECKey>();
				for (Map.Entry<String, List<WechatInvite>> entry : dataMap.entrySet()) {
					String wechatinviterId = entry.getKey();
					String pubKey = wechatInviteResult.get(wechatinviterId);
					if (!StringUtils.nonEmptyString(pubKey)) {
						continue;
					}
					
				}
				giveMoneyUtils.batchGiveMoneyToECKeyList(ecKeys);
			} catch (Exception e) {
				logger.warn("ScheduleGiveMoneyService", e);
			}
		}
	}
}

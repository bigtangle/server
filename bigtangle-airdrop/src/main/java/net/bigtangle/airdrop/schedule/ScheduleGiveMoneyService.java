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
import net.bigtangle.airdrop.store.FullPrunedBlockStore;
import net.bigtangle.airdrop.utils.GiveMoneyUtils;

@Component
@EnableAsync
public class ScheduleGiveMoneyService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleGiveMoneyService.class);

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
                logger.debug(" Start ScheduleGiveMoneyService");
                List<WechatInvite> wechatInvites = this.store.queryByUnfinishedWechatInvite();
                for (Iterator<WechatInvite> iterator = wechatInvites.iterator(); iterator.hasNext();) {
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
                HashMap<String, String> wechatInviteResult = this.store
                        .queryByUWechatInvitePubKeyMapping(dataMap.keySet());
                if (wechatInviteResult.isEmpty()) {
                    return;
                }
                HashMap<String, Integer> giveMoneyResult = new HashMap<String, Integer>();
                for (Iterator<Map.Entry<String, List<WechatInvite>>> iterator = dataMap.entrySet().iterator(); iterator.hasNext();) {
                	Map.Entry<String, List<WechatInvite>> entry = iterator.next();
                    String wechatinviterId = entry.getKey();
                    String pubkey = wechatInviteResult.get(wechatinviterId);
                    if (!StringUtils.nonEmptyString(pubkey)) {
                    	iterator.remove();
                        continue;
                    }
                    final int count = entry.getValue().size();
                    if (count == 0) {
                    	iterator.remove();
                        continue;
                    }
                    giveMoneyResult.put(pubkey, count);
                }
                
                if (giveMoneyResult.isEmpty()) {
                	return;
                }
                
                giveMoneyUtils.batchGiveMoneyToECKeyList(giveMoneyResult);
                
                for (Map.Entry<String, List<WechatInvite>> entry : dataMap.entrySet()) {
                	for (WechatInvite wechatInvite : entry.getValue()) {
                		this.store.updateWechatInviteStatus(wechatInvite.getId(), 1);
                	}
                } 
                
            } catch (Exception e) {
                logger.warn("ScheduleGiveMoneyService", e);
            }
        }
    }
}

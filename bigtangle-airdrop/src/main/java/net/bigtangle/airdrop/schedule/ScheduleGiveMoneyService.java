/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.airdrop.schedule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                Set<String> subInvitedSet = new HashSet<>();

                for (WechatInvite wechatInvite : wechatInvites) {
                    List<WechatInvite> list = dataMap.get(wechatInvite.getWechatinviterId());
                    if ("LinkedinBigtangle".equals(wechatInvite.getWechatinviterId())) {
                        subInvitedSet.add(wechatInvite.getWechatId());
                    }
                    if (list == null) {
                        list = new ArrayList<WechatInvite>();
                        dataMap.put(wechatInvite.getWechatinviterId(), list);
                    }
                    dataMap.get(wechatInvite.getWechatinviterId()).add(wechatInvite);
                }
                if (dataMap.isEmpty()) {
                    return;
                }
                Map<String, HashMap<String, String>> wechatInviteResult = this.store
                        .queryByUWechatInvitePubKeyInviterIdMap(dataMap.keySet());
                if (wechatInviteResult.isEmpty()) {
                    return;
                }
                HashMap<String, Integer> giveMoneyResult = new HashMap<String, Integer>();
                for (Iterator<Map.Entry<String, List<WechatInvite>>> iterator = dataMap.entrySet().iterator(); iterator
                        .hasNext();) {
                    Map.Entry<String, List<WechatInvite>> entry = iterator.next();
                    String wechatinviterId = entry.getKey();
                    String pubkey = wechatInviteResult.get("pubkey").get(wechatinviterId);
                    if (!StringUtils.nonEmptyString(pubkey)) {
                        iterator.remove();
                        continue;
                    }
                    final int count = entry.getValue().size();
                    if (count == 0) {
                        iterator.remove();
                        continue;
                    }

                    giveMoneyResult.put(pubkey, (count + 1) * 1000);
                }
                if (wechatInviteResult.get("wechatInviterId") != null
                        && !wechatInviteResult.get("wechatInviterId").isEmpty()) {

                    Map<String, HashMap<String, String>> wechatInviteResult1 = this.store
                            .queryByUWechatInvitePubKeyInviterIdMap(wechatInviteResult.get("wechatInviterId").values());
                    if (wechatInviteResult1.get("pubkey") != null && !wechatInviteResult1.get("pubkey").isEmpty())
                        for (String pubkey : wechatInviteResult1.get("pubkey").values()) {
                            if (pubkey == null || pubkey.isEmpty()) {
                                continue;
                            }
                            logger.debug("==============");
                            logger.debug(pubkey);
                            if (giveMoneyResult.containsKey("pubkey")) {
                                giveMoneyResult.put(pubkey, giveMoneyResult.get("pubkey") + 1000 / 5);
                            } else {
                                giveMoneyResult.put(pubkey, 1000 / 5);
                            }

                        }
                    if (wechatInviteResult1.get("wechatInviterId") != null
                            && !wechatInviteResult1.get("wechatInviterId").isEmpty()) {
                        Map<String, HashMap<String, String>> wechatInviteResult2 = this.store
                                .queryByUWechatInvitePubKeyInviterIdMap(
                                        wechatInviteResult1.get("wechatInviterId").values());
                        if (wechatInviteResult2.get("pubkey") != null && !wechatInviteResult2.get("pubkey").isEmpty())
                            for (String pubkey : wechatInviteResult2.get("pubkey").values()) {
                                if (pubkey == null || pubkey.isEmpty()) {
                                    continue;
                                }
                                logger.debug("==============");
                                logger.debug(pubkey);
                                if (giveMoneyResult.containsKey("pubkey")) {
                                    giveMoneyResult.put(pubkey, giveMoneyResult.get("pubkey") + 1000 / 5 / 5);
                                } else {
                                    giveMoneyResult.put(pubkey, 1000 / 5 / 5);
                                }
                            }
                        if (wechatInviteResult2.get("wechatInviterId") != null
                                && !wechatInviteResult2.get("wechatInviterId").isEmpty()) {
                            Map<String, HashMap<String, String>> wechatInviteResult3 = this.store
                                    .queryByUWechatInvitePubKeyInviterIdMap(
                                            wechatInviteResult2.get("wechatInviterId").values());
                            if (wechatInviteResult3.get("pubkey") != null
                                    && !wechatInviteResult3.get("pubkey").isEmpty())
                                for (String pubkey : wechatInviteResult3.get("pubkey").values()) {
                                    if (pubkey == null || pubkey.isEmpty()) {
                                        continue;
                                    }
                                    logger.debug("==============");
                                    logger.debug(pubkey);
                                    if (giveMoneyResult.containsKey("pubkey")) {
                                        giveMoneyResult.put(pubkey, giveMoneyResult.get("pubkey") + 1000 / 5 / 5 / 5);
                                    } else {
                                        giveMoneyResult.put(pubkey, 1000 / 5 / 5 / 5);
                                    }
                                }
                            if (wechatInviteResult3.get("wechatInviterId") != null
                                    && !wechatInviteResult3.get("wechatInviterId").isEmpty()) {
                                Map<String, HashMap<String, String>> wechatInviteResult4 = this.store
                                        .queryByUWechatInvitePubKeyInviterIdMap(
                                                wechatInviteResult3.get("wechatInviterId").values());
                                if (wechatInviteResult4.get("pubkey") != null
                                        && !wechatInviteResult4.get("pubkey").isEmpty())
                                    for (String pubkey : wechatInviteResult4.get("pubkey").values()) {
                                        if (pubkey == null || pubkey.isEmpty()) {
                                            continue;
                                        }
                                        logger.debug("==============");
                                        logger.debug(pubkey);
                                        if (giveMoneyResult.containsKey("pubkey")) {
                                            giveMoneyResult.put(pubkey,
                                                    giveMoneyResult.get("pubkey") + 1000 / 5 / 5 / 5 / 5);
                                        } else {
                                            giveMoneyResult.put(pubkey, 1000 / 5 / 5 / 5 / 5);
                                        }
                                    }

                            }
                        }
                    }
                }
                if (subInvitedSet != null && !subInvitedSet.isEmpty()) {
                    Map<String, HashMap<String, String>> wechatInviteResult5 = this.store
                            .queryByUWechatInvitePubKeyInviterIdMap(subInvitedSet);
                    if (wechatInviteResult5.get("pubkey") != null && !wechatInviteResult5.get("pubkey").isEmpty())
                        for (String pubkey : wechatInviteResult5.get("pubkey").values()) {
                            if (pubkey == null || pubkey.isEmpty()) {
                                continue;
                            }
                            logger.debug("==============");
                            logger.debug(pubkey);
                            if (giveMoneyResult.containsKey("pubkey")) {
                                giveMoneyResult.put(pubkey, giveMoneyResult.get("pubkey") + 100000);
                            } else {
                                giveMoneyResult.put(pubkey, 100000);
                            }
                        }
                }
                if (giveMoneyResult.isEmpty()) {
                    return;
                }
                giveMoneyUtils.batchGiveMoneyToECKeyList(giveMoneyResult);

                for (Map.Entry<String, List<WechatInvite>> entry : dataMap.entrySet()) {
                    logger.info("wechat invite give money : " + entry.getKey() + ", money : "
                            + (entry.getValue().size() * 1000));
                    for (WechatInvite wechatInvite : entry.getValue()) {
                        this.store.updateWechatInviteStatus(wechatInvite.getId(), 1);
                        logger.info("wechat invite update status, id : " + wechatInvite.getId() + ", success");
                    }
                }

            } catch (Exception e) {
                logger.warn("ScheduleGiveMoneyService", e);
            }
        }
    }
}

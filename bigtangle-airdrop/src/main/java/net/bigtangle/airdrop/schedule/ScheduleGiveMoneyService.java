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
import net.bigtangle.core.Address;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.params.MainNetParams;

@Component
@EnableAsync
public class ScheduleGiveMoneyService {

    private static final String LINKEDIN_BIGTANGLE = "LinkedinBigtangle";

    private static final int linkedinMoney = 100000;

    private static final int wechatReward = 1000;
    private static final int wechatRewardfactor = 10;

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

                collectLinkedin(wechatInvites, dataMap, subInvitedSet);
                if (dataMap.isEmpty()) {
                    return;
                }
                Map<String, HashMap<String, String>> wechatInviteResult = this.store
                        .queryByUWechatInvitePubKeyInviterIdMap(dataMap.keySet());
                if (wechatInviteResult.isEmpty()) {
                    return;
                }
                HashMap<String, Integer> giveMoneyResult = new HashMap<String, Integer>();
                wechatToplevel(dataMap, wechatInviteResult, giveMoneyResult);
                wechatRecursive(wechatInviteResult, giveMoneyResult);
                giveMoneyLinkedin(subInvitedSet, giveMoneyResult);

                if (giveMoneyResult.isEmpty()) {
                    return;
                }
                if (giveMoneyUtils.batchGiveMoneyToECKeyList(giveMoneyResult)) {
                    // only update, if money is given
                    for (Map.Entry<String, List<WechatInvite>> entry : dataMap.entrySet()) {
                        logger.info("wechat invite give money : " + entry.getKey() + ", money : "
                                + (entry.getValue().size() * wechatReward));
                        for (WechatInvite wechatInvite : entry.getValue()) {
                            boolean flag = true;
                            try {
                                Address.fromBase58(MainNetParams.get(), wechatInvite.getPubkey());
                            } catch (Exception e) {
                                flag = false;
                            }
                            if (flag) {
                                this.store.updateWechatInviteStatus(wechatInvite.getId(), 1);
                                logger.info("wechat invite update status, id : " + wechatInvite.getId() + ", success");
                            }

                        }
                    }
                    for (String wechatId : subInvitedSet) {
                        this.store.updateWechatInviteStatusByWechatId(wechatId, 1);
                        logger.info("wechat invite update status, wechatId : " + wechatId + ", success");
                    }

                }
            } catch (Exception e) {
                logger.warn("ScheduleGiveMoneyService", e);
            }
        }
    }

    private void wechatToplevel(HashMap<String, List<WechatInvite>> dataMap,
            Map<String, HashMap<String, String>> wechatInviteResult, HashMap<String, Integer> giveMoneyResult) {
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

            giveMoneyResult.put(pubkey, (count + 1) * wechatReward);
        }
    }

    private void collectLinkedin(List<WechatInvite> wechatInvites, HashMap<String, List<WechatInvite>> dataMap,
            Set<String> subInvitedSet) {
        for (WechatInvite wechatInvite : wechatInvites) {
            boolean flag = true;
            try {
                Address.fromBase58(MainNetParams.get(), wechatInvite.getPubkey());
            } catch (Exception e) {
                flag = false;
            }
            if (!flag) {
                continue;
            }
            List<WechatInvite> list = dataMap.get(wechatInvite.getWechatinviterId());

            if (LINKEDIN_BIGTANGLE.equals(wechatInvite.getWechatinviterId())) {

                subInvitedSet.add(wechatInvite.getWechatId());

            }

            if (list == null) {
                list = new ArrayList<WechatInvite>();
                dataMap.put(wechatInvite.getWechatinviterId(), list);
            }
            dataMap.get(wechatInvite.getWechatinviterId()).add(wechatInvite);

        }
    }

    private void wechatRecursive(Map<String, HashMap<String, String>> wechatInviteResult,
            HashMap<String, Integer> giveMoneyResult) throws BlockStoreException {
        if (wechatInviteResult.get("wechatInviterId") != null && !wechatInviteResult.get("wechatInviterId").isEmpty()) {

            Map<String, HashMap<String, String>> wechatInviteResult1 = this.store
                    .queryByUWechatInvitePubKeyInviterIdMap(wechatInviteResult.get("wechatInviterId").values());
            if (wechatInviteResult1.get("pubkey") != null && !wechatInviteResult1.get("pubkey").isEmpty())
                for (String pubkey : wechatInviteResult1.get("pubkey").values()) {
                    if (pubkey == null || pubkey.isEmpty()) {
                        continue;
                    }
                    logger.debug("==============");
                    logger.debug(pubkey);
                    if (giveMoneyResult.containsKey(pubkey)) {
                        giveMoneyResult.put(pubkey, giveMoneyResult.get(pubkey) + wechatReward / wechatRewardfactor);
                    } else {
                        giveMoneyResult.put(pubkey, wechatReward / wechatRewardfactor);
                    }

                }
            if (wechatInviteResult1.get("wechatInviterId") != null
                    && !wechatInviteResult1.get("wechatInviterId").isEmpty()) {
                Map<String, HashMap<String, String>> wechatInviteResult2 = this.store
                        .queryByUWechatInvitePubKeyInviterIdMap(wechatInviteResult1.get("wechatInviterId").values());
                if (wechatInviteResult2.get("pubkey") != null && !wechatInviteResult2.get("pubkey").isEmpty())
                    for (String pubkey : wechatInviteResult2.get("pubkey").values()) {
                        if (pubkey == null || pubkey.isEmpty()) {
                            continue;
                        }
                        logger.debug("==============");
                        logger.debug(pubkey);
                        if (giveMoneyResult.containsKey(pubkey)) {
                            giveMoneyResult.put(pubkey, giveMoneyResult.get(pubkey)
                                    + wechatReward / wechatRewardfactor / wechatRewardfactor);
                        } else {
                            giveMoneyResult.put(pubkey, wechatReward / wechatRewardfactor / wechatRewardfactor);
                        }
                    }
                if (wechatInviteResult2.get("wechatInviterId") != null
                        && !wechatInviteResult2.get("wechatInviterId").isEmpty()) {
                    Map<String, HashMap<String, String>> wechatInviteResult3 = this.store
                            .queryByUWechatInvitePubKeyInviterIdMap(
                                    wechatInviteResult2.get("wechatInviterId").values());
                    if (wechatInviteResult3.get("pubkey") != null && !wechatInviteResult3.get("pubkey").isEmpty())
                        for (String pubkey : wechatInviteResult3.get("pubkey").values()) {
                            if (pubkey == null || pubkey.isEmpty()) {
                                continue;
                            }
                            logger.debug("==============");
                            logger.debug(pubkey);
                            if (giveMoneyResult.containsKey(pubkey)) {
                                giveMoneyResult.put(pubkey, giveMoneyResult.get(pubkey)
                                        + wechatReward / wechatRewardfactor / wechatRewardfactor / wechatRewardfactor);
                            } else {
                                giveMoneyResult.put(pubkey,
                                        wechatReward / wechatRewardfactor / wechatRewardfactor / wechatRewardfactor);
                            }
                        }
                    if (wechatInviteResult3.get("wechatInviterId") != null
                            && !wechatInviteResult3.get("wechatInviterId").isEmpty()) {
                        Map<String, HashMap<String, String>> wechatInviteResult4 = this.store
                                .queryByUWechatInvitePubKeyInviterIdMap(
                                        wechatInviteResult3.get("wechatInviterId").values());
                        if (wechatInviteResult4.get("pubkey") != null && !wechatInviteResult4.get("pubkey").isEmpty())
                            for (String pubkey : wechatInviteResult4.get("pubkey").values()) {
                                if (pubkey == null || pubkey.isEmpty()) {
                                    continue;
                                }
                                logger.debug("==============");
                                logger.debug(pubkey);
                                if (giveMoneyResult.containsKey(pubkey)) {
                                    giveMoneyResult.put(pubkey,
                                            giveMoneyResult.get(pubkey) + wechatReward / wechatRewardfactor
                                                    / wechatRewardfactor / wechatRewardfactor / wechatRewardfactor);
                                } else {
                                    giveMoneyResult.put(pubkey, wechatReward / wechatRewardfactor / wechatRewardfactor
                                            / wechatRewardfactor / wechatRewardfactor);
                                }
                            }

                    }
                }
            }
        }
    }

    private void giveMoneyLinkedin(Set<String> subInvitedSet, HashMap<String, Integer> giveMoneyResult)
            throws BlockStoreException {
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
                        giveMoneyResult.put(pubkey, giveMoneyResult.get("pubkey") + linkedinMoney);
                    } else {
                        giveMoneyResult.put(pubkey, linkedinMoney);
                    }
                }
        }
    }
}

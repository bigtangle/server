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
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.params.MainNetParams;

@Component
@EnableAsync
public class ScheduleRewardService {

	private static final int wechatReward = 1000;
	private static final int wechatRewardfactor = 10;

	private static final Logger logger = LoggerFactory.getLogger(ScheduleRewardService.class);

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
				Set<String> invitedSet = new HashSet<>();

				collectDatamap(wechatInvites, dataMap, invitedSet);
				if (dataMap.isEmpty()) {
					logger.debug(" dataMap isEmpty " + wechatInvites);

					return;
				}
				logger.debug("size:" + dataMap.size());
				Map<String, HashMap<String, String>> wechatInviteResult = this.store
						.queryByUWechatInvitePubKeyInviterIdMap(dataMap.keySet());
				if (wechatInviteResult.isEmpty()) {
					logger.debug(" inviter data   isEmpty " + wechatInvites);
					return;
				}
				HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
				wechatToplevel(dataMap, wechatInviteResult, giveMoneyResult);
				wechatRecursive(wechatInviteResult, giveMoneyResult);

				for (String invitedPubkey : invitedSet) {
					boolean flag = true;
					try {
						Address.fromBase58(MainNetParams.get(), invitedPubkey);
					} catch (Exception e) {
						// logger.debug("", e);
						flag = false;
					}
					if (flag) {
						giveMoneyResult.put(invitedPubkey, wechatReward * 1l);
					}

				}
				// no data for process
				if (giveMoneyResult.isEmpty()) {
					return;
				}

				if (giveMoneyUtils.batchGiveMoneyToECKeyList(giveMoneyResult)) {
					// only update, if money is given
					for (Map.Entry<String, List<WechatInvite>> entry : dataMap.entrySet()) {
						logger.info("wechat invite give money : " + entry.getKey() + ", money : "
								+ (entry.getValue().size() * wechatReward));
						for (WechatInvite wechatInvite : entry.getValue()) {

							this.store.updateWechatInviteStatus(wechatInvite.getId(), 1);
							logger.info("wechat invite update status, id : " + wechatInvite.getId() + ", success");

						}
					}

				}
			} catch (Exception e) {
				logger.warn("ScheduleGiveMoneyService", e);
			}
		}
	}

	private void wechatToplevel(HashMap<String, List<WechatInvite>> dataMap,
			Map<String, HashMap<String, String>> wechatInviteResult, HashMap<String, Long> giveMoneyResult) {
		logger.debug("wechatToplevel with data size " + dataMap.size());
		for (Iterator<Map.Entry<String, List<WechatInvite>>> iterator = dataMap.entrySet().iterator(); iterator
				.hasNext();) {
			Map.Entry<String, List<WechatInvite>> entry = iterator.next();
			String wechatinviterId = entry.getKey();
			// logger.debug("wechatid:" + wechatinviterId);
			String pubkey = wechatInviteResult.get("pubkey").get(wechatinviterId);

			final int count = entry.getValue().size();
			if (count == 0) {
				iterator.remove();
				continue;
			}
			try {
				Address.fromBase58(MainNetParams.get(), pubkey);
				giveMoneyResult.put(pubkey, (count + 1) * wechatReward * 1l);
			} catch (Exception e) {
				// logger.debug("", e);

			}

		}
	}

	private void collectDatamap(List<WechatInvite> wechatInvites, HashMap<String, List<WechatInvite>> dataMap,
			Set<String> invitedSet) {
		for (WechatInvite wechatInvite : wechatInvites) {
			if (wechatInvite.getPubkey() != null && !"".equals(wechatInvite.getPubkey().trim())) {
				try {
					Address.fromBase58(MainNetParams.get(), wechatInvite.getPubkey());
					invitedSet.add(wechatInvite.getPubkey());
				} catch (Exception e) {
					// logger.debug("", e);

				}
			}
			List<WechatInvite> list = dataMap.get(wechatInvite.getWechatinviterId());

			if (list == null) {
				list = new ArrayList<WechatInvite>();
				dataMap.put(wechatInvite.getWechatinviterId(), list);
			}
			dataMap.get(wechatInvite.getWechatinviterId()).add(wechatInvite);

		}
	}

	private void wechatRecursive(Map<String, HashMap<String, String>> wechatInviteResult,
			HashMap<String, Long> giveMoneyResult) throws BlockStoreException {
		if (wechatInviteResult.get("wechatInviterId") != null && !wechatInviteResult.get("wechatInviterId").isEmpty()) {

			Map<String, HashMap<String, String>> wechatInviteResult1 = this.store
					.queryByUWechatInvitePubKeyInviterIdMap(wechatInviteResult.get("wechatInviterId").values());
			if (wechatInviteResult1.get("pubkey") != null && !wechatInviteResult1.get("pubkey").isEmpty())
				for (String pubkey : wechatInviteResult1.get("pubkey").values()) {

					try {
						Address.fromBase58(MainNetParams.get(), pubkey);
						if (giveMoneyResult.containsKey(pubkey)) {
							giveMoneyResult.put(pubkey,
									giveMoneyResult.get(pubkey) + wechatReward / wechatRewardfactor);
						} else {
							giveMoneyResult.put(pubkey, wechatReward * 1l / wechatRewardfactor);
						}
					} catch (Exception e) {
						logger.debug(pubkey);
						logger.debug("", e);

					}

				}
			if (wechatInviteResult1.get("wechatInviterId") != null
					&& !wechatInviteResult1.get("wechatInviterId").isEmpty()) {
				Map<String, HashMap<String, String>> wechatInviteResult2 = this.store
						.queryByUWechatInvitePubKeyInviterIdMap(wechatInviteResult1.get("wechatInviterId").values());
				if (wechatInviteResult2.get("pubkey") != null && !wechatInviteResult2.get("pubkey").isEmpty())
					for (String pubkey : wechatInviteResult2.get("pubkey").values()) {
						if (pubkey == null || pubkey.trim().isEmpty()) {
							continue;
						}
						logger.debug("==============");
						logger.debug(pubkey);
						try {
							Address.fromBase58(MainNetParams.get(), pubkey);
							if (giveMoneyResult.containsKey(pubkey)) {
								giveMoneyResult.put(pubkey, giveMoneyResult.get(pubkey)
										+ wechatReward / wechatRewardfactor / wechatRewardfactor);
							} else {
								giveMoneyResult.put(pubkey,
										wechatReward * 1l / wechatRewardfactor / wechatRewardfactor);
							}
						} catch (Exception e) {
							// logger.debug("", e);

						}

					}
				if (wechatInviteResult2.get("wechatInviterId") != null
						&& !wechatInviteResult2.get("wechatInviterId").isEmpty()) {
					Map<String, HashMap<String, String>> wechatInviteResult3 = this.store
							.queryByUWechatInvitePubKeyInviterIdMap(
									wechatInviteResult2.get("wechatInviterId").values());
					if (wechatInviteResult3.get("pubkey") != null && !wechatInviteResult3.get("pubkey").isEmpty())
						for (String pubkey : wechatInviteResult3.get("pubkey").values()) {
							if (pubkey == null || pubkey.trim().isEmpty()) {
								continue;
							}
							// logger.debug("==============");
							// logger.debug(pubkey);
							try {
								Address.fromBase58(MainNetParams.get(), pubkey);
								if (giveMoneyResult.containsKey(pubkey)) {
									giveMoneyResult.put(pubkey, giveMoneyResult.get(pubkey) + wechatReward
											/ wechatRewardfactor / wechatRewardfactor / wechatRewardfactor);
								} else {
									giveMoneyResult.put(pubkey, wechatReward * 1l / wechatRewardfactor
											/ wechatRewardfactor / wechatRewardfactor);
								}
							} catch (Exception e) {
								logger.debug(pubkey, e);

							}

						}
					if (wechatInviteResult3.get("wechatInviterId") != null
							&& !wechatInviteResult3.get("wechatInviterId").isEmpty()) {
						Map<String, HashMap<String, String>> wechatInviteResult4 = this.store
								.queryByUWechatInvitePubKeyInviterIdMap(
										wechatInviteResult3.get("wechatInviterId").values());
						if (wechatInviteResult4.get("pubkey") != null && !wechatInviteResult4.get("pubkey").isEmpty())
							for (String pubkey : wechatInviteResult4.get("pubkey").values()) {
								if (pubkey == null || pubkey.trim().isEmpty()) {
									continue;
								}
								// logger.debug("==============");
								//
								try {
									Address.fromBase58(MainNetParams.get(), pubkey);
									if (giveMoneyResult.containsKey(pubkey)) {
										giveMoneyResult.put(pubkey,
												giveMoneyResult.get(pubkey) + wechatReward / wechatRewardfactor
														/ wechatRewardfactor / wechatRewardfactor / wechatRewardfactor);
									} else {
										giveMoneyResult.put(pubkey, wechatReward * 1l / wechatRewardfactor
												/ wechatRewardfactor / wechatRewardfactor / wechatRewardfactor);
									}
								} catch (Exception e) {
									// logger.debug(pubkey, e);

								}

							}
					}
				}
			}
		}
	}

}

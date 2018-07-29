package net.bigtangle.tools.action.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.tools.utils.GiveMoneyUtils;
import net.bigtangle.utils.OkHttp3Util;

public class MultiSignTokenAction {

    public MultiSignTokenAction(Account account, ECKey ecKey) {
        this.ecKey = ecKey;
        this.account = account;
    }

    private ECKey ecKey;

    private Account account;

    public void execute() {
        logger.info("account name : {}, eckey : {}, multiSign token action start", account.getName(),
                this.ecKey.getPublicKeyAsHex());
        try {
            Block block = GiveMoneyUtils.createTokenBlock(ecKey);
            OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.multiSign.name(), block.bitcoinSerialize());
        } catch (Exception e) {
            logger.error("account name : {}, eckey : {}, multiSign token action exception", account.getName(),
                    this.ecKey.getPublicKeyAsHex(), e);
        }
        logger.info("account name : {}, eckey : {}, multiSign token action end", account.getName(),
                this.ecKey.getPublicKeyAsHex());
    }

    private static final Logger logger = LoggerFactory.getLogger(MultiSignTokenAction.class);
}

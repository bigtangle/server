package net.bigtangle.tools.action.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.SimpleAction;
import net.bigtangle.tools.utils.GiveMoneyUtils;

public class MultiTokenAction extends SimpleAction {

    public MultiTokenAction(Account account) {
        super(account);
    }

    private static final Logger logger = LoggerFactory.getLogger(MultiTokenAction.class);

    @Override
    public void callback() {
    }

    @Override
    public void execute0() throws Exception {
        ECKey ecKey = this.account.getRandomTradeECKey();
        logger.info("account name : {}, eckey : {}, multi token action start", account.getName(),
                Utils.HEX.encode(ecKey.getPubKey()));
        try {
            for (int i = 0; i < 10; i++) {
                Runnable runnable = this.createRunnable(ecKey);
                this.account.executePool(runnable);
            }
        } finally {
            logger.info("account name : {}, eckey : {}, multi token action end", account.getName(),
                    Utils.HEX.encode(ecKey.getPubKey()));
        }
    }
    
    public Runnable createRunnable(final ECKey ecKey) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    ECKey ecKey = account.getPushKey();
                    int amount = 100000000;
                    GiveMoneyUtils.createTokenMultiSign(ecKey.getPublicKeyAsHex(), account.getSignKey(), amount);
                } catch (Exception e) {
                    logger.error("account name : {}, eckey : {}, single token action exception", account.getName(),
                            Utils.HEX.encode(ecKey.getPubKey()), e);
                }
            }
        };
        return runnable;
    }
}

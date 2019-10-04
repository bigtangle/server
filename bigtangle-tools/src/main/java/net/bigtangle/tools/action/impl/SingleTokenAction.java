package net.bigtangle.tools.action.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.SimpleAction;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.tools.utils.GiveMoneyUtils;
import net.bigtangle.utils.OkHttp3Util;

public class SingleTokenAction extends SimpleAction {

    public SingleTokenAction(Account account) {
        super(account);
    }

    @Override
    public void execute0() {
        ECKey ecKey = this.account.getRandomTradeECKey();
        logger.info("account name : {}, eckey : {}, single token action start", account.getName(),
                Utils.HEX.encode(ecKey.getPubKey()));
        try {
            for (int i = 0; i < 10; i++) {
                Runnable runnable = this.createRunnable(ecKey);
                this.account.executePool(runnable);
            }
        } finally {
            logger.info("account name : {}, eckey : {}, single token action end", account.getName(),
                    Utils.HEX.encode(ecKey.getPubKey()));
        }
    }

    public Runnable createRunnable(final ECKey ecKey) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    Block block = GiveMoneyUtils.createTokenBlock(ecKey);
                    OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.signToken.name(),
                            block.bitcoinSerialize());
                } catch (Exception e) {
                    logger.error("account name : {}, eckey : {}, single token action exception", account.getName(),
                            Utils.HEX.encode(ecKey.getPubKey()), e);
                }
            }
        };
        return runnable;
    }

    private static final Logger logger = LoggerFactory.getLogger(SingleTokenAction.class);

    @Override
    public void callback() {
    }
}

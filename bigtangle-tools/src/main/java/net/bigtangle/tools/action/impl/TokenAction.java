package net.bigtangle.tools.action.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.SimpleAction;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.tools.utils.GiveMoneyUtils;
import net.bigtangle.utils.OkHttp3Util;

public class TokenAction extends SimpleAction {

    public TokenAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    @Override
    public void execute0() throws Exception {
        logger.info("account name : {}, token action start", account.getName());
        try {
            for (ECKey outKey : this.account.walletKeys()) {
                Block block = GiveMoneyUtils.createTokenBlock(outKey);
                OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.multiSign.name(), block.bitcoinSerialize());
            }
        } catch (Exception e) {
            logger.error("account name : {}, token action exception", account.getName(), e);
        }
        logger.info("account name : {}, token action end", account.getName());
    }

    private static final Logger logger = LoggerFactory.getLogger(TokenAction.class);
}

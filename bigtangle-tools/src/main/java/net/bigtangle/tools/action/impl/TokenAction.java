package net.bigtangle.tools.action.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.tools.utils.Simulator;
import net.bigtangle.utils.OkHttp3Util;

public class TokenAction extends Action {

    public TokenAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    @Override
    public void execute0() throws Exception {
        ECKey outKey = this.account.getSellKey();

        Block block = Simulator.createTokenBlock(outKey);
        // save block
        block.solve();
        OkHttp3Util.post(Configure.CONTEXT_ROOT + "multiSign", block.bitcoinSerialize());

        logger.info("account name : {},  action success", account.getName());

    }

    private static final Logger logger = LoggerFactory.getLogger(TokenAction.class);
}

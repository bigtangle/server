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
    }
    
    public Runnable createRunnable(final ECKey ecKey) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                
            }
        };
        return runnable;
    }
}

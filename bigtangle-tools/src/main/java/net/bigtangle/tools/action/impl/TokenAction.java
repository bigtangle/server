package net.bigtangle.tools.action.impl;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Utils;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.UUIDUtil;

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
        byte[] pubKey = outKey.getPubKey();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
        requestParam.put("amount", 999999999999L);
        requestParam.put("tokenname", "Test-" + UUIDUtil.randomUUID());
        requestParam.put("description", "Test-" + UUIDUtil.randomUUID());
        requestParam.put("blocktype", false);
        requestParam.put("tokenHex", Utils.HEX.encode(outKey.getPubKeyHash()));
        OkHttp3Util.post(Configure.CONTEXT_ROOT + "createGenesisBlock", Json.jsonmapper().writeValueAsString(requestParam));
        logger.info("account name : {}, createGenesisBlock action success", account.getName());
        
        
    }
    
    private static final Logger logger = LoggerFactory.getLogger(TokenAction.class);
}

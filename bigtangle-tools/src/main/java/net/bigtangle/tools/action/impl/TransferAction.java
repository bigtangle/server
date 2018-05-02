package net.bigtangle.tools.action.impl;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.tools.container.Container;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.SendRequest;

public class TransferAction extends Action {

    public TransferAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    @Override
    public void execute0() throws Exception {
        final Account account = Container.getInstance().randomTradeAccount();
        if (account == null) {
            return;
        }
        ECKey ecKey = account.getRandomECKey();
        if (ecKey == null) {
            return;
        }
        try {
            HashMap<String, String> requestParams = new HashMap<String, String>();
            byte[] data = OkHttp3Util.post(Configure.CONTEXT_ROOT + "askTransaction", Json.jsonmapper().writeValueAsString(requestParams));
            Block rollingBlock = Configure.PARAMS.getDefaultSerializer().makeBlock(data);
            
            Coin amount = Coin.parseCoin("0.001", NetworkParameters.BIGNETCOIN_TOKENID);
            SendRequest request = SendRequest.to(ecKey.toAddress(Configure.PARAMS), amount);
            account.completeTransaction(request);
//            walletAppKit.wallet().completeTx(request);
            rollingBlock.addTransaction(request.tx);
            rollingBlock.solve();
            // save block
            OkHttp3Util.post(Configure.CONTEXT_ROOT + "saveBlock", rollingBlock.bitcoinSerialize());
            logger.info("account name : {},  Transfer action success", account.getName());
        }
        catch (Exception e) {
            logger.error("account name : {},  Transfer action fail", account.getName(), e);
        }
    }
    
    private static final Logger logger = LoggerFactory.getLogger(SellOrderAction.class);

}

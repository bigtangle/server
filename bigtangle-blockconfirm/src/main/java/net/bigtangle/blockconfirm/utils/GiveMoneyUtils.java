package net.bigtangle.blockconfirm.utils;

import java.util.HashMap;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bigtangle.blockconfirm.config.ServerConfiguration;
import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.wallet.Wallet;

@Component
public class GiveMoneyUtils {

    @PostConstruct
    public void init() {
        String contextRoot = serverConfiguration.getServerURL();
        payWallet = new Wallet(networkParameters, contextRoot);
        payWallet.importKey(
                  ECKey.fromPrivateAndPrecalculatedPublic( Utils.HEX.decode(NetworkParameters.testPriv), Utils.HEX.decode(NetworkParameters.testPub)));
        payWallet.setServerURL(contextRoot);
    }

    private Wallet payWallet;

    @Autowired
    private NetworkParameters networkParameters;

    @Autowired
    private ServerConfiguration serverConfiguration;
 
    public synchronized Block batchGiveMoneyToECKeyList(HashMap<String, Long> giveMoneyResult) throws Exception {
        if (giveMoneyResult.isEmpty()) {
            return null;
        }
        LOGGER.info("  start giveMoneyResult : " + giveMoneyResult + " ");

        ECKey fromkey =  ECKey.fromPrivateAndPrecalculatedPublic( Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));
        return  payWallet.payMoneyToECKeyList(null, giveMoneyResult, fromkey);
 
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GiveMoneyUtils.class);
}
package net.bigtangle.blockconfirm.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
    public static String testPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
    public static String testPriv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";

    @PostConstruct
    public void init() {
        String contextRoot = serverConfiguration.getServerURL();
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode( testPriv),
                Utils.HEX.decode( testPub)));

        payWallet = Wallet.fromKeys(networkParameters, keys);
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

        ECKey fromkey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode( testPub));
        payWallet.importKey(fromkey);
        return payWallet.payMoneyToECKeyList(null, giveMoneyResult, "batchGiveMoneyToECKeyList");

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GiveMoneyUtils.class);
}

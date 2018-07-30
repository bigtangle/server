package net.bigtangle.airdrop.utils;

import java.util.HashMap;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bigtangle.airdrop.config.ServerConfiguration;
import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet;

@Component
public class GiveMoneyUtils {

    @PostConstruct
    @SuppressWarnings("deprecation")
    public void init() {
        String contextRoot = serverConfiguration.getServerURL();
        coinbaseWallet = new Wallet(networkParameters, contextRoot);
        coinbaseWallet.importKey(
                new ECKey(Utils.HEX.decode(NetworkParameters.testPriv), Utils.HEX.decode(NetworkParameters.testPub)));
        coinbaseWallet.setServerURL(contextRoot);
    }

    private Wallet coinbaseWallet;

    @Autowired
    private NetworkParameters networkParameters;

    @Autowired
    private ServerConfiguration serverConfiguration;

    public void batchGiveMoneyToECKeyList(HashMap<String, Integer> giveMoneyResult) throws Exception {
        if (giveMoneyResult.isEmpty()) {
            return;
        }
        String contextRoot = serverConfiguration.getServerURL();

        Transaction transaction = new Transaction(this.networkParameters);
        for (Entry<String, Integer> entry : giveMoneyResult.entrySet()) {
            Coin amount = Coin.valueOf(entry.getValue() * 1000, NetworkParameters.BIGNETCOIN_TOKENID);
            Address address = Address.fromBase58(networkParameters, entry.getKey());
            transaction.addOutput(amount, address);
        }

        SendRequest request = SendRequest.forTx(transaction);
        coinbaseWallet.completeTx(request);

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction,
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
    }
}

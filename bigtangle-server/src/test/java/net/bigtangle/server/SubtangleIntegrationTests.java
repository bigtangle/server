/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.config.SubtangleConfiguration;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SubtangleIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private NetworkParameters networkParameters;

    private static final Logger logger = LoggerFactory.getLogger(SubtangleIntegrationTests.class);
    
    public void createTokenSubtangleId(ECKey ecKey) throws Exception {
        byte[] pubKey = ecKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(Utils.HEX.encode(pubKey), "subtangle", "", "", 1, false, TokenType.subtangle.ordinal(),
                true);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokens.getTokenid(), "", ecKey.getPublicKeyAsHex()));

        Coin basecoin = Coin.valueOf(0L, pubKey);
        long amount = basecoin.getValue();
        tokenInfo.setTokenSerial(new TokenSerial(tokens.getTokenid(), 0, amount));

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
        block.addCoinbaseTransaction(ecKey.getPubKey(), basecoin, tokenInfo);

        Transaction transaction = block.getTransactions().get(0);
        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = ecKey.sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(Utils.HEX.encode(pubKey));
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(ecKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(ecKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

        block.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
    }

    public void giveMoneySubtangleId(ECKey outKey, long amount, byte[] subtangleId) throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));

        UTXO findOutput = null;
        for (UTXO output : testTransactionAndGetBalances(false, genesiskey)) {
            if (Arrays.equals(NetworkParameters.BIGNETCOIN_TOKENID, output.getValue().getTokenid())) {
                findOutput = output;
            }
        }
        assertTrue(findOutput != null);

        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(networkParameters, findOutput, 0);
        Transaction transaction = new Transaction(networkParameters);
        Coin coinbase = Coin.valueOf(amount, NetworkParameters.BIGNETCOIN_TOKENID);
        Address address = outKey.toAddress(this.networkParameters);
        transaction.addOutput(coinbase, address);
        transaction.setSubtangleID(subtangleId);

        TransactionInput input = transaction.addInput(spendableOutput);
        Sha256Hash sighash = transaction.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
                false);

        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction,
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(transaction);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
    }
    
    public Coin getBalanceCoin(ECKey ecKey, byte[] tokenid) throws Exception {
        Coin coinbase = Coin.valueOf(0, tokenid);
        for (UTXO output : testTransactionAndGetBalances(false, ecKey)) {
            if (Arrays.equals(coinbase.getTokenid(), output.getValue().getTokenid())) {
                coinbase = coinbase.add(output.getValue());
            }
        }
        return coinbase;
    }
    
    @Autowired
    private SubtangleConfiguration subtangleConfiguration;

    @Test
    public void testGiveMoney() throws Exception {
        logger.info("subtangleConfiguration.active = " + subtangleConfiguration.isActive());
        assertTrue(subtangleConfiguration.isActive());
        
        ECKey subtangleKey = new ECKey();
        this.createTokenSubtangleId(subtangleKey);
        
        ECKey outKey = new ECKey();
        long amount = 1000;
        this.giveMoneySubtangleId(subtangleKey, amount, outKey.getPubKey());
        
        Coin coinbase = getBalanceCoin(subtangleKey, NetworkParameters.BIGNETCOIN_TOKENID);
        logger.info("coinbase : " + coinbase);
        
        assertTrue(amount == coinbase.getValue());
    }
}

package net.bigtangle.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.spongycastle.util.encoders.Hex;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.UTXOProviderException;
import net.bigtangle.encrypt.ECIESCoder;
import net.bigtangle.kits.WalletAppKit;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ECKeyEncryptTest extends AbstractIntegrationTest {

    private byte[] payload = Hex.decode("1122334455");

    @Test
    public void importECKeyDecrypt() {
        BigInteger privKey = new BigInteger("5e173f6ac3c669587538e7727cf19b782a4f2fda07c1eaa662c593e5e85e3051", 16);
        ECKey ecKey = ECKey.fromPrivate(privKey);
        try {
            byte[] cipher = ECIESCoder.encrypt(ecKey.getPubKeyPoint(), payload);
            byte[] decryptedPayload = ECIESCoder.decrypt(ecKey.getPrivKey(), cipher);
            assertArrayEquals(decryptedPayload, payload);
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    @Test
    public void newECKeyDecrypt() {
        ECKey ecKey = new ECKey();
        try {
            byte[] cipher = ECIESCoder.encrypt(ecKey.getPubKeyPoint(), payload);
            byte[] decryptedPayload = ECIESCoder.decrypt(ecKey.getPrivKey(), cipher);
            assertArrayEquals(decryptedPayload, payload);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private Block payBig(ECKey ecKey, long amount, String memoHex) throws Exception,
            InterruptedException, ExecutionException, BlockStoreException, UTXOProviderException {
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        giveMoneyResult.put(ecKey.toAddress(networkParameters).toString(), amount);

        Block b = walletAppKit.wallet().payMoneyToECKeyListMemoHex(null, giveMoneyResult, memoHex);
        mcmcService.update();
        
        
        return b;
    }

    @Test
    public void transactionBlockEncrypt() throws Exception {
        ECKey testKey = walletKeys.get(0);
        
        List<Block> addedBlocks = new ArrayList<>();
        resetAndMakeTestToken(testKey, addedBlocks);
        
        walletAppKit2 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle4");
        walletAppKit2.wallet().setServerURL(contextRoot);
        wallet2Keys = walletAppKit2.wallet().walletKeys(aesKey);
        
        byte[] cipher = ECIESCoder.encrypt(testKey.getPubKeyPoint(), payload);
        String memoHex = Utils.HEX.encode(cipher);
        
        long amount = 77l;
        Block b = payBig(wallet2Keys.get(0), amount, memoHex);
        String jsonString = b.getTransactions().get(0).getMemo();
        
        MemoInfo memoInfo = MemoInfo.parse(jsonString);
        for (KeyValue keyValue : memoInfo.getKv()) {
            if (keyValue.getKey().equals(MemoInfo.ENCRYPT)) {
                memoHex = keyValue.getValue();
            }
        }
        
        byte[] decryptedPayload = ECIESCoder.decrypt(testKey.getPrivKey(), Utils.HEX.decode(memoHex));
        assertArrayEquals(decryptedPayload, payload);
    }

    @Test
    public void accessTokenSignatureVerify() {
        String message = UUID.randomUUID().toString();
        byte[] buf = message.getBytes();
        Sha256Hash hash = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(buf, 0, buf.length));
        
        ECKey ecKey = new ECKey();
        ECKey.ECDSASignature party1Signature = ecKey.sign(hash);
        byte[] signature = party1Signature.encodeToDER();
        
        boolean success = ecKey.verify(hash.getBytes(), signature);
        assertTrue(success);
    }
    
   
    
}

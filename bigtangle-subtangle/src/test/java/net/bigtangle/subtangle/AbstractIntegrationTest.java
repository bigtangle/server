package net.bigtangle.subtangle; /*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.core.response.GetBlockEvaluationsResponse;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.CacheBlockService;
import net.bigtangle.server.service.MCMCService;
import net.bigtangle.server.service.RewardService;
import net.bigtangle.server.service.StoreService;
import net.bigtangle.server.service.SyncBlockService;
import net.bigtangle.server.service.TipsService;
import net.bigtangle.store.FullBlockGraph;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {})

@TestExecutionListeners(value = { DependencyInjectionTestExecutionListener.class, MockitoTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class

})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    private static final String CONTEXT_ROOT_TEMPLATE = "http://localhost:%s/";
    protected static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
    public String contextRoot;
    public List<ECKey> walletKeys;
    public List<ECKey> wallet1Keys;
    public List<ECKey> wallet2Keys;

    public WalletAppKit walletAppKit;
    public WalletAppKit walletAppKit1;
    public WalletAppKit walletAppKit2;

    protected final KeyParameter aesKey = null;

    private HashMap<String, ECKey> walletKeyData = new HashMap<String, ECKey>();

    @Autowired
    protected FullBlockGraph blockGraph;
    @Autowired
    protected BlockService blockService;
    @Autowired
    protected MCMCService mcmcService;
    @Autowired
    protected RewardService rewardService;

    @Autowired
    protected NetworkParameters networkParameters;

    @Autowired
    protected StoreService storeService;

    @Autowired
    protected TipsService tipsService;
    @Autowired
    protected SyncBlockService syncBlockService;

    @Autowired
    protected ServerConfiguration serverConfiguration;
    
    @Autowired
    protected void prepareContextRoot(@Value("${local.server.port}") int port) {
        contextRoot = String.format(CONTEXT_ROOT_TEMPLATE, port);
    }

    protected static ECKey outKey = new ECKey();
    protected static ECKey outKey2 = new ECKey();
    public static String testPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
    public static String testPriv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";
    public static String yuanTokenPub = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a";
    public static String yuanTokenPriv = "8db6bd17fa4a827619e165bfd4b0f551705ef2d549a799e7f07115e5c3abad55";

    protected static ObjectMapper objectMapper = new ObjectMapper();
    public FullBlockStore store;

 
    protected Block addFixedBlocks(int num, Block startBlock, List<Block> blocksAddedAll) throws BlockStoreException {
        // add more blocks follow this startBlock
        Block rollingBlock1 = startBlock;
        for (int i = 0; i < num; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true, store);
            blocksAddedAll.add(rollingBlock1);
        }
        return rollingBlock1;
    }

    public void checkTokenAssertTrue(String tokenid, String domainname) throws Exception {
        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("tokenid", tokenid);
       byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        Token token_ = getTokensResponse.getTokens().get(0);
        assertTrue(token_.getDomainName().equals(domainname));
    }

    public void initWalletKeysMapper() throws Exception {
 
        List<ECKey> tmpList = new ArrayList<ECKey>();
        tmpList.addAll(this.walletKeys);
        tmpList.addAll(this.wallet1Keys);
        tmpList.addAll(this.wallet2Keys);
        for (Iterator<ECKey> iterator = tmpList.iterator(); iterator.hasNext();) {
            ECKey outKey = iterator.next();
            walletKeyData.put(outKey.getPublicKeyAsHex(), outKey);
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        Utils.unsetMockClock();
        store = storeService.getStore();
        resetStore(); 
 
        this.initWalletKeysMapper();

    }
	/**
	 * Resets the store by deleting the contents of the tables and reinitialising
	 * them.
	 * 
	 * @throws BlockStoreException If the tables couldn't be cleared and
	 *                             initialised.
	 */
	public void resetStore() throws BlockStoreException {
	 
		store.resetStore();
		CacheBlockService.lastConfirmedChainBlock=null;
		 
	}

    @AfterEach
    public void close() throws Exception {
        store.close();
    }

  
    // get balance for the walletKeys
    protected List<UTXO> getBalance(boolean withZero, List<ECKey> keys) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : keys) {
            // keyStrHex000.add(ecKey.toAddress(networkParameters).toString());
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }
       byte[] response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

        //byte[] response = mvcResult.getResponse().getContentAsString();
        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            if (withZero) {
                listUTXO.add(utxo);
            } else if (utxo.getValue().getValue().signum() > 0) {
                listUTXO.add(utxo);
            }
        }

        return listUTXO;
    }

    // get balance for the walletKeys
    protected List<UTXO> getBalance(String address) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();

        keyStrHex000.add(Utils.HEX.encode(Address.fromBase58(networkParameters, address).getHash160()));
       byte[] response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            listUTXO.add(utxo);
        }

        return listUTXO;
    }

    protected List<UTXO> getBalance(boolean withZero, ECKey ecKey) throws Exception {
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(ecKey);
        return getBalance(withZero, keys);
    }

 

    // transfer the coin from protected testPub to address in wallet

    protected void testInitTransferWallet() throws Exception {
        ECKey fromkey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, BigInteger> giveMoneyResult = new HashMap<>();
        giveMoneyResult.put(walletKeys.get(1).toAddress(networkParameters).toString(),
                MonetaryFormat.FIAT.noCode().parse("33333").getValue() );
        walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, "testInitTransferWallet");
    }

   
    protected void checkResponse(byte[]  resp) throws JsonParseException, JsonMappingException, IOException {
        checkResponse(resp, 0);
    }

    protected void checkResponse(byte[]  resp, int code) throws JsonParseException, JsonMappingException, IOException {
        @SuppressWarnings("unchecked")
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int error = (Integer) result2.get("errorcode");
        assertTrue(error == code);
    }

    protected void checkBalance(Coin coin, ECKey ecKey) throws Exception {
        ArrayList<ECKey> a = new ArrayList<ECKey>();
        a.add(ecKey);
        checkBalance(coin, a);
    }

    protected void checkBalance(Coin coin, List<ECKey> a) throws Exception {
        List<UTXO> ulist = getBalance(false, a);
        UTXO myutxo = null;
        for (UTXO u : ulist) {
            if (coin.getTokenHex().equals(u.getTokenId()) && coin.getValue().equals(u.getValue().getValue())) {
                myutxo = u;
                break;
            }
        }
        assertTrue(myutxo != null);
        assertTrue(myutxo.getAddress() != null && !myutxo.getAddress().isEmpty());
        log.debug(myutxo.toString());
    }

    protected void checkBalanceSum(Coin coin, List<ECKey> a) throws Exception {
        List<UTXO> ulist = getBalance(false, a);

        Coin sum = new Coin(0, coin.getTokenid());
        for (UTXO u : ulist) {
            if (coin.getTokenHex().equals(u.getTokenId())) {
                sum = sum.add(u.getValue());

            }
        }
        if (coin.getValue().compareTo(sum.getValue()) != 0) {
            log.error(" expected: " + coin + " got: " + sum);
        }
        assertTrue(coin.getValue().compareTo(sum.getValue()) == 0);

    }

 
 
 

 
    
    private List<BlockEvaluationDisplay> getBlockInfos() throws Exception {

        String lastestAmount = "200";
        Map<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("lastestAmount", lastestAmount);
       byte[] response = OkHttp3Util.postString(contextRoot + "/" + ReqCmd.findBlockEvaluation.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
        return getBlockEvaluationsResponse.getEvaluations();
    }

    public Block createToken(ECKey key, String tokename, int decimals, String domainname, String description,
            BigInteger amount, boolean increment, TokenKeyValues tokenKeyValues, int tokentype, String tokenid,
            Wallet w) throws Exception {

        Token token = Token.buildSimpleTokenInfo(true, Sha256Hash.ZERO_HASH, tokenid, tokename, description, 1, 0,
                amount, !increment, decimals, "");
        token.setTokenKeyValues(tokenKeyValues);
        token.setTokentype(tokentype);
        List<MultiSignAddress> addresses = new ArrayList<MultiSignAddress>();
        addresses.add(new MultiSignAddress(tokenid, "", key.getPublicKeyAsHex()));
        return w.createToken(key, domainname, increment, token, addresses);

    }

    public Block createToken(ECKey key, String tokename, int decimals, String domainname, String description,
            BigInteger amount, boolean increment, TokenKeyValues tokenKeyValues, int tokentype, String tokenid,
            Wallet w, byte[] pubkeyTo, MemoInfo memoInfo) throws Exception {

        Token token = Token.buildSimpleTokenInfo(true, Sha256Hash.ZERO_HASH, tokenid, tokename, description, 1, 0,
                amount, !increment, decimals, "");
        token.setTokenKeyValues(tokenKeyValues);
        token.setTokentype(tokentype);
        List<MultiSignAddress> addresses = new ArrayList<MultiSignAddress>();
        addresses.add(new MultiSignAddress(tokenid, "", key.getPublicKeyAsHex()));
        return w.createToken(key, domainname, increment, token, addresses, pubkeyTo, memoInfo);

    }

    public void mcmcServiceUpdate() throws InterruptedException, ExecutionException, BlockStoreException {
        mcmcService.update(store);
//        blockGraph.updateConfirmed();
    }

 
}

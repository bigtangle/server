package net.bigtangle.server.performance;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.apps.lottery.Lottery;
import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.params.TestParams;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class LotteryRemoteTest {

	private NetworkParameters networkParameters =TestParams.get();

	// 1GDsvV5Vgwa7VYULyDmW9Unj9v1hWFxBJ5
	public static String USDTokenPub = "02fbef0f3e1344f548abb7d4b6a799e372d2310ff13fe023b3ba0446f2e3f58e04";
	public static String USDTokenPriv = "6fb4cb30d593536e3c54ac17bfaa311cb0e2bdf219c89483aa8d7185f6c5c3b7";

	
    // 1GZH9mf9w9K3nc58xR3dpTJUuJdiLnuwdW
    public static String ETHTokenPub = "02b8b21c6341872dda1f3a4f26d0d887283ad99f342d1dc35552db39c830919722";
    public static String ETHTokenPriv = "9eac170431a4c8cb188610cea2d40a3db5f656db5b52c0ac5aa9aa3a3fa8366f";

    
	int usernumber =88;
	BigInteger winnerAmount = new BigInteger(  99 + "");
	Wallet wallet;
	public static String contextRoot = "https://test.bigtangle.info:8089/";
	private ECKey accountKey;

	protected static final Logger log = LoggerFactory.getLogger(LotteryRemoteTest.class);

	@Test
	public void lottery() throws Exception {

	//	proxy();

		for (int i = 0; i < 1; i++) {
			usernumber =10;
			winnerAmount = new BigInteger(99 + "");

			lotteryDo();
			log.debug("done iteration " + i + "usernumber=" + usernumber + " winnerAmount=" + winnerAmount);
		}
	}

	
	//@Test
	public void paylist() throws Exception {

		proxy();

	Wallet	w = Wallet.fromKeys(networkParameters, 
			 ECKey.fromPrivate(Utils.HEX.decode(ETHTokenPriv))
			 ); 
		 w.setServerURL(contextRoot);
		 
	 w.payFromList(null, "1GDsvV5Vgwa7VYULyDmW9Unj9v1hWFxBJ5", Coin.valueOf(1234,USDTokenPub ), "test paylist");	
	 
	}

	
	private void proxy() {
		System.setProperty("https.proxyHost", "anwproxy.anwendungen.localnet.de");
		System.setProperty("https.proxyPort", "3128");
	}

	public void lotteryDo() throws Exception {
		wallet = Wallet.fromKeys(networkParameters, ECKey.fromPrivate(Utils.HEX.decode(USDTokenPriv)));
		accountKey = ECKey.fromPrivate(Utils.HEX.decode(ETHTokenPriv));
		wallet.importKey(accountKey);
		wallet.setServerURL(contextRoot);
	 
		// testTokens();
		createUserPay(accountKey);

		Lottery startLottery = startLottery();
		while (!startLottery.isMacthed()) {
			createUserPay(accountKey);
			startLottery = startLottery();
		}
		checkResult(startLottery);
	}

	private Lottery startLottery()
			throws Exception, JsonProcessingException, InterruptedException, ExecutionException, BlockStoreException {
		Lottery startLottery = new Lottery();
		startLottery.setTokenid(USDTokenPub);
		startLottery.setCONTEXT_ROOT(contextRoot);
		startLottery.setParams(networkParameters);
		startLottery.setWalletAdmin(wallet);
		startLottery.setWinnerAmount(winnerAmount);
		startLottery.setAccountKey(accountKey);
		startLottery.start();

		return startLottery;
	}

	private void checkResult(Lottery startLottery) throws Exception {
        sendEmpty(5);
		Coin coin = new Coin(startLottery.sum(), USDTokenPub);

		List<UTXO> users = getBalance(startLottery.getWinner());
		  Coin sum = Coin.valueOf(0, Utils.HEX.decode(USDTokenPub));

		  for (UTXO u : users) {
	            if (coin.getTokenHex().equals(u.getTokenId())
	                    && u.getFromaddress().equals(accountKey.toAddress(networkParameters).toBase58())) {
	                sum = sum.add(u.getValue());

	            }
	        }

	        assertTrue(sum != null);
	        if(startLottery.getWinnerAmount().compareTo(sum.getValue()) > 0) 
	        {
	            log.debug(sum.toString());
	        }
	        assertTrue(" "+ startLottery.getWinnerAmount()+ " sum="+sum.getValue(),
	                startLottery.getWinnerAmount().compareTo(sum.getValue()) <= 0);
	 	}

	// get balance for the walletKeys
	protected List<UTXO> getBalance(String address) throws Exception {
		List<UTXO> listUTXO = new ArrayList<UTXO>();
		List<String> keyStrHex000 = new ArrayList<String>();

		keyStrHex000.add(Utils.HEX.encode(Address.fromBase58(networkParameters, address).getHash160()));
		String response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
				Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

		GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

		for (UTXO utxo : getBalancesResponse.getOutputs()) {
			listUTXO.add(utxo);
		}

		return listUTXO;
	}

	private void createUserPay(ECKey accountKey) throws Exception {
		List<ECKey> ulist = payKeys();
		for (ECKey key : ulist) {
			buyTicket(key, accountKey);
		}
	}

    public void sendEmpty(int c) throws JsonProcessingException, Exception {

        for (int i = 0; i < c; i++) {
            try {
                send();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
    }

    public void send() throws JsonProcessingException, Exception {

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

    }
	/*
	 * pay money to the key and use the key to buy lottery
	 */
	public void buyTicket(ECKey key, ECKey accountKey) throws Exception {
		Wallet w = Wallet.fromKeys(networkParameters, key);
		w.setServerURL(contextRoot);
		try {
			int satoshis = 10;
			w.pay(null, accountKey.toAddress(networkParameters), Coin.valueOf(satoshis, Utils.HEX.decode(USDTokenPub)),
					" buy ticket");
		} catch (Exception e) {
			// TODO: handle exception
		}
	}

	public List<ECKey> payKeys() throws Exception {
		List<ECKey> userkeys = new ArrayList<ECKey>();
		HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();

		for (int i = 0; i < usernumber; i++) {
			ECKey key = new ECKey();
			giveMoneyResult.put(key.toAddress(networkParameters).toString(), winnerAmount.longValue() );
			userkeys.add(key);
		}

		Block b = wallet.payMoneyToECKeyList(null, giveMoneyResult, Utils.HEX.decode(USDTokenPub), " pay to user ", 3, 20000);
		log.debug("block " + (b == null ? "block is null" : b.toString()));
	
		return userkeys;
	}

}

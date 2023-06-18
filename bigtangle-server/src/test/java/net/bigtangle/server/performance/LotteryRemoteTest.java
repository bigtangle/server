package net.bigtangle.server.performance;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Utils;
import net.bigtangle.wallet.Wallet;

public class LotteryRemoteTest extends LotteryTests {

	protected static final Logger log = LoggerFactory.getLogger(LotteryRemoteTest.class);
	ECKey contractKey;

	public void lotteryM() throws Exception {

		usernumber = 10;
		winnerAmount = new BigInteger(usernumber * 100 + "");
		contractKey = new ECKey();
		lotteryDo();

	}

	public void lotteryDo() throws Exception {
		wallet.importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
		testTokens();
		testContractTokens();
		List<ECKey> ulist = createUserkey();
		payUserKeys(ulist);
		payBigUserKeys(ulist);
		// createUserPay(accountKey, ulist);
		payContract(ulist);
		makeRewardBlock();
		Block result = contractExecutionService.createContractExecution(store, contractKey.getPublicKeyAsHex());
	    assertTrue(result!=null);
	     
		makeRewardBlock();

	}

	public void testContractTokens() throws JsonProcessingException, Exception {

		String domain = "";

		TokenKeyValues tokenKeyValues = new TokenKeyValues();
		KeyValue kv = new KeyValue();
		kv.setKey("system");
		kv.setValue("java");
		tokenKeyValues.addKeyvalue(kv);
		kv = new KeyValue();
		kv.setKey("classname");
		kv.setValue("net.bigtangle.server.service.ServiceContract");
		tokenKeyValues.addKeyvalue(kv);
		kv = new KeyValue();
		kv.setKey("winnerAmount");
		kv.setValue("10000");
		tokenKeyValues.addKeyvalue(kv);
		kv = new KeyValue();
		kv.setKey("amount");
		kv.setValue("50");
		tokenKeyValues.addKeyvalue(kv);
		kv = new KeyValue();
		kv.setKey("token");
		kv.setValue(yuanTokenPub);
		tokenKeyValues.addKeyvalue(kv);

		createToken(contractKey, "contractlottery", 0, domain, "contractlottery", BigInteger.valueOf(1), true,
				tokenKeyValues, TokenType.contract.ordinal(), contractKey.getPublicKeyAsHex(), wallet);

		ECKey signkey = ECKey.fromPrivate(Utils.HEX.decode(testPriv));

		wallet.multiSign(contractKey.getPublicKeyAsHex(), signkey, null);

		makeRewardBlock();
	}

	/*
	 * pay money to the contract
	 */
	public void payContract(List<ECKey> userkeys) throws Exception {
		for (ECKey key : userkeys) {
			Wallet w = Wallet.fromKeys(networkParameters, key, contextRoot);
			Block block = w.payContract(null, yuanTokenPub,  winnerAmount , null, null,
					contractKey.getPublicKeyAsHex());
		}
	}

}

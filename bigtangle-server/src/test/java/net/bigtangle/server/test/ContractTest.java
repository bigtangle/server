package net.bigtangle.server.test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Utils;

 
public class ContractTest extends AbstractIntegrationTest {
 
 

     @Test
    public void payContract() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = new ECKey();
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        testContractTokens(testKey );
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open buy order for test tokens
        makeAndConfirmPayContract(genesisKey,  NetworkParameters.BIGTANGLE_TOKENID_STRING , new BigInteger("8888"), testTokenId, addedBlocks);
       
 

    }
 
 	public void testContractTokens(ECKey testKey ) throws JsonProcessingException, Exception {

		String domain = "";

		TokenKeyValues tokenKeyValues = new TokenKeyValues();
		KeyValue kv = new KeyValue();
		kv.setKey("system");
		kv.setValue("java");
		tokenKeyValues.addKeyvalue(kv);
		kv = new KeyValue();
		kv.setKey("classname");
		kv.setValue("net.bigtangle.server.service.LotteryContract");
		tokenKeyValues.addKeyvalue(kv); 
		tokenKeyValues.addKeyvalue(kv);
		kv = new KeyValue();
		kv.setKey("amount");
		kv.setValue("8888");
		tokenKeyValues.addKeyvalue(kv);
		kv = new KeyValue();
		kv.setKey("token");
		kv.setValue(NetworkParameters.BIGTANGLE_TOKENID_STRING);
		tokenKeyValues.addKeyvalue(kv);

		createToken(testKey, "contractlottery", 0, domain, "contractlottery", BigInteger.valueOf(1), false,
				tokenKeyValues, TokenType.contract.ordinal(), testKey.getPublicKeyAsHex(), wallet);

		ECKey signkey = ECKey.fromPrivate(Utils.HEX.decode(testPriv));

		wallet.multiSign(testKey.getPublicKeyAsHex(), signkey, null);

		makeRewardBlock();
	}


}

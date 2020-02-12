package net.bigtangle.server;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ContractTest extends AbstractIntegrationTest {
 
 

    @Test
    public void payContract() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open buy order for test tokens
        makeAndConfirmPayContract(genesisKey,  NetworkParameters.BIGTANGLE_TOKENID_STRING , new BigInteger("1000"), testTokenId, addedBlocks);
       


        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }
 
 

}

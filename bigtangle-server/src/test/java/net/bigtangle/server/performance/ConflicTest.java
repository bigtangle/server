package net.bigtangle.server.performance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.GetOutputsResponse;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.AbstractIntegrationTest;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

 
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ConflicTest extends AbstractIntegrationTest {


    @Test
    public void testPayConflict() throws Exception {
        
        mcmcService.update();
        
        // Generate two conflicting blocks
        ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        List<UTXO> outputs = getBalance(false, testKey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount, new ECKey()));
        TransactionInput input = doublespendTX.addInput(outputs.get(0).getBlockHash(), spendableOutput);
        Sha256Hash sighash = doublespendTX.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create blocks with conflict
        Block b1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), doublespendTX);
        Block b2 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), doublespendTX);

        blockGraph.add(b1, true,store);
        blockGraph.add(b2, true,store);
        sendEmpty(3);
        //add blocks and want to get fast resolve of double spending
        mcmcService.update();
         
        BlockEvaluation b1e = blockService.getBlockEvaluation(b1.getHash(),store);
        
        BlockEvaluation blockEvaluation = blockService.getBlockEvaluation(b2.getHash(),store);
        log.debug(b1e.toString());
        log.debug(blockEvaluation.toString());
        assertFalse(b1e.isConfirmed()
                && blockEvaluation.isConfirmed());
        assertTrue(b1e.isConfirmed()
                || blockEvaluation.isConfirmed());

        mcmcService.update();
        //
        assertFalse(b1e.isConfirmed()
                && blockEvaluation.isConfirmed());
        assertTrue(b1e.isConfirmed()
                || blockEvaluation.isConfirmed());
    }


  
}

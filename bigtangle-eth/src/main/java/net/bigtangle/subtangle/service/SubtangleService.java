package net.bigtangle.subtangle.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
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
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.OutputService;
import net.bigtangle.subtangle.SubtangleConfiguration;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.Wallet;

@Service
public class SubtangleService {

    @SuppressWarnings("deprecation")
    public void giveMoneyToTargetAccount() throws Exception {
        ECKey signKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(subtangleConfiguration.getPriKeyHex0()),
                Utils.HEX.decode(subtangleConfiguration.getPubKeyHex0()));
        List<ECKey> keys = new ArrayList<>();
        keys.add(signKey);

        List<UTXO> outputs = this.getRemoteBalances(false, keys);
        if (outputs.isEmpty()) {
            return;
        }

        for (UTXO output : outputs) {
            try {
                String blockHashHex = output.getBlockHashHex();
                Block block = this.getRemoteBlock(blockHashHex);
                byte[] toAddressInSubtangle = block.getTransactions().get(0).getToAddressInSubtangle();
                if (toAddressInSubtangle == null) {
                    continue;
                }
                Coin coinbase = output.getValue();

                Block b = blockService.getBlockPrototype();
                b.setBlockType(Block.Type.BLOCKTYPE_CROSSTANGLE);
                b.addCoinbaseTransaction(signKey.getPubKey(), coinbase);
                blockService.saveBlock(b);

                Address address = new Address(this.networkParameters, toAddressInSubtangle);
                this.giveMoney(signKey, address, coinbase);

                this.giveRemoteMoney(signKey, coinbase, output);
            } catch (Exception e) {
                // e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void giveRemoteMoney(ECKey signKey, Coin amount, UTXO output) throws Exception {
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(networkParameters, output);
        Transaction transaction = new Transaction(networkParameters);

        ECKey outKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(subtangleConfiguration.getPriKeyHex1()),
                Utils.HEX.decode(subtangleConfiguration.getPubKeyHex1()));
        transaction.addOutput(amount, outKey);

        TransactionInput input = transaction.addInput(output.getBlockHash(), spendableOutput);
        Sha256Hash sighash = transaction.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
                false);

        TransactionSignature tsrecsig = new TransactionSignature(signKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);

        Block b = blockService.getBlockPrototype();
        b.addTransaction(transaction);
        b.solve();
        this.blockService.saveBlock(b);
    }

    public void giveMoney(ECKey signKey, Address address, Coin amount) throws Exception {
        Wallet wallet = new Wallet(networkParameters);
        wallet.setServerURL(subtangleConfiguration.getParentContextRoot());

        List<UTXO> utxolist = getBalancesUTOXList(false, signKey, amount.getTokenid()).stream()
                .filter(out -> Utils.HEX.encode(out.getValue().getTokenid())
                        .equals(Utils.HEX.encode(NetworkParameters.BIGTANGLE_TOKENID)))
                .filter(out -> out.getValue().getValue().compareTo( amount.getValue())>0).collect(Collectors.toList());

        if (utxolist.isEmpty()) {
            return;
        }
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(networkParameters, utxolist.get(0));
        Transaction transaction = new Transaction(networkParameters);
        transaction.addOutput(amount, address);
        transaction.addOutput(spendableOutput.getValue().subtract(amount), signKey);
        wallet.signTransaction(transaction, null);

        Block b = blockService.getBlockPrototype();
        b.addTransaction(transaction);
        b.solve();
        this.blockService.saveBlock(b);
    }

    private List<UTXO> getBalancesUTOXList(boolean withZero, ECKey signKey, byte[] tokenid) throws BlockStoreException {
        Set<byte[]> pubKeyHashs = new HashSet<byte[]>();
        pubKeyHashs.add(signKey.toAddress(this.networkParameters).getHash160());
        GetBalancesResponse getBalancesResponse = (GetBalancesResponse) walletService
                .getAccountBalanceInfo(pubKeyHashs);
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            if (withZero) {
                listUTXO.add(utxo);
            } else if (utxo.getValue().getValue().signum() > 0) {
                listUTXO.add(utxo);
            }
        }
        for (Iterator<UTXO> iterator = listUTXO.iterator(); iterator.hasNext();) {
            UTXO utxo = iterator.next();
            if (!Arrays.equals(utxo.getValue().getTokenid(), tokenid)) {
                iterator.remove();
            }
        }
        return listUTXO;
    }

    @Autowired
    private OutputService walletService;

    @Autowired
    private BlockService blockService;

  

    public List<UTXO> getRemoteBalances(boolean withZero, List<ECKey> keys) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : keys) {
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }

        String contextRoot = subtangleConfiguration.getParentContextRoot();
        String response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            if (withZero) {
                listUTXO.add(utxo);
            } else if (utxo.getValue().getValue() .signum()> 0) {
                listUTXO.add(utxo);
            }
        }

        return listUTXO;
    }

    public Block getRemoteBlock(String blockHashHex) throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("hashHex", blockHashHex);
        String contextRoot = subtangleConfiguration.getParentContextRoot();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getBlockByHash.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        return block;
    }

    @Autowired
    private NetworkParameters networkParameters;

    @Autowired
    private SubtangleConfiguration subtangleConfiguration;
}

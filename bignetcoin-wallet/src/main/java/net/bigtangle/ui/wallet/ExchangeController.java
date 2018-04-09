/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.ui.wallet.utils.FileUtil;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.MapToBeanMapperUtil;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet.MissingSigsMode;

import org.spongycastle.crypto.params.KeyParameter;

public class ExchangeController {
    
    @FXML
    public TextField toAmountTextField;
    
    @FXML
    public ComboBox<String> toTokenHexComboBox;
    
    @FXML
    public ComboBox<String> toAddressComboBox;
    
    @FXML
    public TextField fromAmountTextField;

    @FXML
    public ComboBox<String> fromTokenHexComboBox;
    
    @FXML
    public ComboBox<String> fromAddressComboBox;

    public Main.OverlayUI<?> overlayUI;
    
    private Transaction mTransaction;

    @FXML
    public void initialize() {
        try {
            initComboBox();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
        mTransaction = null;
    }

    @SuppressWarnings("unchecked")
    public void initComboBox() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        ECKey ecKey = Main.bitcoin.wallet().currentReceiveKey();
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokens", ecKey.getPubKeyHash());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> tokens = (List<Map<String, Object>>) data.get("tokens");
        for (Map<String, Object> map : tokens) {
            String tokenHex = (String) map.get("tokenHex");
            String tokenname = (String) map.get("tokenname");
            tokenData.add(tokenname+" : "+tokenHex);
        }
        toTokenHexComboBox.setItems(tokenData);
        fromTokenHexComboBox.setItems(tokenData);

        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(null);
        ObservableList<String> addresses = FXCollections.observableArrayList();
        for (ECKey key : keys) {
            addresses.add(key.toAddress(Main.params).toString());
        }
        fromAddressComboBox.setItems(addresses);
    }

    public void exchangeCoin(ActionEvent event) throws Exception {
        if (mTransaction == null) {
            GuiUtils.informationalAlert("alert", "Transaction Is Empty");
            return;
        }
        SendRequest request = SendRequest.forTx(mTransaction); 
        Main.bitcoin.wallet().signTransaction(request);
         
        String ContextRoot = "http://" + Main.IpAddress + ":" + Main.port + "/";
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(ContextRoot + "askTransaction", Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = Main.params.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(mTransaction);
        rollingBlock.solve();
        OkHttp3Util.post(ContextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        overlayUI.done();
    }

    public void importBlock(ActionEvent event) {
        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        byte[] buf = FileUtil.readFile(file);
        if (buf == null) {
            return;
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf);
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            fromAddressComboBox.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            fromTokenHexComboBox.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            fromAmountTextField.setText(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            toAddressComboBox.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            toTokenHexComboBox.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            toAmountTextField.setText(new String(dst));
        }
        byte[] data = new byte[byteBuffer.getInt()];
        byteBuffer.put(data);
        try {
            mTransaction = (Transaction) Main.params.getDefaultSerializer().makeTransaction(data);
            if (mTransaction == null) {
                GuiUtils.informationalAlert("alert", "Transaction Is Empty");
            }
        }
        catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
        overlayUI.done();
    }

    @SuppressWarnings("deprecation")
    public void exportBlock(ActionEvent event) {
        String ContextRoot = "http://" + Main.IpAddress + ":" + Main.port + "/";
        String fromAddress = fromAddressComboBox.getValue();
        String fromTokenHex = fromTokenHexComboBox.getValue();
        String fromAmount = fromAmountTextField.getText();
        String toAddress = toAddressComboBox.getValue();
        String toTokenHex = toTokenHexComboBox.getValue();
        String toAmount = toAmountTextField.getText();
        byte[] buf = null;
        KeyParameter aesKey = null;
        try {
            List<UTXO> outputs = new ArrayList<UTXO>();

            Address fromAddress00 = new Address(Main.params, fromAddress);
            Address toAddress00 = new Address(Main.params, toAddress);
            outputs.addAll(this.getUTXOWithPubKeyHash(toAddress00.getHash160(), Utils.HEX.decode(toTokenHex)));
            outputs.addAll(this.getUTXOWithECKeyList(Main.bitcoin.wallet().walletKeys(aesKey), Utils.HEX.decode(fromTokenHex)));
            
            Coin amountCoin0 = Coin.parseCoin(toAmount, Utils.HEX.decode(toTokenHex));
            Coin amountCoin1 = Coin.parseCoin(fromAmount, Utils.HEX.decode(fromTokenHex));
            SendRequest req = SendRequest.to(fromAddress00, amountCoin0);
            req.tx.addOutput(amountCoin1, toAddress00);
            req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;

            List<TransactionOutput> candidates = Main.bitcoin.wallet().transforSpendCandidates(outputs);
            Main.bitcoin.wallet().setServerURL(ContextRoot);
            Main.bitcoin.wallet().completeTx(req, candidates, false);
            Main.bitcoin.wallet().signTransaction(req);
            
            this.mTransaction = req.tx;
            buf = mTransaction.bitcoinSerialize();
        }
        catch (Exception e) {
            GuiUtils.crashAlert(e);
            return;
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(buf.length  + 4 +
                fromAddress.getBytes().length                   + 4 + 
                fromTokenHex.getBytes().length                  + 4 + 
                fromAmount.getBytes().length                    + 4 + 
                toAddress.getBytes().length                     + 4 + 
                toTokenHex.getBytes().length                    + 4 + 
                toAmount.getBytes().length                      + 4);
        
        byteBuffer.putInt(fromAddress.getBytes().length).put(fromAddress.getBytes());
        byteBuffer.putInt(fromTokenHex.getBytes().length).put(fromTokenHex.getBytes());
        byteBuffer.putInt(fromAmount.getBytes().length).put(fromAmount.getBytes());
        byteBuffer.putInt(toAddress.getBytes().length).put(toAddress.getBytes());
        byteBuffer.putInt(toTokenHex.getBytes().length).put(toTokenHex.getBytes());
        byteBuffer.putInt(toAmount.getBytes().length).put(toAmount.getBytes());
        byteBuffer.putInt(buf.length).put(buf);
        
        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showSaveDialog(null);
        FileUtil.writeFile(file, byteBuffer.array());
        overlayUI.done();
    }

    @SuppressWarnings("unchecked")
    public List<UTXO> getUTXOWithPubKeyHash(byte[] pubKeyHash, byte[] tokenid) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        String ContextRoot = "http://" + Main.IpAddress + ":" + Main.port + "/";
        String response = OkHttp3Util.post(ContextRoot + "getOutputs", pubKeyHash);
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        if (data == null || data.isEmpty()) {
            return listUTXO;
        }
        List<Map<String, Object>> outputs = (List<Map<String, Object>>) data.get("outputs");
        if (outputs == null || outputs.isEmpty()) {
            return listUTXO;
        }
        for (Map<String, Object> object : outputs) {
            UTXO utxo = MapToBeanMapperUtil.parseUTXO(object);
            if (!Arrays.equals(utxo.getTokenid(), tokenid)) {
                continue;
            }
            if (utxo.getValue().getValue() > 0) {
                listUTXO.add(utxo);
            }
        }
        return listUTXO;
    }

    public void refund(ActionEvent event) {
        overlayUI.done();
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

//    public List<UTXO> getUTXOWithECKeyList(ECKey ecKey, byte[] tokenid) throws Exception {
//        List<ECKey> ecKeys = new ArrayList<ECKey>();
//        ecKeys.add(ecKey);
//        return getUTXOWithECKeyList(ecKeys, tokenid);
//    }
    
    @SuppressWarnings("unchecked")
    public List<UTXO> getUTXOWithECKeyList(List<ECKey> ecKeys, byte[] tokenid) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        String ContextRoot = "http://" + Main.IpAddress + ":" + Main.port + "/";
        for (ECKey ecKey : ecKeys) {
            String response = OkHttp3Util.post(ContextRoot + "getOutputs", ecKey.getPubKeyHash());
            final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
            if (data == null || data.isEmpty()) {
                return listUTXO;
            }
            List<Map<String, Object>> outputs = (List<Map<String, Object>>) data.get("outputs");
            if (outputs == null || outputs.isEmpty()) {
                return listUTXO;
            }
            for (Map<String, Object> object : outputs) {
                UTXO utxo = MapToBeanMapperUtil.parseUTXO(object);
                if (!Arrays.equals(utxo.getTokenid(), tokenid)) {
                    continue;
                }
                if (utxo.getValue().getValue() > 0) {
                    listUTXO.add(utxo);
                }
            }
        }
        return listUTXO;
    }
}

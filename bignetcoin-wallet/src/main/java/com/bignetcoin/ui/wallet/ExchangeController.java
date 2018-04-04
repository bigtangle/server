/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.ui.wallet;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.MapToBeanMapperUtil;
import org.bitcoinj.utils.OkHttp3Util;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet.MissingSigsMode;
import org.spongycastle.crypto.params.KeyParameter;

import com.bignetcoin.ui.wallet.utils.GuiUtils;

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
            tokenData.add(tokenHex);
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
        String ContextRoot = "http://" + Main.IpAddress + ":" + Main.port + "/";
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(ContextRoot + "askTransaction", Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = Main.params.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(mTransaction);
        rollingBlock.solve();
        OkHttp3Util.post(ContextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
    }

    public void importBlock(ActionEvent event) {
        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        byte[] blockByte = null;
        if (file != null) {
            ByteArrayOutputStream byteArrayOutputStream = null;
            BufferedInputStream bufferedInputStream = null;
            byteArrayOutputStream = new ByteArrayOutputStream((int) file.length());
            try {
                bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
                int buffSize = 1024;
                byte[] buffer = new byte[buffSize];
                int len = 0;
                while (-1 != (len = bufferedInputStream.read(buffer, 0, buffSize))) {
                    byteArrayOutputStream.write(buffer, 0, len);
                }
                blockByte = byteArrayOutputStream.toByteArray();
            } catch (Exception e) {
                GuiUtils.crashAlert(e);
            } finally {
                if (bufferedInputStream != null) {
                    try {
                        bufferedInputStream.close();
                        if (byteArrayOutputStream != null) {
                            byteArrayOutputStream.close();
                        }
                    } catch (IOException e) {
                        GuiUtils.crashAlert(e);
                    }
                }
            }
        }
        if (blockByte != null) {
            try {
                mTransaction = (Transaction) Main.params.getDefaultSerializer().makeTransaction(blockByte);
                if (mTransaction == null) {
                    GuiUtils.informationalAlert("alert", "Transaction Is Empty");
                }
            }
            catch (Exception e) {
                GuiUtils.crashAlert(e);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public void exportBlock(ActionEvent event) {
        String ContextRoot = "http://" + Main.IpAddress + ":" + Main.port + "/";
        String fromAddress = fromAddressComboBox.getValue();
        String fromTokenHex = fromTokenHexComboBox.getValue();
        String fromAmount = fromAmountTextField.getText();
        String toAddress = fromAddressComboBox.getValue();
        String toTokenHex = toTokenHexComboBox.getValue();
        String toAmount = toAmountTextField.getText();
        byte[] blockByte = null;
        KeyParameter aesKey = null;
        try {
            List<UTXO> outputs = new ArrayList<UTXO>();

//            outputs.addAll(this.getUTXOWithECKeyList(ECKey.fromPublicOnly(pub), Utils.HEX.decode(toTokenHex)));
            outputs.addAll(this.getUTXOWithECKeyList(Main.bitcoin.wallet().walletKeys(aesKey), Utils.HEX.decode(fromTokenHex)));
            
            Coin amountCoin0 = Coin.parseCoin(toAmount, Utils.HEX.decode(toTokenHex));
            Coin amountCoin1 = Coin.parseCoin(fromAmount, Utils.HEX.decode(fromTokenHex));
            SendRequest req = SendRequest.to(new Address(Main.params, fromAddress), amountCoin0);
            req.tx.addOutput(amountCoin1, new Address(Main.params, toAddress));
            req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;

            List<TransactionOutput> candidates = Main.bitcoin.wallet().transforSpendCandidates(outputs);
            Main.bitcoin.wallet().setServerURL(ContextRoot);
            Main.bitcoin.wallet().completeTx(req, candidates, false);
            Main.bitcoin.wallet().signTransaction(req);
            
            this.mTransaction = req.tx;
            blockByte = mTransaction.bitcoinSerialize();
        }
        catch (Exception e) {
            GuiUtils.crashAlert(e);
            return;
        }
        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showSaveDialog(null);
        if (file == null)
            return;
        if (file.exists()) {
            file.delete();
        }
        BufferedOutputStream bufferedOutputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            bufferedOutputStream.write(blockByte);
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        } finally {
            try {
                if (bufferedOutputStream != null) {
                    bufferedOutputStream.close();
                }
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (Exception e) {
                GuiUtils.crashAlert(e);
            }
        }
    }

    public void refund(ActionEvent event) {
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

    public List<UTXO> getUTXOWithECKeyList(ECKey ecKey, byte[] tokenid) throws Exception {
        List<ECKey> ecKeys = new ArrayList<ECKey>();
        ecKeys.add(ecKey);
        return getUTXOWithECKeyList(ecKeys, tokenid);
    }
    
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

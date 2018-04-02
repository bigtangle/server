/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.ui.wallet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.OkHttp3Util;

import com.bignetcoin.ui.wallet.utils.GuiUtils;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class StockController {
    @FXML
    public CheckBox firstPublishCheckBox;
    @FXML
    public ComboBox<String> tokenid;

    @FXML
    public TextField stockName;

    @FXML
    public TextField stockAmount;
    @FXML
    public TextArea stockDescription;

    public Main.OverlayUI overlayUI;

    @FXML
    public void initialize() {
        firstPublishCheckBox.setAllowIndeterminate(false);
        try {
            initCombobox();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initCombobox() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        ECKey ecKey = Main.bitcoin.wallet().currentReceiveKey();
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokens", ecKey.getPubKeyHash());

        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");
        List<String> names = new ArrayList<String>();
        // wallet keys minus used from token list with one time (blocktype false
        // Todo
        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(null);
        for (ECKey key : keys) {
            String temp = Utils.HEX.encode(key.getPubKeyHash());
            for (Map<String, Object> map : list) {

                String tokenHex = (String) map.get("tokenHex");
                int blocktype = (int) map.get("blocktype");
                if (blocktype == NetworkParameters.BLOCKTYPE_GENESIS && !temp.equals(tokenHex)
                        && !tokenData.contains(temp)) {
                    tokenData.add(temp);
                    break;
                    // names.add(map.get("tokenname").toString());
                }
                if (blocktype == NetworkParameters.BLOCKTYPE_GENESIS_MULTIPLE && !tokenData.contains(temp)) {
                    tokenData.add(temp);
                    break;
                    // names.add(map.get("tokenname").toString());
                }

            }
        }
        tokenid.setItems(tokenData);
        // tokenid.getSelectionModel().selectedIndexProperty().addListener((ov,
        // oldv, newv) -> {
        // stockName.setText(names.get(newv.intValue()));
        // });

    }

    public void saveStock(ActionEvent event) {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ECKey outKey = Main.bitcoin.wallet().currentReceiveKey();

        try {
            byte[] pubKey = outKey.getPubKey();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
            requestParam.put("amount", Long.valueOf(stockAmount.getText()));
            requestParam.put("tokenname", stockName.getText());
            requestParam.put("description", stockDescription.getText());
            requestParam.put("tokenHex", tokenid.getValue());
            requestParam.put("blocktype", firstPublishCheckBox.selectedProperty().get());

            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "createGenesisBlock",
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block block = Main.params.getDefaultSerializer().makeBlock(data);

            GuiUtils.informationalAlert("token publish is ok", "", "");
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void closeStock(ActionEvent event) {
        overlayUI.done();
    }

}

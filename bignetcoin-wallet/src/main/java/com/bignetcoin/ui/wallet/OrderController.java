/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.ui.wallet;

import java.util.List;
import java.util.Map;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Json;
import org.bitcoinj.utils.OkHttp3Util;

import com.bignetcoin.ui.wallet.utils.GuiUtils;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class OrderController {
    @FXML
    public TextField limitTextField;
    @FXML
    public ComboBox<String> addressComboBox;
    @FXML
    public ComboBox<String> tokenComboBox;
    @FXML
    public ChoiceBox<Object> statusChoiceBox;

    public Main.OverlayUI overlayUI;

    @FXML
    public void initialize() {
        try {
            initComboBox();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initComboBox() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        ECKey ecKey = Main.bitcoin.wallet().currentReceiveKey();
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokens", ecKey.getPubKeyHash());

        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");
        for (Map<String, Object> map : list) {
            String tokenHex = (String) map.get("tokenHex");
            tokenData.add(tokenHex);
        }
        tokenComboBox.setItems(tokenData);

        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(null);
        ObservableList<String> addresses = FXCollections.observableArrayList();
        for (ECKey key : keys) {
            addresses.add(key.toAddress(Main.params).toString());
        }
        addressComboBox.setItems(addresses);
        
        ObservableList<Object> statusData= FXCollections.observableArrayList("buy","sell");
        statusChoiceBox.setItems(statusData);
    }

    @SuppressWarnings("deprecation")
    public void buy(ActionEvent event) throws Exception {

    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

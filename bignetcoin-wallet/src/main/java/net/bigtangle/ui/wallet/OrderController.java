/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.OkHttp3Util;

public class OrderController {
    @FXML
    public TextField limitTextField;
    @FXML
    public TextField amountTextField;
    @FXML
    public ComboBox<String> addressComboBox;
    @FXML
    public ComboBox<String> tokenComboBox;
    @FXML
    public ChoiceBox<Object> statusChoiceBox;
    @FXML
    public DatePicker validdateFromDatePicker;
    @FXML
    public DatePicker validdateToDatePicker;

    public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {
        try {
            initComboBox();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    @SuppressWarnings("unchecked")
    public void initComboBox() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        ECKey ecKey = Main.bitcoin.wallet().currentReceiveKey();
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokens", ecKey.getPubKeyHash());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");
        for (Map<String, Object> map : list) {
            String tokenHex = (String) map.get("tokenHex");
            String tokenname = (String) map.get("tokenname");
            tokenData.add(tokenname + " : " + tokenHex);
        }
        tokenComboBox.setItems(tokenData);
        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(null);
        ObservableList<String> addresses = FXCollections.observableArrayList();
        for (ECKey key : keys) {
            addresses.add(key.toAddress(Main.params).toString());
        }
        addressComboBox.setItems(addresses);
        ObservableList<Object> statusData = FXCollections.observableArrayList("buy", "sell");
        statusChoiceBox.setItems(statusData);
    }

    public void buy(ActionEvent event) throws Exception {
        if (validdateFromDatePicker.getValue().equals("")) {
            GuiUtils.informationalAlert("save order param", "validdate From Date Picker ERROR");
            return;
        }
        if (validdateToDatePicker.getValue().equals("")) {
            GuiUtils.informationalAlert("save order param", "validdate To Date Picker ERROR");
            return;
        }
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd 00:00:00");
        String validdateFrom = df.format(validdateFromDatePicker.getValue());
        String validdateTo = df.format(validdateToDatePicker.getValue());
        String ContextRoot = "http://" + Main.IpAddress + ":" + Main.port + "/";
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("address", this.addressComboBox.getValue());
        requestParam.put("tokenid", this.tokenComboBox.getValue());
        String typeStr = (String) statusChoiceBox.getValue();
        requestParam.put("type", typeStr.equals("sell") ? 1 : 0);
        int limit = Integer.parseInt(this.limitTextField.getText());
        requestParam.put("limitl", limit);
        requestParam.put("validateto", validdateTo);
        requestParam.put("validatefrom", validdateFrom);
        OkHttp3Util.post(ContextRoot + "saveOrder", Json.jsonmapper().writeValueAsString(requestParam));
        overlayUI.done();
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.spongycastle.crypto.params.KeyParameter;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import net.bigtangle.core.Address;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.OrderState;

public class OrderController {
    @FXML
    public TextField limitTextField;
    @FXML
    public TextField amountTextField;

    @FXML
    public TextField orderid4searchTextField;
    @FXML
    public TextField address4searchTextField;

    @FXML
    public ComboBox<String> addressComboBox;
    @FXML
    public ComboBox<String> tokenComboBox;

    @FXML
    public ComboBox<String> marketComboBox;

    @FXML
    public ChoiceBox<Object> statusChoiceBox;

    @FXML
    public ChoiceBox<String> state4searchChoiceBox;

    @FXML
    public DatePicker validdateFromDatePicker;
    @FXML
    public DatePicker validdateToDatePicker;
    @FXML
    public TableView<Map<String, Object>> orderTable;
    @FXML
    public TableColumn<Map<String, Object>, String> orderidCol;
    @FXML
    public TableColumn<Map<String, Object>, String> addressCol;
    @FXML
    public TableColumn<Map<String, Object>, String> tokenidCol;
    @FXML
    public TableColumn<Map<String, Object>, String> typeCol;
    @FXML
    public TableColumn<Map<String, Object>, String> validdatetoCol;
    @FXML
    public TableColumn<Map<String, Object>, String> validdatefromCol;
    @FXML
    public TableColumn<Map<String, Object>, String> stateCol;
    @FXML
    public TableColumn<Map<String, Object>, String> priceCol;
    @FXML
    public TableColumn<Map<String, Object>, String> amountCol;

    public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {
        try {
            String[] items = new String[OrderState.values().length];
            for (int i = 0; i < OrderState.values().length; i++) {
                items[i] = OrderState.values()[i].name();
            }
            state4searchChoiceBox.setItems(FXCollections.observableArrayList(items));
            state4searchChoiceBox.getSelectionModel().selectFirst();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            initComboBox();
            initTable(requestParam);
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initAddress(String address) {
        addressComboBox.setValue(address);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void initTable(Map<String, Object> requestParam) throws Exception {
        if (requestParam.containsKey("state")) {
            String stateStr = (String) requestParam.get("state");
            OrderState orderState = OrderState.valueOf(stateStr);
            requestParam.put("state", orderState.ordinal());
        }
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<Map<String, Object>> orderData = FXCollections.observableArrayList();
        // HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getOrders",
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("orders");
        if (list != null) {
            for (Map<String, Object> map : list) {
                if ((Integer) map.get("type") == 1) {
                    map.put("type", "SELL");
                } else {
                    map.put("type", "BUY");
                }
                int stateIndex = (int) map.get("state");
                OrderState orderState = OrderState.values()[stateIndex];
                map.put("state", orderState.name());
                Coin fromAmount = Coin.valueOf(Long.parseLong(map.get("price").toString()),
                        Utils.HEX.decode((String) map.get("tokenid")));
                Coin toAmount = Coin.valueOf(Long.parseLong(map.get("amount").toString()),
                        Utils.HEX.decode((String) map.get("tokenid")));
                map.put("price", fromAmount.toPlainString());
                map.put("amount", toAmount.toPlainString());
                orderData.add(map);
            }
        }
        orderidCol.setCellValueFactory(new MapValueFactory("orderid"));
        addressCol.setCellValueFactory(new MapValueFactory("address"));
        tokenidCol.setCellValueFactory(new MapValueFactory("tokenid"));
        typeCol.setCellValueFactory(new MapValueFactory("type"));
        validdatetoCol.setCellValueFactory(new MapValueFactory("validateto"));
        validdatefromCol.setCellValueFactory(new MapValueFactory("validatefrom"));
        stateCol.setCellValueFactory(new MapValueFactory("state"));
        priceCol.setCellValueFactory(new MapValueFactory("price"));
        amountCol.setCellValueFactory(new MapValueFactory("amount"));

        orderidCol.setCellFactory(TextFieldTableCell.forTableColumn());
        addressCol.setCellFactory(TextFieldTableCell.forTableColumn());
        tokenidCol.setCellFactory(TextFieldTableCell.forTableColumn());

        orderTable.setItems(orderData);
    }

    @SuppressWarnings("unchecked")
    public void initComboBox() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokens",
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");
        for (Map<String, Object> map : list) {
            String tokenHex = (String) map.get("tokenHex");
            String tokenname = (String) map.get("tokenname");
            tokenData.add(tokenname + " : " + tokenHex);
        }
        tokenComboBox.setItems(tokenData);
        KeyParameter aeskey = null;
        // Main.initAeskey(aeskey);
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aeskey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(aeskey);
        ObservableList<String> addresses = FXCollections.observableArrayList();
        for (ECKey key : keys) {
            addresses.add(key.toAddress(Main.params).toString());
        }
        addressComboBox.setItems(addresses);
        ObservableList<Object> statusData = FXCollections.observableArrayList("buy", "sell");
        statusChoiceBox.setItems(statusData);
    }

    public void buy(ActionEvent event) throws Exception {
        String tokenid = tokenComboBox.getValue().split(":")[1].trim();
        List<UTXO> utxos = Main.getUTXOWithPubKeyHash(
                Address.fromBase58(Main.params, addressComboBox.getValue()).getHash160(), Utils.HEX.decode(tokenid));
        long utxoAmount = 0;
        if (utxos != null && !utxos.isEmpty()) {
            for (UTXO utxo : utxos) {
                utxoAmount += utxo.getValue().getValue();
            }
        }
        long amount = Coin.parseCoinValue(this.amountTextField.getText());

        if (utxoAmount < amount) {
            GuiUtils.informationalAlert("amount no enough", "amount no enough", "");
            return;
        }

        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd 00:00:00");
        String validdateFrom = "";
        if (validdateFromDatePicker.getValue() != null) {
            validdateFrom = df.format(validdateFromDatePicker.getValue());
        }
        String validdateTo = "";
        if (validdateToDatePicker.getValue() != null) {
            validdateTo = df.format(validdateToDatePicker.getValue());
        }
        String ContextRoot = "http://" + Main.IpAddress + ":" + Main.port + "/";
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("address", this.addressComboBox.getValue());
        // String tokenid = this.tokenComboBox.getValue().split(":")[1].trim();
        requestParam.put("tokenid", tokenid);
        String typeStr = (String) statusChoiceBox.getValue();
        requestParam.put("type", typeStr.equals("sell") ? 1 : 0);
        long price = Coin.parseCoinValue(this.limitTextField.getText());
        requestParam.put("price", price);
        requestParam.put("amount", amount);
        requestParam.put("validateto", validdateTo);
        requestParam.put("validatefrom", validdateFrom);
        // TODO xiao mi change
        String market = marketComboBox.getValue();
        requestParam.put("market", market);
        OkHttp3Util.post(ContextRoot + "saveOrder", Json.jsonmapper().writeValueAsString(requestParam));
        overlayUI.done();
    }

    public void search(ActionEvent event) {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("orderid", orderid4searchTextField.getText());
        requestParam.put("address", address4searchTextField.getText());
        requestParam.put("state", state4searchChoiceBox.getValue());
        try {
            initTable(requestParam);
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
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
            initComboBox();
            initTable();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initTable() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<Map<String, Object>> orderData = FXCollections.observableArrayList();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getOrders",
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("orders");
        for (Map<String, Object> map : list) {
            orderData.add(map);
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
//        if (validdateFromDatePicker.getValue() == null) {
//            GuiUtils.informationalAlert("save order param", "validdate From Date Picker ERROR");
//            return;
//        }
//        if (validdateToDatePicker.getValue() == null) {
//            GuiUtils.informationalAlert("save order param", "validdate To Date Picker ERROR");
//            return;
//        }
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
        String tokenid = this.tokenComboBox.getValue().split(":")[1].trim();
        requestParam.put("tokenid", tokenid);
        String typeStr = (String) statusChoiceBox.getValue();
        requestParam.put("type", typeStr.equals("sell") ? 1 : 0);
        int price = Integer.parseInt(this.limitTextField.getText());
        int amount = Integer.parseInt(this.amountTextField.getText());
        requestParam.put("price", price);
        requestParam.put("amount", amount);
        requestParam.put("validateto", validdateTo);
        requestParam.put("validatefrom", validdateFrom);
        OkHttp3Util.post(ContextRoot + "saveOrder", Json.jsonmapper().writeValueAsString(requestParam));
        overlayUI.done();
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

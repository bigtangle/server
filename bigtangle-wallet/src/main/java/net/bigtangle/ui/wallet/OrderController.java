/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import net.bigtangle.core.Address;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.ordermatch.resp.GetOrderResponse;
import net.bigtangle.core.http.server.resp.GetTokensResponse;
import net.bigtangle.core.http.server.resp.OrderdataResponse;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.params.OrdermatchReqCmd;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.TextFieldValidator;
import net.bigtangle.ui.wallet.utils.WTUtils;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.OrderState;

public class OrderController extends ExchangeController {
    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    @FXML
    public CheckBox mineCB;

    @FXML
    public TextField fromTimeTF;
    @FXML
    public TextField fromTimeTF1;
    @FXML
    public TextField toTimeTF;

    @FXML
    public TextField limitTextField;
    @FXML
    public TextField quantityTextField;

    @FXML
    public TextField orderid4searchTextField;
    @FXML
    public TextField address4searchTextField;
    @FXML
    public TextField market4searchTextField;

    @FXML
    public ComboBox<String> addressComboBox;
    @FXML
    public ComboBox<String> tokenComboBox;

    @FXML
    public ComboBox<String> marketComboBox;

    @FXML
    public RadioButton buyRadioButton;
    @FXML
    public RadioButton sellRadioButton;

    @FXML
    public RadioButton stateRB1;
    @FXML
    public RadioButton stateRB2;
    @FXML
    public RadioButton stateRB3;
    @FXML
    public ToggleGroup stateTG;
    @FXML
    public ToggleGroup buySellTG;

    @FXML
    public DatePicker validdateFromDatePicker;
    @FXML
    public DatePicker validdateFromDatePicker1;
    @FXML
    public DatePicker validdateToDatePicker;
    @FXML
    public TableView<Map<String, Object>> orderTable;
    @FXML
    public TableColumn<Map<String, Object>, String> orderidCol;
    @FXML
    public TableColumn<Map<String, Object>, String> addressCol;
    @FXML
    public TableColumn<Map<String, Object>, String> beneficiaryAddressCol;
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
    public Set<String> tempAddressSet;
    public ChangeListener<String> myListener;
    public ChangeListener<String> myListenerA;

    @FXML
    public ComboBox<String> addressComboBox1;
    @FXML
    public ComboBox<String> tokenComboBox1;
    @FXML
    public DatePicker validdateToDatePicker1;
    @FXML
    public TextField limitTextField1;
    @FXML
    public TextField quantityTextField1;
    @FXML
    public RadioButton buyRadioButton1;
    @FXML
    public RadioButton sellRadioButton1;
    @FXML
    public ToggleGroup buySellTG1;
    @FXML
    public TextField toTimeTF1;

    @FXML
    public TabPane tabPane;

    // public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {
        try {

            buyRadioButton.setUserData("buy");
            sellRadioButton.setUserData("sell");
            buyRadioButton1.setUserData("buy");
            sellRadioButton1.setUserData("sell");
            stateRB1.setUserData("publish");
            stateRB2.setUserData("match");
         //   stateRB3.setUserData("finish");
            mineCB.setSelected(true);
            Main.resetWachted();
            initMarketComboBox();
            myListener = (ov1, o1, n1) -> {
                if (n1 != null && !n1.isEmpty()) {
                    if (Main.validTokenMap != null && !Main.validTokenMap.isEmpty()) {
                        tempAddressSet = Main.validTokenMap.get(n1.split(":")[1].trim());
                        ObservableList<String> addresses = FXCollections.observableArrayList(tempAddressSet);
                        addressComboBox.setItems(addresses);
                    }

                }

            };
            myListenerA = (ov1, o1, n1) -> {
                if (n1 != null && !n1.isEmpty()) {
                    if (Main.validTokenMap != null && !Main.validTokenMap.isEmpty()) {
                        tempAddressSet = Main.validTokenMap.get(n1.split(":")[1].trim());
                        ObservableList<String> addresses = FXCollections.observableArrayList(tempAddressSet);
                        addressComboBox1.setItems(addresses);
                    }

                }

            };
            buySellTG.selectedToggleProperty().addListener((ov, o, n) -> {
                String temp = n.getUserData().toString();
                boolean flag = "sell".equalsIgnoreCase(temp);
                try {
                    initComboBox(!flag);
                } catch (Exception e) {

                }

                if (flag) {
                    tempAddressSet = new HashSet<String>();

                    tokenComboBox.getSelectionModel().selectedItemProperty().addListener(myListener);
                    if (Main.validTokenMap != null && !Main.validTokenMap.isEmpty()) {
                        tokenComboBox.getSelectionModel().selectFirst();
                        if (tokenComboBox.getValue() != null && !tokenComboBox.getValue().trim().isEmpty()) {
                            String key = tokenComboBox.getValue().split(":")[1];
                            if (Main.validTokenMap.get(key) != null && !Main.validTokenMap.get(key).isEmpty()) {
                                tempAddressSet = Main.validTokenMap.get(key);
                            }
                        }

                    }

                } else {
                    tokenComboBox.getSelectionModel().selectedItemProperty().removeListener(myListener);
                    tempAddressSet = Main.validAddressSet;

                }
                ObservableList<String> addresses = FXCollections.observableArrayList(tempAddressSet);

                addressComboBox.setItems(addresses);

            });

            buySellTG1.selectedToggleProperty().addListener((ov, o, n) -> {
                String temp = n.getUserData().toString();
                boolean flag = "sell".equalsIgnoreCase(temp);
                try {
                    initComboBox(!flag);
                } catch (Exception e) {

                }

                if (flag) {
                    tempAddressSet = new HashSet<String>();

                    tokenComboBox1.getSelectionModel().selectedItemProperty().addListener(myListenerA);
                    if (Main.validTokenMap != null && !Main.validTokenMap.isEmpty()) {
                        tokenComboBox1.getSelectionModel().selectFirst();
                        if (tokenComboBox1.getValue() != null && !tokenComboBox1.getValue().trim().isEmpty()) {
                            String key = tokenComboBox1.getValue().split(":")[1];
                            if (Main.validTokenMap.get(key) != null && !Main.validTokenMap.get(key).isEmpty()) {
                                tempAddressSet = Main.validTokenMap.get(key);
                            }
                        }

                    }

                } else {
                    tokenComboBox1.getSelectionModel().selectedItemProperty().removeListener(myListenerA);
                    tempAddressSet = Main.validAddressSet;

                }
                ObservableList<String> addresses = FXCollections.observableArrayList(tempAddressSet);

                addressComboBox1.setItems(addresses);

            });
            tabPane.getSelectionModel().selectedIndexProperty().addListener((ov, t, t1) -> {
                int index = t1.intValue();
                switch (index) {
                case 0: {
                }

                    break;
                case 1: {
                }

                    break;
                case 2: {
                }

                    break;
                case 3: {
                }

                    break;
                }
            });
            initComboBox(true);
            // TODO auto initTable is quite slow and disabled now and click
            // search to start initTable initTable(requestParam);
            super.initialize();
            new TextFieldValidator(fromTimeTF, text -> !WTUtils.didThrow(() -> checkState(Main.isTime(text))));
            new TextFieldValidator(toTimeTF, text -> !WTUtils.didThrow(() -> checkState(Main.isTime(text))));
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initAddress(String address) {
        addressComboBox.setValue(address);
        addressComboBox1.setValue(address);
    }

    public void refreshSIgnTable(ActionEvent event) {
        try {
            super.initTable();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void initTable(Map<String, Object> requestParam) throws Exception {

        ObservableList<Map<String, Object>> orderData = FXCollections.observableArrayList();

        String CONTEXT_ROOT = Main.getContextRoot();
        getOrder(requestParam, orderData, CONTEXT_ROOT);

        getOTCOrder(requestParam, orderData, CONTEXT_ROOT);
        orderidCol.setCellValueFactory(new MapValueFactory("orderId"));
        addressCol.setCellValueFactory(new MapValueFactory("address"));
        // beneficiaryAddressCol.setCellValueFactory(new
        // MapValueFactory("beneficiaryAddress"));
        tokenidCol.setCellValueFactory(new MapValueFactory("tokenId"));
        typeCol.setCellValueFactory(new MapValueFactory("type"));
        // TODO
        validdatetoCol.setCellValueFactory(new MapValueFactory("validateTo"));
        validdatefromCol.setCellValueFactory(new MapValueFactory("validatefrom"));
        stateCol.setCellValueFactory(new MapValueFactory("state"));
        priceCol.setCellValueFactory(new MapValueFactory("price"));
        amountCol.setCellValueFactory(new MapValueFactory("amount"));

        orderidCol.setCellFactory(TextFieldTableCell.forTableColumn());
        addressCol.setCellFactory(TextFieldTableCell.forTableColumn());
        tokenidCol.setCellFactory(TextFieldTableCell.forTableColumn());

        orderTable.setItems(orderData);
    }

    private void getOTCOrder(Map<String, Object> requestParam, ObservableList<Map<String, Object>> orderData,
            String CONTEXT_ROOT)
            throws Exception, JsonProcessingException, IOException, JsonParseException, JsonMappingException {
        if (requestParam.containsKey("state")) {
            String stateStr = (String) requestParam.get("state");
            OrderState orderState = OrderState.valueOf(stateStr);
            requestParam.put("state", orderState.ordinal());
        }

        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getOTCMarkets.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(response, GetTokensResponse.class);

        for (Token tokens : getTokensResponse.getTokens()) {
            if (tokens.getTokentype() != TokenType.market.ordinal()) {
                continue;
            }
            String url ="https://"+ tokens.getDomainname();
            try {
                response = OkHttp3Util.post(url + "/" + OrdermatchReqCmd.getOrders.name(),
                        Json.jsonmapper().writeValueAsString(requestParam).getBytes());
            } catch (Exception e) {
                continue;
            }
            GetOrderResponse getOrderResponse = Json.jsonmapper().readValue(response, GetOrderResponse.class);
            for (OrderPublish orderPublish : getOrderResponse.getOrders()) {
                HashMap<String, Object> map = new HashMap<String, Object>();
                if (orderPublish.getType() == 1) {
                    map.put("type", Main.getText("SELL"));
                } else {
                    map.put("type", Main.getText("BUY"));
                }
                int stateIndex = orderPublish.getState();
                OrderState orderState = OrderState.values()[stateIndex];
                map.put("state", Main.getText(orderState.name()));

                byte[] tokenid = null;

                if (StringUtils.isNotBlank(orderPublish.getTokenId())) {
                    tokenid = Utils.HEX.decode(orderPublish.getTokenId());
                }
                Coin fromAmount = Coin.valueOf(orderPublish.getPrice(), tokenid);
                Coin toAmount = Coin.valueOf(orderPublish.getAmount(), tokenid);
                map.put("price", fromAmount.toPlainString());
                map.put("amount", toAmount.toPlainString());
                map.put("orderId", orderPublish.getOrderId());
                map.put("address", orderPublish.getAddress());
                map.put("tokenId", orderPublish.getTokenId());
                map.put("validateTo", orderPublish.getValidateTo());
                map.put("validateFrom", orderPublish.getValidateFrom());
                map.put("market", orderPublish.getMarket());
                orderData.add(map);
            }
        }
    }

    private void getOrder(Map<String, Object> requestParam, ObservableList<Map<String, Object>> orderData,
            String CONTEXT_ROOT)
            throws Exception, JsonProcessingException, IOException, JsonParseException, JsonMappingException {
        if (requestParam.containsKey("state")) {
            String stateStr = (String) requestParam.get("state");
            requestParam.put("spent", "publish".equals(stateStr) ? "false" : "true");
        }
        boolean ifMineOrder = mineCB.isSelected();
      
        List<ECKey> keys = Main.walletAppKit.wallet().walletKeys(Main.getAesKey());
        List<String> address = new ArrayList<String>();
        if (ifMineOrder) {
            for (ECKey ecKey : keys) {
                address.add(ecKey.toAddress(Main.params).toString());
            }
            requestParam.put("addresses", address);
        }

        String response0 = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        log.debug(response0);
        OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);

        for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
            HashMap<String, Object> map = new HashMap<String, Object>();

            if (NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(orderRecord.getOfferTokenid())) {
                map.put("type", Main.getText("BUY"));
                map.put("amount", orderRecord.getTargetValue());
                map.put("tokenId", orderRecord.getTargetTokenid());
                map.put("price", Coin.toPlainString(orderRecord.getOfferValue() / orderRecord.getTargetValue()));
            } else {
                map.put("type", Main.getText("SELL"));
                map.put("amount", orderRecord.getOfferValue());
                map.put("tokenId", orderRecord.getOfferTokenid());
                map.put("price", Coin.toPlainString(orderRecord.getTargetValue() / orderRecord.getOfferValue()));
            }
            map.put("orderId", orderRecord.getInitialBlockHashHex());
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            map.put("validateTo", dateFormat.format(new Date(orderRecord.getValidToTime() * 1000)));
            map.put("validatefrom", dateFormat.format(new Date(orderRecord.getValidFromTime() * 1000)));
            map.put("address",
                    ECKey.fromPublicOnly(orderRecord.getBeneficiaryPubKey()).toAddress(Main.params).toString());
            map.put("initialBlockHashHex", orderRecord.getInitialBlockHashHex());
         //   map.put("state", Main.getText( (String) requestParam.get("state")));
            orderData.add(map);
        }
    }

    public void initMarketComboBox() throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getOTCMarkets.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(response, GetTokensResponse.class);
        for (Token tokens : getTokensResponse.getTokens()) {
            String tokenHex = tokens.getBlockhash();
            String tokenname = tokens.getTokenname();
            tokenData.add(tokenname + " : " + tokenHex);
        }
        marketComboBox.setItems(tokenData);
    }

    /**
     * 
     * @param all
     *            buy==all without system coin BIG
     * @throws Exception
     */
    public void initComboBox(boolean buy) throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getTokensAmount.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(response, GetTokensResponse.class);

        if (!buy) {
            if (Main.validTokenSet != null && !Main.validTokenSet.isEmpty()) {
                for (String tokeninfo : Main.validTokenSet) {
                    if (!isSystemCoin(tokeninfo)) {
                        tokenData.add(tokeninfo);
                    }
                }
            }
        } else {

            for (Token p : Main.getWatched().getTokenList()) {
                if (!isSystemCoin(p.getTokenid())) {
                    if (!tokenData.contains(p.getTokenname() + ":" + p.getTokenid())) {
                 //       if (Main.getNoMultiTokens().contains(p.getTokenid())) {
                            tokenData.add(p.getTokenname() + ":" + p.getTokenid());
                  //      }
                    }
                }
            }

        }

        tokenComboBox.setItems(tokenData);
        // tokenComboBox.getSelectionModel().selectFirst();
        tokenComboBox1.setItems(tokenData);
        // tokenComboBox1.getSelectionModel().selectFirst();
        ObservableList<String> addresses = FXCollections.observableArrayList(Main.validAddressSet);
        addressComboBox.setItems(addresses);
        addressComboBox1.setItems(addresses);
    }

    public boolean isSystemCoin(String token) {
        return ("BIG:" + NetworkParameters.BIGTANGLE_TOKENID_STRING).equals(token)
                || (NetworkParameters.BIGTANGLE_TOKENID_STRING).equals(token);

    }

    public void buy(ActionEvent event) throws Exception {

        try {
            buyOTCDo(event);
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void cancelMyOrder(ActionEvent event) throws Exception {

        try {
            cancelOrderDo();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void cancelOrderDo() throws Exception {
        String ContextRoot = Main.getContextRoot();
        Main.walletAppKit.wallet().setServerURL(ContextRoot);
        Map<String, Object> rowData = orderTable.getSelectionModel().getSelectedItem();
        if (rowData == null || rowData.isEmpty()) {
            GuiUtils.informationalAlert("", Main.getText("pleaseSelect"), "");
            return;
        }
        Sha256Hash hash = Sha256Hash.wrap(rowData.get("initialBlockHashHex").toString());
        ECKey legitimatingKey = null;

      
        List<ECKey> keys = Main.walletAppKit.wallet().walletKeys(Main.getAesKey());
        for (ECKey ecKey : keys) {
            if (rowData.get("address").equals(ecKey.toAddress(Main.params).toString())) {
                legitimatingKey = ecKey;
                Main.walletAppKit.wallet().cancelOrder(hash, legitimatingKey);
                break;
            }
        }

    }

    public void buyA(ActionEvent event) throws Exception {

        try {
            buyDo(event);
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void buyDo(ActionEvent event) throws Exception {

        log.debug(tokenComboBox1.getValue());
        String tokenid = tokenComboBox1.getValue().split(":")[1].trim();
        String typeStr = (String) buySellTG1.getSelectedToggle().getUserData().toString();

        byte[] pubKeyHash = Address.fromBase58(Main.params, addressComboBox1.getValue()).getHash160();

        Coin coin = Main.calculateTotalUTXOList(pubKeyHash,
                typeStr.equals("sell") ? tokenid : NetworkParameters.BIGTANGLE_TOKENID_STRING);
        long quantity = Long.valueOf(this.quantityTextField1.getText());
        Coin price = Coin.parseCoin(this.limitTextField1.getText(), NetworkParameters.BIGTANGLE_TOKENID);
        long amount = quantity;
        if (!typeStr.equals("sell")) {
            amount = quantity * price.getValue();
        }
        if (coin.getValue() < amount) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m"), Main.getText("o_c_d"));
            return;
        }
        // TODO time and null
        LocalDate to = validdateToDatePicker1.getValue();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String validdateTo = "";
        Long totime = null;
        if (to != null) {
            validdateTo = df.format(to);
            String validateTime = validdateTo + " " + toTimeTF1.getText();
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            totime = dateFormat.parse(validateTime).getTime();
        }

        LocalDate from = validdateFromDatePicker1.getValue();
        String validdatefrom = "";
        Long fromtime = null;
        if (from != null) {
            validdatefrom = df.format(from);
            String validatefromTime = validdatefrom + " " + fromTimeTF1.getText();
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            fromtime = dateFormat.parse(validatefromTime).getTime();
        }

        String ContextRoot = Main.getContextRoot();
        Main.walletAppKit.wallet().setServerURL(ContextRoot);
 
        List<ECKey> keys = Main.walletAppKit.wallet().walletKeys(Main.getAesKey());
        ECKey beneficiary = null;
        for (ECKey ecKey : keys) {
            if (addressComboBox1.getValue().equals(ecKey.toAddress(Main.params).toString())) {
                beneficiary = ecKey;
                break;
            }
        }

        if (typeStr.equals("sell")) {
            Main.walletAppKit.wallet().sellOrder(Main.getAesKey(),  tokenid, price.getValue(), quantity,
                    totime, fromtime);
        } else {
            Main.walletAppKit.wallet().buyOrder(Main.getAesKey(), tokenid, price.getValue(), quantity,
                    totime, fromtime);
        }

        overlayUI.done();
    }

    public void buyOTCDo(ActionEvent event) throws Exception {

        log.debug(tokenComboBox.getValue());
        String tokenid = tokenComboBox.getValue().split(":")[1].trim();
        String typeStr = (String) buySellTG.getSelectedToggle().getUserData().toString();

        byte[] pubKeyHash = Address.fromBase58(Main.params, addressComboBox.getValue()).getHash160();

        Coin coin = Main.calculateTotalUTXOList(pubKeyHash,
                typeStr.equals("sell") ? tokenid : NetworkParameters.BIGTANGLE_TOKENID_STRING);
        long quantity = Long.valueOf(this.quantityTextField.getText());
        Coin price = Coin.parseCoin(this.limitTextField.getText(), NetworkParameters.BIGTANGLE_TOKENID);
        long amount = quantity;
        if (!typeStr.equals("sell")) {
            amount = quantity * price.getValue();
        }
        if (coin.getValue() < amount) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m"), Main.getText("o_c_d"));
            return;
        }

        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String validdateFrom = "";
        if (validdateFromDatePicker.getValue() != null) {
            validdateFrom = df.format(validdateFromDatePicker.getValue());
        }
        String validdateTo = "";
        if (validdateToDatePicker.getValue() != null) {
            validdateTo = df.format(validdateToDatePicker.getValue());
        }
        String ContextRoot = Main.getContextRoot();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("address", addressComboBox.getValue());
        // String tokenid = this.tokenComboBox.getValue().split(":")[1].trim();
        requestParam.put("tokenid", tokenid);
        if (typeStr.equals("sell")) {
            Set<String> addrSet = Main.validOutputMultiMap.get(tokenid);
            if (addrSet != null && !addrSet.isEmpty()) {
                addrSet.remove(addressComboBox.getValue());
                requestParam.put("signaddress", Main.validOutputMultiMap.get(tokenid));
            }
        }
        requestParam.put("type", typeStr.equals("sell") ? 1 : 0);

        requestParam.put("price", price);
        requestParam.put("amount", amount);
        requestParam.put("validateto", validdateTo + " " + toTimeTF.getText());
        requestParam.put("validatefrom", validdateFrom + " " + fromTimeTF.getText());
        // TODO xiao mi change
        String market = marketComboBox.getValue();
        String temp = market.contains(":") ? market.substring(market.indexOf(":") + 1).trim() : market.trim();
        requestParam.put("market", temp);

        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("tokenid", temp);
        String resp = OkHttp3Util.postString(ContextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        Token token_ = getTokensResponse.getTokens().get(0);

        String url ="https://"+ token_.getDomainname();
        OkHttp3Util.post(url + "/" + OrdermatchReqCmd.saveOrder.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        overlayUI.done();
    }

    public void search(ActionEvent event) {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        // requestParam.put("orderid", orderid4searchTextField.getText());
        requestParam.put("address", address4searchTextField.getText());
        // requestParam.put("market", market4searchTextField.getText());

        requestParam.put("state", stateTG.getSelectedToggle().getUserData().toString());
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

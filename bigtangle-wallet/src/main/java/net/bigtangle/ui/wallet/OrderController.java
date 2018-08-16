/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import static com.google.common.base.Preconditions.checkState;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
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
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.UserSettingData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.WatchedInfo;
import net.bigtangle.core.http.ordermatch.resp.GetOrderResponse;
import net.bigtangle.core.http.server.resp.GetTokensResponse;
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
    public TextField fromTimeTF;
    @FXML
    public TextField toTimeTF;

    @FXML
    public TextField limitTextField;
    @FXML
    public TextField amountTextField;

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
    public Set<String> tempAddressSet;
    public ChangeListener<String> myListener;

    @FXML
    public TabPane tabPane;

    // public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {
        try {

            buyRadioButton.setUserData("buy");
            sellRadioButton.setUserData("sell");
            stateRB1.setUserData("publish");
            stateRB2.setUserData("match");
            stateRB3.setUserData("finish");
            WatchedInfo watchedInfo = (WatchedInfo) Main.getUserdata(DataClassName.TOKEN.name());
            Main.tokenInfo = new TokenInfo();
            List<UserSettingData> list = watchedInfo.getUserSettingDatas();
            for (UserSettingData userSettingData : list) {
                if (DataClassName.TOKEN.name().equals(userSettingData.getDomain().trim())) {
                    Main.tokenInfo.getPositveTokenList()
                            .add(new Tokens(userSettingData.getKey(), userSettingData.getValue()));
                }
            }
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
                        String key = tokenComboBox.getValue().split(":")[1];
                        if (Main.validTokenMap.get(key) != null && !Main.validTokenMap.get(key).isEmpty()) {
                            tempAddressSet = Main.validTokenMap.get(key);
                        }

                    }

                } else {
                    tokenComboBox.getSelectionModel().selectedItemProperty().removeListener(myListener);
                    tempAddressSet = Main.validAddressSet;

                }
                ObservableList<String> addresses = FXCollections.observableArrayList(tempAddressSet);

                addressComboBox.setItems(addresses);

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
        if (requestParam.containsKey("state")) {
            String stateStr = (String) requestParam.get("state");
            OrderState orderState = OrderState.valueOf(stateStr);
            requestParam.put("state", orderState.ordinal());
        }
        ObservableList<Map<String, Object>> orderData = FXCollections.observableArrayList();

        String CONTEXT_ROOT = Main.getContextRoot();
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getMarkets.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(response, GetTokensResponse.class);

        for (Tokens tokens : getTokensResponse.getTokens()) {
            if (tokens.getTokentype() != TokenType.market.ordinal()) {
                continue;
            }
            String url = tokens.getUrl();
            try {
                response = OkHttp3Util.post(url + "/" + OrdermatchReqCmd.getOrders.name(),
                        Json.jsonmapper().writeValueAsString(requestParam).getBytes());
                log.info("+++++++++");
                log.info(response);
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

    public void initMarketComboBox() throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getMarkets.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(response, GetTokensResponse.class);
        for (Tokens tokens : getTokensResponse.getTokens()) {
            String tokenHex = tokens.getTokenid();
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
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getTokensNoMarket.name(),
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
            for (Tokens tokens : getTokensResponse.getTokens()) {
                String tokenHex = tokens.getTokenid();
                if (tokens.getTokentype() != TokenType.token.ordinal()) {
                    continue;
                }
                String tokenname = tokens.getTokenname();
                if (!isSystemCoin(tokenname + ":" + tokenHex)) {
                    tokenData.add(tokenname + ":" + tokenHex);
                }
            }
            if (Main.tokenInfo != null && Main.tokenInfo.getPositveTokenList() != null) {
                for (Tokens p : Main.tokenInfo.getPositveTokenList()) {
                    if (!isSystemCoin(p.getTokenname() + ":" + p.getTokenid())
                            && p.getTokenname().endsWith(":" + Main.getText("Token"))) {
                        if (!tokenData.contains(
                                p.getTokenname().substring(0, p.getTokenname().indexOf(":")) + ":" + p.getTokenid())) {
                            tokenData.add(p.getTokenname().substring(0, p.getTokenname().indexOf(":")) + ":"
                                    + p.getTokenid());
                        }

                    }
                }
            }
        }

        tokenComboBox.setItems(tokenData);
        tokenComboBox.getSelectionModel().selectFirst();
        ObservableList<String> addresses = FXCollections.observableArrayList(Main.validAddressSet);
        addressComboBox.setItems(addresses);
    }

    public boolean isSystemCoin(String token) {
        return ("BIG:" + NetworkParameters.BIGNETCOIN_TOKENID_STRING).equals(token);

    }

    public void buy(ActionEvent event) throws Exception {

        try {
            buyDo(event);
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void buyDo(ActionEvent event) throws Exception {
        log.debug(tokenComboBox.getValue());
        String tokenid = tokenComboBox.getValue().split(":")[1].trim();
        String typeStr = (String) buySellTG.getSelectedToggle().getUserData().toString();

        byte[] pubKeyHash = Address.fromBase58(Main.params, addressComboBox.getValue()).getHash160();

        Coin coin = Main.calculateTotalUTXOList(pubKeyHash,
                typeStr.equals("sell") ? tokenid : NetworkParameters.BIGNETCOIN_TOKENID_STRING);
        long amount = Coin.parseCoinValue(this.amountTextField.getText());

        if (coin.getValue() < amount) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m"), Main.getText("o_c_d"));
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
        String ContextRoot = Main.getContextRoot();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("address", addressComboBox.getValue());
        // String tokenid = this.tokenComboBox.getValue().split(":")[1].trim();
        requestParam.put("tokenid", tokenid);
        requestParam.put("type", typeStr.equals("sell") ? 1 : 0);
        long price = Coin.parseCoinValue(this.limitTextField.getText());
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
        Tokens token_ = getTokensResponse.getToken();

        String url = token_.getUrl();
        OkHttp3Util.post(url + "/" + OrdermatchReqCmd.saveOrder.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        overlayUI.done();
    }

    public void search(ActionEvent event) {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("orderid", orderid4searchTextField.getText());
        requestParam.put("address", address4searchTextField.getText());
        requestParam.put("market", market4searchTextField.getText());

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

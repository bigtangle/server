/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import static com.google.common.base.Preconditions.checkState;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.RadioButton;
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
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.KeyCrypterScrypt;
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

    // public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {
        try {
            buyRadioButton.setUserData("buy");
            sellRadioButton.setUserData("sell");
            stateRB1.setUserData("publish");
            stateRB2.setUserData("match");
            stateRB3.setUserData("finish");
            initMarketComboBox();
            buySellTG.selectedToggleProperty().addListener((ov, o, n) -> {
                String temp = n.getUserData().toString();
                if ("sell".equalsIgnoreCase(temp)) {
                    try {
                        initComboBox(false);
                    } catch (Exception e) {

                    }
                } else {
                    try {
                        initComboBox(true);
                    } catch (Exception e) {

                    }
                }
            });
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            initComboBox(true);
            initTable(requestParam);
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void initTable(Map<String, Object> requestParam) throws Exception {
        if (requestParam.containsKey("state")) {
            String stateStr = (String) requestParam.get("state");
            OrderState orderState = OrderState.valueOf(stateStr);
            requestParam.put("state", orderState.ordinal());
        }
        ObservableList<Map<String, Object>> orderData = FXCollections.observableArrayList();

        String CONTEXT_ROOT = Main.getContextRoot();
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getMarkets",
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        final Map<String, Object> getTokensResult = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> tokensList = (List<Map<String, Object>>) getTokensResult.get("tokens");
        for (Map<String, Object> tokenResult : tokensList) {
            boolean asmarket = (boolean) tokenResult.get("asmarket");
            if (!asmarket) {
                continue;
            }
            String url = (String) tokenResult.get("url");
            try {
                response = OkHttp3Util.post(url + "/" + "getOrders",
                        Json.jsonmapper().writeValueAsString(requestParam).getBytes());
            } catch (Exception e) {
                continue;
            }
            final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
            List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("orders");
            if (list != null) {
                for (Map<String, Object> map : list) {
                    if ((Integer) map.get("type") == 1) {
                        map.put("type", Main.getText("SELL"));
                    } else {
                        map.put("type", Main.getText("BUY"));
                    }
                    int stateIndex = (int) map.get("state");
                    OrderState orderState = OrderState.values()[stateIndex];
                    map.put("state", Main.getText(orderState.name()));
                    Coin fromAmount = Coin.valueOf(Long.parseLong(map.get("price").toString()),
                            Utils.HEX.decode((String) map.get("tokenid")));
                    Coin toAmount = Coin.valueOf(Long.parseLong(map.get("amount").toString()),
                            Utils.HEX.decode((String) map.get("tokenid")));
                    map.put("price", fromAmount.toPlainString());
                    map.put("amount", toAmount.toPlainString());
                    orderData.add(map);
                }
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
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getMarkets",
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");
        for (Map<String, Object> map : list) {
            String tokenHex = (String) map.get("tokenid");
            String tokenname = (String) map.get("tokenname");
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
    @SuppressWarnings("unchecked")
    public void initComboBox(boolean buy) throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokensNoMarket",
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");

        if (!buy) {
            if (Main.validTokenSet != null && !Main.validTokenSet.isEmpty()) {
                for (String tokeninfo : Main.validTokenSet) {
                    if(!isSystemCoin(tokeninfo)) {
                    tokenData.add(tokeninfo);
                    }
                }
            }
        } else {
            for (Map<String, Object> map : list) {
                String tokenHex = (String) map.get("tokenid");
                boolean asmarket = (boolean) map.get("asmarket");
                if (asmarket)
                    continue;

                String tokenname = (String) map.get("tokenname");
                if(!isSystemCoin(tokenname + ":" + tokenHex)) {
                tokenData.add(tokenname + ":" + tokenHex);
                }

            }
        }

        tokenComboBox.setItems(tokenData);
        tokenComboBox.getSelectionModel().selectFirst();
        KeyParameter aeskey = null;
        // Main.initAeskey(aeskey);
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aeskey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(aeskey);
        ObservableList<String> addresses = FXCollections.observableArrayList();
        for (ECKey key : keys) {
            String address = key.toAddress(Main.params).toString();
            if (Main.validAddressSet.contains(address)) {
                addresses.add(address);
            }
        }
        addressComboBox.setItems(addresses);

    }

    public boolean isSystemCoin(String token) {
        return ("BIG:"+NetworkParameters.BIGNETCOIN_TOKENID_STRING).equals(token);

    }

    public void buy(ActionEvent event) throws Exception {

        try {
            buyDo(event);
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    @SuppressWarnings("unchecked")
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
        String resp = OkHttp3Util.postString(ContextRoot + "getTokenById",
                Json.jsonmapper().writeValueAsString(requestParam0));
        HashMap<String, Object> res = Json.jsonmapper().readValue(resp, HashMap.class);
        HashMap<String, Object> token_ = (HashMap<String, Object>) res.get("token");

        String url = (String) token_.get("url");
        OkHttp3Util.post(url + "/" + "saveOrder", Json.jsonmapper().writeValueAsString(requestParam));
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

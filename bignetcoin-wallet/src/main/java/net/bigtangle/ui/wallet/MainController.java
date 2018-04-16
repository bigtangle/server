/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.bigtangle.ui.wallet;

import static net.bigtangle.ui.wallet.Main.bitcoin;
import static net.bigtangle.ui.wallet.Main.params;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fxmisc.easybind.EasyBind;
import org.spongycastle.crypto.params.KeyParameter;

import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import net.bigtangle.core.Address;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.ui.wallet.controls.NotificationBarPane;
import net.bigtangle.ui.wallet.utils.BitcoinUIModel;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.easing.EasingMode;
import net.bigtangle.ui.wallet.utils.easing.ElasticInterpolator;
import net.bigtangle.utils.MapToBeanMapperUtil;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.OkHttp3Util;

/**
 * Gets created auto-magically by FXMLLoader via reflection. The widget fields
 * are set to the GUI controls they're named after. This class handles all the
 * updates and event handling for the main UI.
 */
public class MainController {
    public HBox controlsBox;
    public Label balance;
    public Button sendMoneyOutBtn;

    @FXML
    public TableView<CoinModel> coinTable;
    @FXML
    public TableColumn<CoinModel, String> valueColumn;
    @FXML
    public TableColumn<CoinModel, String> tokentypeColumn;

    @FXML
    public TableView<UTXOModel> utxoTable;
    @FXML
    public TableColumn<UTXOModel, String> balanceColumn;
    @FXML
    public TableColumn<UTXOModel, String> tokentypeColumnA;
    @FXML
    public TableColumn<UTXOModel, String> addressColumn;
    @FXML
    public TableColumn<UTXOModel, String> spendPendingColumn;

    @FXML
    public TextField Server;
    @FXML
    public TextField IPPort;

    @FXML
    public TextField addressTextField;

    private BitcoinUIModel model = new BitcoinUIModel();

    private NotificationBarPane.Item syncItem;

    @FXML
    public ToggleGroup toggleGroup;
    @FXML
    public ToggleButton enLocaleButton;
    @FXML
    public ToggleButton cnLocaleButton;

    @FXML
    public void initialize() {
        enLocaleButton.setUserData("en");
        cnLocaleButton.setUserData("cn");
        cnLocaleButton.setSelected(true);
        toggleGroup.selectedToggleProperty().addListener((v, oldt, newt) -> {
            Main.lang = newt.getUserData().toString();
            changeLocale();
        });
        Server.setText(Main.IpAddress);
        IPPort.setText(Main.port);
        initTableView();
    }

    public void changeLocale() {
    }

    @SuppressWarnings("unchecked")
    public void initTable(String addressString) throws Exception {
        Main.instance.getUtxoData().clear();
        Main.instance.getCoinData().clear();
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        bitcoin = new WalletAppKit(params, new File(Main.keyFileDirectory), Main.keyFilePrefix);
        KeyParameter aesKey = null;
        List<String> keyStrHex000 = new ArrayList<String>();
        if (addressString == null || "".equals(addressString.trim())) {
            for (ECKey ecKey : bitcoin.wallet().walletKeys(aesKey)) {
                keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
            }
        } else {
            keyStrHex000.add(Utils.HEX.encode(Address.fromBase58(Main.params, addressString).getHash160()));
        }

        String response = OkHttp3Util.post(CONTEXT_ROOT + "batchGetBalances",
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        if (data == null || data.isEmpty()) {
            return;
        }
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("outputs");
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Map<String, Object> object : list) {
            UTXO u = MapToBeanMapperUtil.parseUTXO(object);
            Coin c = u.getValue();
            String balance = c.toFriendlyString();
            byte[] tokenid = c.tokenid;
            String address = u.getAddress();
            boolean spendPending = u.isSpendPending();
            Main.instance.getUtxoData().add(new UTXOModel(balance, tokenid, address, spendPending));
        }
        list = (List<Map<String, Object>>) data.get("tokens");
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Map<String, Object> map : list) {
            Coin coin2 = MapToBeanMapperUtil.parseCoin(map);
            if (!coin2.isZero()) {
                Main.instance.getCoinData().add(new CoinModel(coin2.toFriendlyString(), coin2.tokenid));
            }
        }
    }

    public void initTableView() {
        try {

            initTable(addressTextField.getText());
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
        utxoTable.setItems(Main.instance.getUtxoData());
        coinTable.setItems(Main.instance.getCoinData());

        balanceColumn.setCellValueFactory(cellData -> cellData.getValue().balance());
        tokentypeColumnA.setCellValueFactory(cellData -> cellData.getValue().tokenid());
        addressColumn.setCellValueFactory(cellData -> cellData.getValue().address());
        spendPendingColumn.setCellValueFactory(cellData -> cellData.getValue().spendPending());
        addressColumn.setCellFactory(TextFieldTableCell.<UTXOModel>forTableColumn());

        valueColumn.setCellValueFactory(cellData -> cellData.getValue().value());
        tokentypeColumn.setCellValueFactory(cellData -> cellData.getValue().tokenid());

    }

    public void onBitcoinSetup() {
        model.setWallet(bitcoin.wallet());
        balance.textProperty().bind(
                EasyBind.map(model.balanceProperty(), coin -> MonetaryFormat.BTA.noCode().format(coin).toString()));
        // Don't let the user click send money when the wallet is empty.
        sendMoneyOutBtn.disableProperty().bind(model.balanceProperty().isEqualTo(Coin.ZERO));

        showBitcoinSyncMessage();

        model.syncProgressProperty().addListener(x -> {
            if (model.syncProgressProperty().get() >= 1.0) {
                readyToGoAnimation();
                if (syncItem != null) {
                    syncItem.cancel();
                    syncItem = null;
                }
            } else if (syncItem == null) {
                showBitcoinSyncMessage();
            }
        });
    }

    private void showBitcoinSyncMessage() {
        syncItem = Main.instance.notificationBar.pushItem("Synchronising with the Bitcoin network",
                model.syncProgressProperty());
    }

    public void sendMoneyOut(ActionEvent event) {

        Main.instance.overlayUI("send_money.fxml");
    }

    public void orders(ActionEvent event) {

        Main.instance.overlayUI("orders.fxml");
    }

    public void otherWallet(ActionEvent event) {

        initialize();
    }

    public void blockEvaluation(ActionEvent event) {

        Main.instance.overlayUI("blockEvaluation.fxml");
    }

    public void stockPublish(ActionEvent event) {

        Main.instance.overlayUI("stock.fxml");
    }

    public void eckeyList(ActionEvent event) {

        Main.instance.overlayUI("eckeys.fxml");
    }

    public void exchangeCoin(ActionEvent event) {

        Main.instance.overlayUI("exchange.fxml");
    }

    public void connectServer(ActionEvent event) {
        Main.instance.getUtxoData().clear();
        Main.instance.getCoinData().clear();
        Main.IpAddress = Server.getText();
        Main.port = IPPort.getText();
        try {
            initTableView();
            // GuiUtils.informationalAlert("set server info is ok", "", "");
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public void settingsClicked(ActionEvent event) {
        Main.OverlayUI<WalletSettingsController> screen = Main.instance.overlayUI("wallet_settings.fxml");
        screen.controller.initialize(null);
    }

    public void restoreFromSeedAnimation() {
        // Buttons slide out ...
        TranslateTransition leave = new TranslateTransition(Duration.millis(1200), controlsBox);
        leave.setByY(80.0);
        leave.play();
    }

    public void readyToGoAnimation() {
        // Buttons slide in and clickable address appears simultaneously.
        TranslateTransition arrive = new TranslateTransition(Duration.millis(1200), controlsBox);
        arrive.setInterpolator(new ElasticInterpolator(EasingMode.EASE_OUT, 1, 2));
        arrive.setToY(0.0);

    }

}

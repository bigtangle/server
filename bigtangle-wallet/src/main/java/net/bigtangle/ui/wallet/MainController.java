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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import net.bigtangle.core.Address;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.ui.wallet.controls.ClickableBitcoinAddress;
import net.bigtangle.ui.wallet.controls.NotificationBarPane;
import net.bigtangle.ui.wallet.utils.BitcoinUIModel;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.easing.EasingMode;
import net.bigtangle.ui.wallet.utils.easing.ElasticInterpolator;
import net.bigtangle.utils.MapToBeanMapperUtil;
import net.bigtangle.utils.OkHttp3Util;

/**
 * Gets created auto-magically by FXMLLoader via reflection. The widget fields
 * are set to the GUI controls they're named after. This class handles all the
 * updates and event handling for the main UI.
 */
public class MainController {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    public HBox controlsBox;
    public ClickableBitcoinAddress addressControl;

    @FXML
    public HBox buttonHBox;
    @FXML
    public AnchorPane serverPane;
    @FXML
    public AnchorPane searchPane;
    public Label balance;
    public Button sendMoneyOutBtn;
    @FXML
    public HBox passwordHBox;
    @FXML
    public PasswordField passwordField;

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
    private KeyParameter aesKey = null;

    @FXML
    public void initialize() {
        if (bitcoin.wallet().isEncrypted()) {
            searchPane.setVisible(false);
            serverPane.setVisible(false);
            buttonHBox.setVisible(false);
            passwordHBox.setVisible(true);
        } else {
            searchPane.setVisible(true);
            serverPane.setVisible(true);
            buttonHBox.setVisible(true);
            passwordHBox.setVisible(false);
        }
        Server.setText(Main.IpAddress);
        IPPort.setText(Main.port);
        initTableView();
    }

    @SuppressWarnings("unchecked")
    public void initTable(String addressString) throws Exception {
        String myPositvleTokens = Main.getString4file(Main.keyFileDirectory + Main.positiveFile);
        Main.instance.getUtxoData().clear();
        Main.instance.getCoinData().clear();
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        bitcoin = new WalletAppKit(params, new File(Main.keyFileDirectory), Main.keyFilePrefix);
        aesKey = null;
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }
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
        log.debug(response);
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        if (data == null || data.isEmpty()) {
            return;
        }
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("outputs");
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<String, String> hashNameMap = Main.getTokenHexNameMap();
        ObservableList<UTXOModel> subutxos = FXCollections.observableArrayList();
        for (Map<String, Object> object : list) {
            UTXO u = MapToBeanMapperUtil.parseUTXO(object);
            Coin c = u.getValue();
            String balance = c.toFriendlyString();
            byte[] tokenid = c.tokenid;
            String address = u.getAddress();

            Main.validAddressSet.clear();
            Main.validAddressSet.add(address);
            boolean spendPending = u.isSpendPending();
            if (myPositvleTokens != null && !"".equals(myPositvleTokens.trim()) && !myPositvleTokens.trim().isEmpty()) {
                if (myPositvleTokens.contains(Utils.HEX.encode(tokenid))) {
                    Main.instance.getUtxoData().add(new UTXOModel(balance, tokenid, address, spendPending,
                            Main.getString(hashNameMap.get(Utils.HEX.encode(tokenid)))));
                } else {
                    subutxos.add(new UTXOModel(balance, tokenid, address, spendPending,
                            Main.getString(hashNameMap.get(Utils.HEX.encode(tokenid)))));
                }

            }
            if (myPositvleTokens == null || myPositvleTokens.isEmpty() || "".equals(myPositvleTokens.trim()))
                Main.instance.getUtxoData().add(new UTXOModel(balance, tokenid, address, spendPending,
                        Main.getString(hashNameMap.get(Utils.HEX.encode(tokenid)))));
        }
        Main.instance.getUtxoData().addAll(subutxos);
        list = (List<Map<String, Object>>) data.get("tokens");
        if (list == null || list.isEmpty()) {
            return;
        }
        ObservableList<CoinModel> subcoins = FXCollections.observableArrayList();
        for (Map<String, Object> map : list) {
            Coin coin2 = MapToBeanMapperUtil.parseCoin(map);
            Main.validTokenMap.clear();
            Main.validTokenMap.put(Utils.HEX.encode(coin2.tokenid), true);

            if (!coin2.isZero()) {
                if (myPositvleTokens != null && !"".equals(myPositvleTokens.trim())
                        && !myPositvleTokens.trim().isEmpty()) {
                    if (myPositvleTokens.contains(Utils.HEX.encode(coin2.tokenid))) {
                        Main.instance.getCoinData().add(new CoinModel(coin2.toFriendlyString(), coin2.tokenid,
                                Main.getString(hashNameMap.get(Utils.HEX.encode(coin2.tokenid)))));

                    } else {
                        subcoins.add(new CoinModel(coin2.toFriendlyString(), coin2.tokenid,
                                Main.getString(hashNameMap.get(Utils.HEX.encode(coin2.tokenid)))));
                    }
                }
                if (myPositvleTokens == null || myPositvleTokens.isEmpty() || "".equals(myPositvleTokens.trim()))
                    Main.instance.getCoinData().add(new CoinModel(coin2.toFriendlyString(), coin2.tokenid,
                            Main.getString(hashNameMap.get(Utils.HEX.encode(coin2.tokenid)))));
            }
        }
        Main.instance.getCoinData().addAll(subcoins);
    }

    public void initTableView() {
        try {

            initTable(addressTextField.getText());
            utxoTable.setItems(Main.instance.getUtxoData());
            coinTable.setItems(Main.instance.getCoinData());

            balanceColumn.setCellValueFactory(cellData -> cellData.getValue().balance());
            tokentypeColumnA.setCellValueFactory(cellData -> cellData.getValue().tokenid());
            addressColumn.setCellValueFactory(cellData -> cellData.getValue().address());
            spendPendingColumn.setCellValueFactory(cellData -> cellData.getValue().spendPending());
            addressColumn.setCellFactory(TextFieldTableCell.<UTXOModel>forTableColumn());

            valueColumn.setCellValueFactory(cellData -> cellData.getValue().value());
            tokentypeColumn.setCellValueFactory(cellData -> cellData.getValue().tokenid());
            searchPane.setVisible(true);
            serverPane.setVisible(true);
            buttonHBox.setVisible(true);
            passwordHBox.setVisible(false);
            // bitcoin.wallet().isEncrypted()
            // if (!passwordHBox.isVisible()) {
            // utxoTable.setLayoutY(utxoTable.getLayoutY() -
            // passwordHBox.getHeight());
            // coinTable.setLayoutY(coinTable.getLayoutY() -
            // passwordHBox.getHeight());
            // }
        } catch (Exception e) {
            if (e instanceof ECKey.KeyIsEncryptedException) {
                searchPane.setVisible(false);
                serverPane.setVisible(false);
                buttonHBox.setVisible(false);
                passwordHBox.setVisible(true);

            } else {
                GuiUtils.crashAlert(e);
            }

        }

    }

    public void okPassword(ActionEvent event) {
        Main.password = passwordField.getText();

        initTableView();
    }

    public void sendMoneyOut(ActionEvent event) {
        Main.IpAddress = Server.getText();
        Main.port = IPPort.getText();
        initTableView();
        Main.instance.overlayUI("send_money.fxml");
    }

    public void modyfySIgn(ActionEvent event) {

        Main.instance.overlayUI("modify_sign.fxml");
    }

    public void orders(ActionEvent event) {
        Main.IpAddress = Server.getText();
        Main.port = IPPort.getText();
        Main.OverlayUI<OrderController> order = Main.instance.overlayUI("orders.fxml");
        if (utxoTable.getSelectionModel().getSelectedItem() != null) {
            String address = utxoTable.getSelectionModel().getSelectedItem().getAddress();
            order.controller.initAddress(address);
        }
    }

    public void otherWallet(ActionEvent event) {
        Main.IpAddress = Server.getText();
        Main.port = IPPort.getText();
        initialize();
    }

    public void blockEvaluation(ActionEvent event) {
        Main.IpAddress = Server.getText();
        Main.port = IPPort.getText();
        Main.instance.overlayUI("blockEvaluation.fxml");
    }

    public void stockPublish(ActionEvent event) {
        Main.IpAddress = Server.getText();
        Main.port = IPPort.getText();
        Main.instance.overlayUI("stock.fxml");
    }

    public void eckeyList(ActionEvent event) {
        Main.IpAddress = Server.getText();
        Main.port = IPPort.getText();
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
        Main.IpAddress = Server.getText();
        Main.port = IPPort.getText();
        Main.instance.overlayUI("wallet_set_password.fxml");
    }

    private void askForPasswordAndRetry() {
        Main.OverlayUI<WalletPasswordController> pwd = Main.instance.overlayUI("wallet_password.fxml");

        pwd.controller.aesKeyProperty().addListener((observable, old, cur) -> {

            Main.OverlayUI<MainController> screen = Main.instance.overlayUI("main.fxml");
            screen.controller.aesKey = cur;

            screen.controller.initTableView();
        });
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
        FadeTransition reveal = new FadeTransition(Duration.millis(1200), addressControl);
        reveal.setToValue(1.0);
        ParallelTransition group = new ParallelTransition(arrive, reveal);
        group.setDelay(NotificationBarPane.ANIM_OUT_DURATION);
        group.setCycleCount(1);
        group.play();
    }
}

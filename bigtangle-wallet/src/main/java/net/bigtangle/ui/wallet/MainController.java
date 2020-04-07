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

import static net.bigtangle.ui.wallet.Main.params;
import static net.bigtangle.ui.wallet.Main.walletAppKit;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
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
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Token;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.core.response.SettingResponse;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.ui.wallet.controls.ClickableBitcoinAddress;
import net.bigtangle.ui.wallet.controls.NotificationBarPane;
import net.bigtangle.ui.wallet.utils.BlockFormat;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.easing.EasingMode;
import net.bigtangle.ui.wallet.utils.easing.ElasticInterpolator;
import net.bigtangle.utils.MonetaryFormat;
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
    public TableColumn<UTXOModel, String> memoColumn;
    @FXML
    public TableColumn<UTXOModel, String> minimumsignColumn;
    @FXML
    public TextField Server;
    @FXML
    public TextField IPPort;

    @FXML
    public TextField addressTextField;

    @FXML
    public void initialize() {

        try {
            Main.addUsersettingData();
        } catch (Exception e) {
            // ignore
        }
        try {
            // TODO ask server, if client ming is allowed
            walletAppKit.wallet().setAllowClientMining(true);
            walletAppKit.wallet()
                    .setClientMiningAddress(walletAppKit.wallet().walletKeys(Main.getAesKey()).get(0).getPubKeyHash());
        } catch (Exception e) {
            // ignore
        }

        try {
            if (checkVersion()) {
                if (walletAppKit.wallet().isEncrypted()) {
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

                initTableView();
            }
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
            searchPane.setVisible(false);
            serverPane.setVisible(false);
            buttonHBox.setVisible(false);
            passwordHBox.setVisible(false);
        }

    }

    public boolean checkVersion() throws Exception {
        return true;
    }
    
    public void initTable(String addressString) throws Exception {

        Main.instance.getUtxoData().clear();
        Main.instance.getCoinData().clear();
        String CONTEXT_ROOT = Main.getContextRoot();

        List<String> keyStrHex000 = new ArrayList<String>();
        if (addressString == null || "".equals(addressString.trim())) {
            for (ECKey ecKey : walletAppKit.wallet().walletKeys(Main.getAesKey())) {
                keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
            }
        } else {
            keyStrHex000.add(Utils.HEX.encode(Address.fromBase58(Main.params, addressString).getHash160()));
        }

        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());
        log.debug(response);

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

        ObservableList<UTXOModel> subutxos = FXCollections.observableArrayList();
        Main.validTokenMap.clear();
        Main.validAddressSet.clear();

        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            Coin c = utxo.getValue();
            Token t = getBalancesResponse.getTokennames().get(Utils.HEX.encode(c.getTokenid()));
            if (c.isZero() || t == null) {
                continue;
            }
            String balance = MonetaryFormat.FIAT.noCode().format(c.getValue(), t.getDecimals());
            byte[] tokenid = c.getTokenid();
            String address = utxo.getAddress();
            String tokenname = t.getTokenname();
            String memo = utxo.memoToString();
            String minimumsign = Main.getString(utxo.getMinimumsign()).trim();
            String hashHex = utxo.getBlockHashHex();
            String hash = utxo.getHashHex();
            long outputindex = utxo.getIndex();
            String key = Utils.HEX.encode(tokenid);
            int signnum = Integer.parseInt(minimumsign);
            if (!utxo.isMultiSig()) {
                Main.validTokenSet.add(Main.getString(t.getTokenname() + ":" + key));
            }

            if (Main.validTokenMap.get(key) == null) {
                Set<String> addressList = new HashSet<String>();
                addressList.add(address);
                Main.validTokenMap.put(key, addressList);
            } else {
                Set<String> addressList = Main.validTokenMap.get(key);
                if (!addressList.contains(address)) {
                    addressList.add(address);
                }
                Main.validTokenMap.put(key, addressList);
            }
            if (utxo.getTokenId().trim().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)) {
                Main.validAddressSet.add(address);
            }

            boolean spendPending = utxo.isSpendPending();

            if (Main.isTokenInWatched(Utils.HEX.encode(tokenid))) {
                Main.instance.getUtxoData().add(new UTXOModel(balance, tokenid, address, spendPending, tokenname, memo,
                        minimumsign, hashHex, hash, outputindex));
            } else {
                subutxos.add(new UTXOModel(balance, tokenid, address, spendPending, tokenname, memo, minimumsign,
                        hashHex, hash, outputindex));
            }

        }
        Main.instance.getUtxoData().addAll(subutxos);

        ObservableList<CoinModel> subcoins = FXCollections.observableArrayList();

        for (Coin coin : getBalancesResponse.getBalance()) {
            Token t = getBalancesResponse.getTokennames().get(Utils.HEX.encode(coin.getTokenid()));

            if (!coin.isZero() && t != null) {

                if (Main.isTokenInWatched(Utils.HEX.encode(coin.getTokenid()))) {
                    Main.instance.getCoinData()
                            .add(new CoinModel(MonetaryFormat.FIAT.noCode().format(coin.getValue(), t.getDecimals()),
                                    coin.getTokenid(), t.getTokenname()));

                } else {
                    subcoins.add(new CoinModel(MonetaryFormat.FIAT.noCode().format(coin.getValue(), t.getDecimals()),
                            coin.getTokenid(), t.getTokenname()));
                }
            }
        }
        Main.instance.getCoinData().addAll(subcoins);
    }

    public void initTableView() {
        walletAppKit = new WalletAppKit(params, new File(Main.keyFileDirectory), Main.keyFilePrefix);

        if (walletAppKit.wallet().isEncrypted() && Main.getAesKey() ==null) {
            searchPane.setVisible(false);
            serverPane.setVisible(false);
            buttonHBox.setVisible(false);
            passwordHBox.setVisible(true);
        } else {
            try {

                try {
                    initTable(addressTextField.getText());
                } catch (Exception e) {
                    // TODO: handle exception
                }

                utxoTable.setItems(Main.instance.getUtxoData());
                coinTable.setItems(Main.instance.getCoinData());

                balanceColumn.setCellValueFactory(cellData -> cellData.getValue().balance());
                tokentypeColumnA.setCellValueFactory(cellData -> cellData.getValue().tokenid());
                addressColumn.setCellValueFactory(cellData -> cellData.getValue().address());
                memoColumn.setCellValueFactory(cellData -> cellData.getValue().memo());
                spendPendingColumn.setCellValueFactory(cellData -> cellData.getValue().spendPending());
                addressColumn.setCellFactory(TextFieldTableCell.<UTXOModel>forTableColumn());
                minimumsignColumn.setCellValueFactory(cellData -> cellData.getValue().minimumsign());

                valueColumn.setCellValueFactory(cellData -> cellData.getValue().value());
                tokentypeColumn.setCellValueFactory(cellData -> cellData.getValue().tokenid());
                searchPane.setVisible(true);
                serverPane.setVisible(true);
                buttonHBox.setVisible(true);
                passwordHBox.setVisible(false);
                // walletAppKit.wallet().isEncrypted()
                // if (!passwordHBox.isVisible()) {
                // utxoTable.setLayoutY(utxoTable.getLayoutY() -
                // passwordHBox.getHeight());
                // coinTable.setLayoutY(coinTable.getLayoutY() -
                // passwordHBox.getHeight());
                // }
            } catch (Exception e) {

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
        // Main.port = IPPort.getText();
        initTableView();
        Main.instance.overlayUI("send_money.fxml");
    }

    public void modyfySIgn(ActionEvent event) {

        Main.instance.overlayUI("modify_sign.fxml");
    }

    public void orders(ActionEvent event) {
        Main.IpAddress = Server.getText();
        // Main.port = IPPort.getText();
        Main.OverlayUI<OrderController> order = Main.instance.overlayUI("orders.fxml");
        if (utxoTable.getSelectionModel().getSelectedItem() != null) {
            String address = utxoTable.getSelectionModel().getSelectedItem().getAddress();
            String tokeninfo = utxoTable.getSelectionModel().getSelectedItem().getTokenid();
            if (tokeninfo.split(":")[1].trim().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)) {
                order.controller.initAddress(address);
            }

        }
    }

    public void otherWallet(ActionEvent event) {
        Main.IpAddress = Server.getText();
        // Main.port = IPPort.getText();
        initialize();
    }

    public void userdataList(ActionEvent event) {
        Main.IpAddress = Server.getText();
        // Main.port = IPPort.getText();
        Main.instance.overlayUI("userdata.fxml");
    }

    public void vos(ActionEvent event) {
        Main.IpAddress = Server.getText();
        // Main.port = IPPort.getText();
        Main.instance.overlayUI("vos.fxml");
    }

    public void blockEvaluation(ActionEvent event) {
        Main.IpAddress = Server.getText();
        // Main.port = IPPort.getText();
        Main.instance.overlayUI("blockEvaluation.fxml");
    }

    public void stockPublish(ActionEvent event) {
        Main.IpAddress = Server.getText();
        // Main.port = IPPort.getText();
        Main.instance.overlayUI("token.fxml");
    }

    public void eckeyList(ActionEvent event) {
        Main.IpAddress = Server.getText();
        // Main.port = IPPort.getText();
        Main.instance.overlayUI("eckeys.fxml");
    }

    public void exchangeCoin(ActionEvent event) {

        Main.instance.overlayUI("exchange.fxml");
    }

    public void connectServer(ActionEvent event) {
        Main.instance.getUtxoData().clear();
        Main.instance.getCoinData().clear();
        Main.IpAddress = Server.getText();
        // Main.port = IPPort.getText();
        try {
            initTableView();
            // GuiUtils.informationalAlert("set server info is ok", "", "");
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public void settingsClicked(ActionEvent event) {
        Main.IpAddress = Server.getText();
        // Main.port = IPPort.getText();
        Main.instance.overlayUI("wallet_set_password.fxml");
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

    public void showBlock(ActionEvent event) throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        UTXOModel utxoModel = utxoTable.getSelectionModel().getSelectedItem();
        if (utxoModel == null) {
            return;
        }
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("hashHex", Main.getString(utxoModel.getHashHex()));

        byte[] data = OkHttp3Util.postAndGetBlock(CONTEXT_ROOT + ReqCmd.getBlockByHash.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block re = Main.params.getDefaultSerializer().makeBlock(data);
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setHeight(800);
        alert.setWidth(800);
        alert.setTitle("");
        alert.setHeaderText(null);
        alert.setResizable(true);
        String blockinfo = BlockFormat.block2string(re, Main.params);
        alert.setContentText(blockinfo);

        alert.showAndWait();
    }
}

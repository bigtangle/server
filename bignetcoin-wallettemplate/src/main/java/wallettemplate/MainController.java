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

package wallettemplate;

import static wallettemplate.Main.bitcoin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.utils.MapToBeanMapperUtil;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.utils.OkHttp3Util;
import org.bitcoinj.wallet.DecryptingKeyBag;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.fxmisc.easybind.EasyBind;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.subgraph.orchid.TorClient;
import com.subgraph.orchid.TorInitializationListener;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import wallettemplate.controls.ClickableBitcoinAddress;
import wallettemplate.controls.NotificationBarPane;
import wallettemplate.utils.BitcoinUIModel;
import wallettemplate.utils.GuiUtils;
import wallettemplate.utils.easing.EasingMode;
import wallettemplate.utils.easing.ElasticInterpolator;

/**
 * Gets created auto-magically by FXMLLoader via reflection. The widget fields
 * are set to the GUI controls they're named after. This class handles all the
 * updates and event handling for the main UI.
 */
public class MainController {
    public HBox controlsBox;
    public Label balance;
    public Button sendMoneyOutBtn;
    public ClickableBitcoinAddress addressControl;
    @FXML
    public TableView<CoinModel> coinTable;
    @FXML
    public TableColumn<CoinModel, Number> valueColumn;
    @FXML
    public TableColumn<CoinModel, Number> tokentypeColumn;

    @FXML
    public TableView<UTXOModel> utxoTable;
    @FXML
    public TableColumn<UTXOModel, Number> balanceColumn;
    @FXML
    public TableColumn<UTXOModel, Number> tokentypeColumnA;
    @FXML
    public TableColumn<UTXOModel, String> addressColumn;

    @FXML
    public TextField IPAdress;
    @FXML
    public TextField IPPort;

    @FXML
    public TextField keyFileDirectory;

    private BitcoinUIModel model = new BitcoinUIModel();
    private NotificationBarPane.Item syncItem;
    private Main mainApp;
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    OkHttpClient client = new OkHttpClient();

    // Called by FXMLLoader.
    @SuppressWarnings("unchecked")
    @FXML
    public void initialize() {
        try {
            initTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
        addressControl.setOpacity(1.0);
    }

    public void initTableView() throws Exception {
        Main.instance.getUtxoData().clear();
        Main.instance.getCoinData().clear();
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        // ECKey ecKey = Main.bitcoin.wallet().currentReceiveKey();

        DecryptingKeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(bitcoin.wallet(), null);
        List<ECKey> keys = new ArrayList<ECKey>();
        for (ECKey key : bitcoin.wallet().getImportedKeys()) {
            ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
            // System.out.println("realKey, pubKey : " +
            // ecKey.getPublicKeyAsHex() + ", prvKey : " +
            // ecKey.getPrivateKeyAsHex());
            keys.add(ecKey);
        }
        for (DeterministicKeyChain chain : bitcoin.wallet().getKeyChainGroup().getDeterministicKeyChains()) {
            for (ECKey key : chain.getLeafKeys()) {
                ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
                // System.out.println("realKey, pubKey : " +
                // ecKey.getPublicKeyAsHex() + ", priKey : " +
                // ecKey.getPrivateKeyAsHex());
                keys.add(ecKey);
            }
        }

        for (ECKey ecKey : keys) {

            String response = OkHttp3Util.post(CONTEXT_ROOT + "getBalances", ecKey.getPubKeyHash());
            final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

            if (data != null && !data.isEmpty()) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("outputs");
                if (list != null && !list.isEmpty()) {
                    for (Map<String, Object> object : list) {
                        UTXO u = MapToBeanMapperUtil.parseUTXO(object);
                        Coin c = u.getValue();
                        long balance = c.getValue();
                        long tokenid = c.tokenid;
                        String address = u.getAddress();
                        if (!u.isSpent()) {
                            Main.instance.getUtxoData().add(new UTXOModel(balance, tokenid, address));
                        }

                    }
                }
                list = (List<Map<String, Object>>) data.get("tokens");
                if (list != null && !list.isEmpty()) {
                    for (Map<String, Object> map : list) {
                        Coin coin2 = MapToBeanMapperUtil.parseCoin(map);
                        if (!coin2.isZero()) {
                            Main.instance.getCoinData().add(new CoinModel(coin2.value, coin2.tokenid));

                        }
                    }
                }
            }
        }
        utxoTable.setItems(Main.instance.getUtxoData());
        coinTable.setItems(Main.instance.getCoinData());

        balanceColumn.setCellValueFactory(cellData -> cellData.getValue().balance());
        tokentypeColumnA.setCellValueFactory(cellData -> cellData.getValue().tokenid());
        addressColumn.setCellValueFactory(cellData -> cellData.getValue().address());

        addressColumn.setCellFactory(TextFieldTableCell.<UTXOModel>forTableColumn());

        valueColumn.setCellValueFactory(cellData -> cellData.getValue().value());
        tokentypeColumn.setCellValueFactory(cellData -> cellData.getValue().tokenid());

    }

    public void onBitcoinSetup() {
        model.setWallet(bitcoin.wallet());
        addressControl.addressProperty().bind(model.addressProperty());
        balance.textProperty().bind(
                EasyBind.map(model.balanceProperty(), coin -> MonetaryFormat.BTC.noCode().format(coin).toString()));
        // Don't let the user click send money when the wallet is empty.
        sendMoneyOutBtn.disableProperty().bind(model.balanceProperty().isEqualTo(Coin.ZERO));

        TorClient torClient = Main.bitcoin.peerGroup().getTorClient();
        if (torClient != null) {
            SimpleDoubleProperty torProgress = new SimpleDoubleProperty(-1);
            String torMsg = "Initialising Tor";
            syncItem = Main.instance.notificationBar.pushItem(torMsg, torProgress);
            torClient.addInitializationListener(new TorInitializationListener() {
                @Override
                public void initializationProgress(String message, int percent) {
                    Platform.runLater(() -> {
                        syncItem.label.set(torMsg + ": " + message);
                        torProgress.set(percent / 100.0);
                    });
                }

                @Override
                public void initializationCompleted() {
                    Platform.runLater(() -> {
                        syncItem.cancel();
                        showBitcoinSyncMessage();
                    });
                }
            });
        } else {
            showBitcoinSyncMessage();
        }
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
        // Hide this UI and show the send money UI. This UI won't be clickable
        // until the user dismisses send_money.
        Main.instance.overlayUI("send_money.fxml");
    }

    public void setKeyFilePath(ActionEvent event) {
        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        // final Desktop desktop = Desktop.getDesktop();
        if (file != null) {
            // try {
            // desktop.open(file);
            keyFileDirectory.setText(file.getAbsolutePath());
            // } catch (IOException e) {

            // GuiUtils.crashAlert(e);
            // }

            Main.keyFileDirectory = file.getParent()+"/";
            String filename = file.getName();

            Main.keyFilePrefix = filename.contains(".") ? filename.substring(0, filename.lastIndexOf(".")) : filename;

            try {
                Main.instance.getUtxoData().clear();
                Main.instance.getCoinData().clear();
                initTableView();
                GuiUtils.informationalAlert("set key file is ok", "", "");

            } catch (Exception e) {
                GuiUtils.crashAlert(e);
            }
        }

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
        Main.IpAddress = IPAdress.getText();
        Main.port = IPPort.getText();
        try {
            initTableView();
            GuiUtils.informationalAlert("set server info is ok", "", "");
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
        FadeTransition reveal = new FadeTransition(Duration.millis(1200), addressControl);
        reveal.setToValue(1.0);
        ParallelTransition group = new ParallelTransition(arrive, reveal);
        group.setDelay(NotificationBarPane.ANIM_OUT_DURATION);
        group.setCycleCount(1);
        group.play();
    }

    public DownloadProgressTracker progressBarUpdater() {
        return model.getDownloadProgressTracker();
    }

}

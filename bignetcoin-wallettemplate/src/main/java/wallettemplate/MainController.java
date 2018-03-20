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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.utils.MonetaryFormat;
import org.fxmisc.easybind.EasyBind;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.subgraph.orchid.TorClient;
import com.subgraph.orchid.TorInitializationListener;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import wallettemplate.controls.ClickableBitcoinAddress;
import wallettemplate.controls.NotificationBarPane;
import wallettemplate.utils.BitcoinUIModel;
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
    public TableColumn<CoinModel, String> tokentypeColumn;

    @FXML
    public TableView<UTXOModel> utxoTable;
    @FXML
    public TableColumn<UTXOModel, Number> balanceColumn;
    @FXML
    public TableColumn<UTXOModel, String> tokentypeColumnA;
    @FXML
    public TableColumn<UTXOModel, String> addressColumn;

    @FXML
    public TextField IPAdress;
    @FXML
    public TextField IPPort;

    private BitcoinUIModel model = new BitcoinUIModel();
    private NotificationBarPane.Item syncItem;
    private Main mainApp;
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    OkHttpClient client = new OkHttpClient();

    String post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    // Called by FXMLLoader.
    @FXML
    public void initialize() throws Exception {
        String addr = "030d8952f6c079f60cd26eb3ba83cf16a81c51fc8e47b767721fa38b5e20092a75";
        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getBalances");
        request.put("addresses", new String[] { addr });
        request.put("threshold", 100);

        String response = post("http://localhost:14265", Json.jsonmapper().writeValueAsString(request));
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

        if (data != null && !data.isEmpty()) {
            List list = (List) data.get("outputs");
            if (list != null && !list.isEmpty()) {
                for (Object object : list) {
                    Map<String, Object> utxo = (Map<String, Object>) object;
                    double balance = ((Coin) utxo.get("value")).getValue();
                    int tokenid = (int) (utxo.get("tokenid"));
                    String address = (String) (utxo.get("address"));
                    Main.instance.getUtxoData().add(new UTXOModel(0, tokenid, address));
                }
            }
            Map map = (Map) data.get("tokens");
            if (map != null && !map.isEmpty()) {
                for (Object object : map.values()) {
                    Map coins = (Map) object;

                }
            }
        }
        utxoTable.setItems(Main.instance.getUtxoData());
        coinTable.setItems(Main.instance.getCoinData());

        balanceColumn.setCellValueFactory(cellData -> cellData.getValue().balance());
        tokentypeColumnA.setCellValueFactory(cellData -> cellData.getValue().tokentype());
        addressColumn.setCellValueFactory(cellData -> cellData.getValue().address());

        valueColumn.setCellValueFactory(cellData -> cellData.getValue().value());
        tokentypeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getTokenid() == NetworkParameters.BIGNETCOIN_TOKENID ? "bignetcoin" : "other"));
        addressControl.setOpacity(0.0);
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

    public void stockPublish(ActionEvent event) {

        Main.instance.overlayUI("stock.fxml");
    }

    public void exchangeCoin(ActionEvent event) {

        Main.instance.overlayUI("exchange.fxml");
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

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

import static net.bigtangle.ui.wallet.utils.GuiUtils.blurIn;
import static net.bigtangle.ui.wallet.utils.GuiUtils.blurOut;
import static net.bigtangle.ui.wallet.utils.GuiUtils.checkGuiThread;
import static net.bigtangle.ui.wallet.utils.GuiUtils.explodeOut;
import static net.bigtangle.ui.wallet.utils.GuiUtils.fadeIn;
import static net.bigtangle.ui.wallet.utils.GuiUtils.fadeOutAndRemove;
import static net.bigtangle.ui.wallet.utils.GuiUtils.zoomIn;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.annotation.Nullable;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.UnitTestParams;
import net.bigtangle.ui.wallet.controls.NotificationBarPane;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.TextFieldValidator;
import net.bigtangle.utils.BriefLogFormatter;
import net.bigtangle.utils.Threading;
import net.bigtangle.wallet.DeterministicSeed;

public class Main extends Application {
    public static NetworkParameters params = UnitTestParams.get();
    public static final String APP_NAME = "Wallet";
    private static final String WALLET_FILE_NAME = APP_NAME.replaceAll("[^a-zA-Z0-9.-]", "_") + "-"
            + params.getPaymentProtocolId();
    public static String keyFileDirectory = ".";
    public static String keyFilePrefix = "bignetcoin";
    public static WalletAppKit bitcoin = new WalletAppKit(params, new File(Main.keyFileDirectory), Main.keyFilePrefix);
    public static Main instance;

    private StackPane uiStack;
    private Pane mainUI;
    public MainController controller;
    public NotificationBarPane notificationBar;
    public Stage mainWindow;
    private ObservableList<CoinModel> coinData = FXCollections.observableArrayList();
    private ObservableList<UTXOModel> utxoData = FXCollections.observableArrayList();

    public static String IpAddress = "bigtangle.net";
    public static String port = "8088";

    @Override
    public void start(Stage mainWindow) throws Exception {
        try {

            realStart(mainWindow);
        } catch (Throwable e) {
            GuiUtils.crashAlert(e);
            // throw e;
        }
    }

    private void realStart(Stage mainWindow) throws IOException {
        this.mainWindow = mainWindow;
        instance = this;
        // Show the crash dialog for any exceptions that we don't handle and
        // that hit the main loop.
        GuiUtils.handleCrashesOnThisThread();

        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            // We could match the Mac Aqua style here, except that (a) Modena
            // doesn't look that bad, and (b)
            // the date picker widget is kinda broken in AquaFx and I can't be
            // bothered fixing it.
            // AquaFx.style();
        }

        // Load the GUI. The MainController class will be automagically created
        // and wired up.
        URL location = getClass().getResource("main.fxml");
        FXMLLoader loader = new FXMLLoader(location);
        mainUI = loader.load();
        controller = loader.getController();
        // Configure the window with a StackPane so we can overlay things on top
        // of the main UI, and a
        // NotificationBarPane so we can slide messages and progress bars in
        // from the bottom. Note that
        // ordering of the construction and connection matters here, otherwise
        // we get (harmless) CSS error
        // spew to the logs.
        notificationBar = new NotificationBarPane(mainUI);
        mainWindow.setTitle(APP_NAME);
        uiStack = new StackPane();
        Scene scene = new Scene(uiStack);
        TextFieldValidator.configureScene(scene); // Add CSS that we need.
        scene.getStylesheets().add(getClass().getResource("wallet.css").toString());
        uiStack.getChildren().add(notificationBar);
        mainWindow.setScene(scene);

        // Make log output concise.
        BriefLogFormatter.init();
        // Tell bitcoinj to run event handlers on the JavaFX UI thread. This
        // keeps things simple and means
        // we cannot forget to switch threads when adding event handlers.
        // Unfortunately, the DownloadListener
        // we give to the app kit is currently an exception and runs on a
        // library thread. It'll get fixed in
        // a future version.
        Threading.USER_THREAD = Platform::runLater;
        // Create the app kit. It won't do any heavyweight initialization until
        // after we start it.
        setupWalletKit(null);

        mainWindow.show();

        WalletSetPasswordController.estimateKeyDerivationTimeMsec();
 
    }

    public void setupWalletKit(@Nullable DeterministicSeed seed) {
        // If seed is non-null it means we are restoring from backup.
        bitcoin.startAsync();
        if (seed != null)
            bitcoin.restoreWalletFromSeed(seed);
    }

    private Node stopClickPane = new Pane();

    public class OverlayUI<T> {
        public Node ui;
        public T controller;

        public OverlayUI(Node ui, T controller) {
            this.ui = ui;
            this.controller = controller;
        }

        public void show() {
            checkGuiThread();
            if (currentOverlay == null) {
                uiStack.getChildren().add(stopClickPane);
                uiStack.getChildren().add(ui);
                blurOut(mainUI);
                // darken(mainUI);
                ui.setOpacity(1.0);
                fadeIn(ui);
                zoomIn(ui);
            } else {
                // Do a quick transition between the current overlay and the
                // next.
                // Bug here: we don't pay attention to changes in
                // outsideClickDismisses.
                explodeOut(currentOverlay.ui);
                fadeOutAndRemove(uiStack, currentOverlay.ui);
                uiStack.getChildren().add(ui);
                ui.setOpacity(1.0);
                fadeIn(ui, 100);
                zoomIn(ui, 100);
            }
            currentOverlay = this;
        }

        public void outsideClickDismisses() {
            stopClickPane.setOnMouseClicked((ev) -> done());
        }

        public void done() {
            checkGuiThread();
            if (ui == null)
                return; // In the middle of being dismissed and got an extra
                        // click.
            explodeOut(ui);
            fadeOutAndRemove(uiStack, ui, stopClickPane);
            blurIn(mainUI);
            // undark(mainUI);
            this.ui = null;
            this.controller = null;
            currentOverlay = null;
        }
    }

    @Nullable
    private OverlayUI currentOverlay;

    public <T> OverlayUI<T> overlayUI(Node node, T controller) {
        checkGuiThread();
        OverlayUI<T> pair = new OverlayUI<T>(node, controller);
        // Auto-magically set the overlayUI member, if it's there.
        try {
            controller.getClass().getField("overlayUI").set(controller, pair);
        } catch (IllegalAccessException | NoSuchFieldException ignored) {
        }
        pair.show();
        return pair;
    }

    /**
     * Loads the FXML file with the given name, blurs out the main UI and puts
     * this one on top.
     */
    public <T> OverlayUI<T> overlayUI(String name) {
        try {
            checkGuiThread();
            // Load the UI from disk.
            URL location = GuiUtils.getResource(name);
            FXMLLoader loader = new FXMLLoader(location);
            Pane ui = loader.load();
            T controller = loader.getController();
            OverlayUI<T> pair = new OverlayUI<T>(ui, controller);
            // Auto-magically set the overlayUI member, if it's there.
            try {
                if (controller != null)
                    controller.getClass().getField("overlayUI").set(controller, pair);
            } catch (IllegalAccessException | NoSuchFieldException ignored) {
                ignored.printStackTrace();
            }
            pair.show();
            return pair;
        } catch (IOException e) {
            throw new RuntimeException(e); // Can't happen.
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    public ObservableList<CoinModel> getCoinData() {
        return coinData;
    }

    public void setCoinData(ObservableList<CoinModel> coinData) {
        this.coinData = coinData;
    }

    public ObservableList<UTXOModel> getUtxoData() {
        return utxoData;
    }

    public void setUtxoData(ObservableList<UTXOModel> utxoData) {
        this.utxoData = utxoData;
    }
}

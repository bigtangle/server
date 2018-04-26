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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.Nullable;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.spongycastle.crypto.params.KeyParameter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ser.std.StringSerializer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.UTXO;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.UnitTestParams;
import net.bigtangle.ui.wallet.controls.NotificationBarPane;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.TextFieldValidator;
import net.bigtangle.utils.BriefLogFormatter;
import net.bigtangle.utils.MapToBeanMapperUtil;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.Threading;
import net.bigtangle.wallet.DeterministicSeed;

public class Main extends Application {
    public static NetworkParameters params = UnitTestParams.get();
    public static final String APP_NAME = "Wallet";

    public static String keyFileDirectory = ".";
    public static String keyFilePrefix = "bignetcoin";
    public static WalletAppKit bitcoin;
    public static Main instance;

    private StackPane uiStack;
    private Pane mainUI;
    public MainController controller;
    public NotificationBarPane notificationBar;
    public Stage mainWindow;
    private ObservableList<CoinModel> coinData = FXCollections.observableArrayList();
    private ObservableList<UTXOModel> utxoData = FXCollections.observableArrayList();

    public static String IpAddress = "cn.server.bigtangle.net";
    public static String port = "8088";
    public static FXMLLoader loader;

    public static String lang = "en";
    public static String password = "";
    public static int numberOfEmptyBlocks = 3;
    public static boolean emptyBlocks = false;

    public String blockTopic = "bigtangle";
    public String kafka = "cn.kafka.bigtangle.net:9092";

    @Override
    public void start(Stage mainWindow) throws Exception {
        try {

            realStart(mainWindow, Main.lang);
        } catch (Throwable e) {
            GuiUtils.crashAlert(e);
            // throw e;
        }
    }

    public static String getTextt(String s) {
        ResourceBundle rb = ResourceBundle.getBundle("net.bigtangle.ui.wallet.message", Locale.getDefault());
        if ("en".equalsIgnoreCase(lang) || "de".equalsIgnoreCase(lang)) {
            rb = ResourceBundle.getBundle("net.bigtangle.ui.wallet.message_en", Locale.ENGLISH);
        } else {
            rb = ResourceBundle.getBundle("net.bigtangle.ui.wallet.message", Locale.CHINESE);
        }
        return rb.getString(s);

    }

    public static void initAeskey(KeyParameter aesKey) {
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }
    }

    public void realStart(Stage mainWindow, String language) throws IOException {

        this.mainWindow = mainWindow;
        // mainWindow.setMaximized(true);
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
        loader = new FXMLLoader(location);
        String resourceFile = "net.bigtangle.ui.wallet.message";
        lang = language;
        Locale locale = Locale.CHINESE;
        if ("en".equals(lang) || "de".equals(lang)) {
            resourceFile += "_en";
            locale = Locale.ENGLISH;
        }
        ResourceBundle resourceBundle = ResourceBundle.getBundle(resourceFile, locale);
        loader.setResources(resourceBundle);
        bitcoin = new WalletAppKit(params, new File(Main.keyFileDirectory), Main.keyFilePrefix);

        // set local kafka to send
        if (!Locale.CHINESE.equals(locale)) {
            kafka = "de.kafka.bigtangle.net:9092";
            IpAddress = "de.server.bigtangle.net";
        }
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

    public static void addAddress2file(String name, String address) throws Exception {
        String homedir = Main.keyFileDirectory;
        File addressFile = new File(homedir + "/addresses.txt");
        if (!addressFile.exists()) {
            addressFile.createNewFile();
        }
        BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(addressFile, true), "UTF-8"));
        out.write(name + "," + address);
        out.newLine();
        out.flush();

        out.close();
    }

    public static List<String> initAddress4file() throws Exception {
        String homedir = Main.keyFileDirectory;
        File addressFile = new File(homedir + "/addresses.txt");
        if (!addressFile.exists()) {
            addressFile.createNewFile();
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(addressFile), "UTF-8"));
        String str = "";
        List<String> addressList = new ArrayList<String>();
        addressList.clear();
        addressList = new ArrayList<String>();
        while ((str = in.readLine()) != null) {
            addressList.add(str);
        }
        in.close();
        return addressList;
    }

    public void setupWalletKit(@Nullable DeterministicSeed seed) {

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
    private OverlayUI<?> currentOverlay;

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
            String resourceFile = "net.bigtangle.ui.wallet.message";
            Locale locale = Locale.CHINESE;
            if ("en".equals(Main.lang) || "de".equals(Main.lang)) {
                resourceFile += "_en";
                locale = Locale.ENGLISH;
            }
            ResourceBundle resourceBundle = ResourceBundle.getBundle(resourceFile, locale);
            loader.setResources(resourceBundle);
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
        String systemLang = Locale.getDefault().getLanguage();
        // String systemName = System.getProperty("os.name").toLowerCase();
        if (args == null || args.length == 0) {
            lang = systemLang;
            keyFileDirectory = System.getProperty("user.home");
            keyFilePrefix = System.getProperty("user.name");
        }
        if (args != null && args.length == 2) {
            lang = args[0];
            keyFileDirectory = new File(args[1]).getParent();
            String temp = new File(args[1]).getName();
            if (temp.contains(".")) {
                keyFilePrefix = temp.substring(0, temp.lastIndexOf("."));
            } else {
                keyFilePrefix = temp;
            }

        }
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

    public static void sentEmpstyBlock(int number) {
        if (emptyBlocks) {
            Runnable r = () -> {
                for (int i = 0; i < number; i++) {
                    try {
                        sentEmpstyBlock();
                        System.out.println("empty block " + i);
                    } catch (Exception e) {
                        // Ignore
                        e.printStackTrace();
                    }
                }
            };
            Platform.runLater(r);
            // Threading.USER_THREAD.execute(r);
        }
    }

    public static String sentEmpstyBlock() throws JsonProcessingException, Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = params.getDefaultSerializer().makeBlock(data);
        rollingBlock.solve();

        return OkHttp3Util.post(CONTEXT_ROOT + "saveBlock", rollingBlock.bitcoinSerialize());

    }

    @SuppressWarnings("unchecked")
    public static List<UTXO> getUTXOWithPubKeyHash(byte[] pubKeyHash, byte[] tokenid) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        String ContextRoot = "http://" + Main.IpAddress + ":" + Main.port + "/";
        String response = OkHttp3Util.post(ContextRoot + "getOutputs", pubKeyHash);
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        if (data == null || data.isEmpty()) {
            return listUTXO;
        }
        List<Map<String, Object>> outputs = (List<Map<String, Object>>) data.get("outputs");
        if (outputs == null || outputs.isEmpty()) {
            return listUTXO;
        }
        for (Map<String, Object> object : outputs) {
            UTXO utxo = MapToBeanMapperUtil.parseUTXO(object);
            if (!Arrays.equals(utxo.getTokenid(), tokenid)) {
                continue;
            }
            if (utxo.getValue().getValue() > 0) {
                listUTXO.add(utxo);
            }
        }
        return listUTXO;
    }

    public static Coin calculateTotalUTXOList(byte[] pubKeyHash, byte[] tokenid) throws Exception {
        List<UTXO> listUTXO = getUTXOWithPubKeyHash(pubKeyHash, tokenid);
        Coin amount = Coin.valueOf(0, tokenid);
        if (listUTXO == null || listUTXO.isEmpty()) {
            return amount;
        }
        for (UTXO utxo : listUTXO) {
            amount = amount.add(utxo.getValue());
        }
        return amount;
    }

    public boolean sendMessage(byte[] data) throws InterruptedException, ExecutionException {
        return sendMessage(data, this.blockTopic, this.kafka);
    }

    public boolean sendMessage(byte[] data, String topic, String bootstrapServers)
            throws InterruptedException, ExecutionException {
        final String key = UUID.randomUUID().toString();
        KafkaProducer<String, byte[]> messageProducer = new KafkaProducer<String, byte[]>(
                producerConfig(bootstrapServers, true));
        ProducerRecord<String, byte[]> producerRecord = null;
        producerRecord = new ProducerRecord<String, byte[]>(topic, key, data);
        final Future<RecordMetadata> result = messageProducer.send(producerRecord);
        RecordMetadata mdata = result.get();
        // log.debug(" sendMessage "+ key );
        messageProducer.close();
        return mdata != null;

    }

    public Properties producerConfig(String bootstrapServers, boolean binaryMessageKey) {
        Properties producerConfig = new Properties();
        producerConfig.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerConfig.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.setProperty(ProducerConfig.RETRIES_CONFIG, "0");
        producerConfig.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                binaryMessageKey ? ByteArraySerializer.class.getName() : StringSerializer.class.getName());
        producerConfig.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerConfig.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        // producerConfig.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        // KafkaAvroSerializer.class.getName());
        // producerConfig.setProperty(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
        // configuration.getSchemaRegistryUrl());
        return producerConfig;
    }

}

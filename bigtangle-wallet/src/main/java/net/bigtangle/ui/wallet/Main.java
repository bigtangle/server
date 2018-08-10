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
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Strings;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Contact;
import net.bigtangle.core.ContactInfo;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.MyHomeAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.ScriptException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UploadfileInfo;
import net.bigtangle.core.UserSettingData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.WatchedInfo;
import net.bigtangle.core.http.server.resp.GetOutputsResponse;
import net.bigtangle.core.http.server.resp.GetTokensResponse;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.params.UnitTestParams;
import net.bigtangle.script.Script;
import net.bigtangle.ui.wallet.controls.NotificationBarPane;
import net.bigtangle.ui.wallet.utils.FileUtil;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.TextFieldValidator;
import net.bigtangle.utils.BriefLogFormatter;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.Threading;
import net.bigtangle.wallet.DeterministicSeed;

public class Main extends Application {
    public static NetworkParameters params = UnitTestParams.get();
    public static final String APP_NAME = "Bigtangle  Wallet Test " + Main.version;
    public static final String version = "0.3.0";
    public static String keyFileDirectory = ".";
    public static String keyFilePrefix = "bigtangle";
    public static WalletAppKit bitcoin;
    public static Main instance;

    private StackPane uiStack;
    private Pane mainUI;
    public MainController controller;
    public NotificationBarPane notificationBar;
    public Stage mainWindow;
    private ObservableList<CoinModel> coinData = FXCollections.observableArrayList();
    private ObservableList<UTXOModel> utxoData = FXCollections.observableArrayList();

    public static String IpAddress = "";
    public static String versionserver = "https://bigtangle.org/";
    public static FXMLLoader loader;

    public static String lang = "en";
    public static String password = "";
    public static int numberOfEmptyBlocks = 3;
    public static boolean emptyBlocks = true;
    public static Map<String, Set<String>> validTokenMap = new HashMap<String, Set<String>>();
    public static Set<String> validAddressSet = new HashSet<String>();
    public static Set<String> validTokenSet = new HashSet<String>();
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static List<String> userdataList = new ArrayList<String>();
    // TODO as instance variable, not static
    public static TokenInfo tokenInfo;

    @Override
    public void start(Stage mainWindow) throws Exception {
        try {

            realStart(mainWindow, Main.lang);
        } catch (Throwable e) {
            GuiUtils.crashAlert(e);
            // throw e;
        }
    }

    public static String getText(String s) {
        ResourceBundle rb = ResourceBundle.getBundle("net.bigtangle.ui.wallet.message", Locale.getDefault());
        if ("en".equalsIgnoreCase(lang) || "de".equalsIgnoreCase(lang)) {
            rb = ResourceBundle.getBundle("net.bigtangle.ui.wallet.message_en", Locale.ENGLISH);
        } else {
            rb = ResourceBundle.getBundle("net.bigtangle.ui.wallet.message", Locale.CHINESE);
        }
        return rb.getString(s);

    }

    public static void addToken(String contextRoot, String tokenname, String tokenid, String type) throws Exception {
        String domain = type;
        long blocktype = Block.BLOCKTYPE_USERDATA;
        if (DataClassName.SERVERURL.name().equals(type)) {
            type = DataClassName.WATCHED.name();
            // blocktype = NetworkParameters.BLOCKTYPE_USERDATA_SERVERURL;
        }
        if (DataClassName.LANG.name().equals(type)) {
            type = DataClassName.WATCHED.name();
            // blocktype = NetworkParameters.BLOCKTYPE_USERDATA_LANG;
        }
        if (DataClassName.TOKEN.name().equals(type)) {
            type = DataClassName.WATCHED.name();
            // blocktype = NetworkParameters.BLOCKTYPE_USERDATA_TOKEN;
        }
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = Main.params.getDefaultSerializer().makeBlock(data);
        block.setBlockType(blocktype);
        ECKey pubKeyTo = null;

        KeyParameter aesKey = null;
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> issuedKeys = Main.bitcoin.wallet().walletKeys(aesKey);

        if (Main.bitcoin.wallet().isEncrypted()) {
            pubKeyTo = issuedKeys.get(0);
        } else {
            pubKeyTo = Main.bitcoin.wallet().currentReceiveKey();
        }

        Transaction coinbase = new Transaction(Main.params);
        UserSettingData userSettingData = new UserSettingData();
        userSettingData.setDomain(domain);
        userSettingData.setKey(tokenid);
        userSettingData.setValue(tokenname);
        WatchedInfo watchedInfo = (WatchedInfo) getUserdata(DataClassName.WATCHED.name());
        if (watchedInfo == null)
            return;
        List<UserSettingData> userSettingDatas = watchedInfo.getUserSettingDatas();
        List<UserSettingData> temps = new ArrayList<>();

        if (userSettingDatas != null && !userSettingDatas.isEmpty()) {
            for (UserSettingData userSettingData2 : userSettingDatas) {
                if (DataClassName.SERVERURL.name().equals(userSettingData2.getDomain())
                        || DataClassName.LANG.name().equals(userSettingData2.getDomain())) {
                    if (domain.equals(userSettingData2.getDomain())) {
                        userSettingData2.setValue(tokenname);
                    }

                }
                temps.add(userSettingData2);

            }
            watchedInfo.setUserSettingDatas(temps);
        } else {
            watchedInfo.getUserSettingDatas().add(userSettingData);
        }

        coinbase.setDataClassName(type);
        coinbase.setData(watchedInfo.toByteArray());

        Sha256Hash sighash = coinbase.getHash();

        ECKey.ECDSASignature party1Signature = pubKeyTo.sign(sighash, aesKey);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setAddress(pubKeyTo.toAddress(Main.params).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(pubKeyTo.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        coinbase.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignBies));

        block.addTransaction(coinbase);
        block.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
        if (DataClassName.WATCHED.name().equals(type)) {
            byte[] buf = block.bitcoinSerialize();
            if (buf == null) {
                return;
            }

            File file = new File(Main.keyFileDirectory + "/usersetting.block");
            if (file.exists()) {
                file.delete();
            }
            FileUtil.writeFile(file, buf);
        }

    }

    /**
     * 
     * @param version1
     * @param version2
     * @return
     */
    public static int compareVersion(String version1, String version2) throws Exception {
        if (version1 == null || version2 == null) {
            throw new Exception("compareVersion error:illegal params.");
        }
        String[] versionArray1 = version1.split("\\.");
        String[] versionArray2 = version2.split("\\.");
        int idx = 0;
        int minLength = Math.min(versionArray1.length, versionArray2.length);
        int diff = 0;
        while (idx < minLength && (diff = versionArray1[idx].length() - versionArray2[idx].length()) == 0
                && (diff = versionArray1[idx].compareTo(versionArray2[idx])) == 0) {
            ++idx;
        }
        diff = (diff != 0) ? diff : versionArray1.length - versionArray2.length;
        return diff;
    }

    public static String getString(Object object) {

        return object == null ? " " : String.valueOf(object).trim();

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

            if ("".equals(IpAddress))
                IpAddress = "https://bigtangle.de";
        } else {

            if ("".equals(IpAddress))
                IpAddress = "https://bigtangle.org";

        }

        addUsersettingData();

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
        mainWindow.getIcons().add(new Image(getClass().getResourceAsStream("bigtangle_logo_plain.png")));

        mainWindow.show();

        WalletSetPasswordController.estimateKeyDerivationTimeMsec();

    }

    public static Map<String, String> getTokenHexNameMap() throws Exception {
        String CONTEXT_ROOT = Main.IpAddress + "/"; // Main.getContextRoot();
        Map<String, Object> requestParam = new HashMap<String, Object>();
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getTokens.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(response, GetTokensResponse.class);
        Map<String, String> map = new HashMap<String, String>();
        for (Tokens tokens : getTokensResponse.getTokens()) {
            map.put(Main.getString(tokens.getTokenid()), Main.getString(tokens.getTokenname()));
        }
        return map;
    }

    public static void addAddress2block(String name, String address) throws Exception {
        String CONTEXT_ROOT = getContextRoot();
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = Main.params.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_USERDATA);
        KeyParameter aesKey = null;
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> issuedKeys = Main.bitcoin.wallet().walletKeys(aesKey);

        ECKey pubKeyTo = null;
        if (bitcoin.wallet().isEncrypted()) {
            pubKeyTo = issuedKeys.get(0);
        } else {
            pubKeyTo = Main.bitcoin.wallet().currentReceiveKey();
        }

        Transaction coinbase = new Transaction(Main.params);
        Contact contact = new Contact();
        contact.setName(name);
        contact.setAddress(address);
        ContactInfo contactInfo = (ContactInfo) getUserdata(DataClassName.CONTACTINFO.name());

        List<Contact> list = contactInfo.getContactList();
        list.add(contact);
        contactInfo.setContactList(list);

        coinbase.setDataClassName(DataClassName.CONTACTINFO.name());
        coinbase.setData(contactInfo.toByteArray());

        Sha256Hash sighash = coinbase.getHash();

        ECKey.ECDSASignature party1Signature = pubKeyTo.sign(sighash, aesKey);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setAddress(pubKeyTo.toAddress(Main.params).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(pubKeyTo.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        coinbase.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignBies));

        block.addTransaction(coinbase);
        block.solve();

        OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
    }

    public static String getString4block(List<String> list) throws Exception {
        StringBuffer temp = new StringBuffer("");
        if (list != null && !list.isEmpty()) {
            for (String string : list) {
                temp.append(string + "\n\r");
            }
        }
        return temp.toString();

    }

    public static List<String> initAddress4block() throws Exception {

        List<String> addressList = new ArrayList<String>();
        ContactInfo contactInfo = (ContactInfo) getUserdata(DataClassName.CONTACTINFO.name());

        List<Contact> list = contactInfo.getContactList();
        for (Contact contact : list) {
            addressList.add(contact.getName() + "," + contact.getAddress());
        }
        return addressList;
    }

    public static String transaction2string(Transaction transaction) {
        StringBuilder s = new StringBuilder();
        s.append("  ").append(transaction.getHashAsString()).append('\n');

        if (transaction.isTimeLocked()) {
            s.append("  time locked until ");
            if (transaction.getLockTime() < Transaction.LOCKTIME_THRESHOLD) {
                s.append("block ").append(transaction.getLockTime());

            } else {
                s.append(Utils.dateTimeFormat(transaction.getLockTime() * 1000));
            }
            s.append('\n');
        }

        if (transaction.isCoinBase()) {
            String script;
            String script2;
            try {
                script = transaction.getInputs().get(0).getScriptSig().toString();
                script2 = transaction.getOutputs().get(0).toString();
            } catch (ScriptException e) {
                script = "???";
                script2 = "???";
            }
            s.append(Main.getText("coinbase")).append(script).append("   (").append(script2).append(")\n");
            return s.toString();
        }
        if (!transaction.getInputs().isEmpty()) {
            for (TransactionInput in : transaction.getInputs()) {
                s.append("     ");
                s.append(Main.getText("input") + ":   ");

                try {
                    String scriptSigStr = in.getScriptSig().toString();
                    s.append(!Strings.isNullOrEmpty(scriptSigStr) ? scriptSigStr : " ");
                    if (in.getValue() != null)
                        s.append(" ").append(in.getValue().toString());
                    s.append("\n          ");
                    s.append(Main.getText("connectedOutput"));
                    final TransactionOutPoint outpoint = in.getOutpoint();
                    s.append(outpoint.toString());
                    final TransactionOutput connectedOutput = outpoint.getConnectedOutput();
                    if (connectedOutput != null) {
                        Script scriptPubKey = connectedOutput.getScriptPubKey();
                        if (scriptPubKey.isSentToAddress() || scriptPubKey.isPayToScriptHash()) {
                            s.append(" hash160:");
                            s.append(Utils.HEX.encode(scriptPubKey.getPubKeyHash()));
                        }
                    }
                    if (in.hasSequence()) {
                        s.append("\n          sequence:").append(Long.toHexString(in.getSequenceNumber()));
                    }
                } catch (Exception e) {
                    s.append("[exception: ").append(e.getMessage()).append("]");
                }
                s.append('\n');
            }
        } else {
            s.append("     ");
            // s.append("INCOMPLETE: No inputs!\n");
        }
        for (TransactionOutput out : transaction.getOutputs()) {
            s.append("     ");
            s.append("out  ");
            try {
                String scriptPubKeyStr = out.getScriptPubKey().toString();
                s.append(!Strings.isNullOrEmpty(scriptPubKeyStr) ? scriptPubKeyStr : "");
                s.append("\n ");
                s.append(out.getValue().toString());
                if (!out.isAvailableForSpending()) {
                    s.append(" Spent");
                }
                if (out.getSpentBy() != null) {
                    s.append(" by ");
                    s.append(out.getSpentBy().getParentTransaction().getHashAsString());
                }
            } catch (Exception e) {
                s.append("[exception: ").append(e.getMessage()).append("]");
            }
            s.append('\n');
        }

        return s.toString();
    }

    public static String block2string(Block block) {
        StringBuilder s = new StringBuilder();
        s.append(Main.getText("blockhash") + ": ").append(block.getHashAsString()).append('\n');
        if (block.getTransactions() != null && block.getTransactions().size() > 0) {
            s.append("   ").append(block.getTransactions().size()).append(" " + Main.getText("transaction") + ":\n");
            for (Transaction tx : block.getTransactions()) {
                s.append(transaction2string(tx));
            }
        }
        s.append("   " + Main.getText("version") + ": ").append(block.getVersion());
        s.append('\n');
        s.append("   " + Main.getText("previous") + ": ").append(block.getPrevBlockHash()).append("\n");
        s.append("   " + Main.getText("branch") + ": ").append(block.getPrevBranchBlockHash()).append("\n");
        s.append("   " + Main.getText("merkle") + ": ").append(block.getMerkleRoot()).append("\n");
        s.append("   " + Main.getText("time") + ": ").append(block.getTimeSeconds()).append(" (")
                .append(Utils.dateTimeFormat(block.getTimeSeconds() * 1000)).append(")\n");
        // s.append(" difficulty target (nBits):
        // ").append(difficultyTarget).append("\n");
        s.append("   " + Main.getText("nonce") + ": ").append(block.getNonce()).append("\n");
        if (block.getMinerAddress() != null)
            s.append("   " + Main.getText("mineraddress") + ": ").append(new Address(params, block.getMinerAddress()))
                    .append("\n");

        s.append("   " + Main.getText("blocktype") + ": ").append(block.getBlockType()).append("\n");

        return s.toString();

    }

    public static List<String> initToken4block() throws Exception {
        WatchedInfo tokenInfo = (WatchedInfo) getUserdata(DataClassName.TOKEN.name());
        if (tokenInfo == null) {
            return null;
        }
        List<UserSettingData> list = tokenInfo.getUserSettingDatas();
        List<String> addressList = new ArrayList<String>();
        if (list != null && !list.isEmpty()) {
            for (UserSettingData userSettingData : list) {
                if (userSettingData.getDomain().equals(DataClassName.TOKEN.name())
                        && userSettingData.getValue().endsWith(":" + Main.getText("Token"))) {
                    addressList.add(userSettingData.getKey() + ","
                            + userSettingData.getValue().substring(0, userSettingData.getValue().lastIndexOf(":")));
                }

            }
        }
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
        File file = new File(keyFileDirectory + "/usersetting.block");
        byte[] data = null;
        if (file.exists()) {
            data = FileUtil.readFile(file);
        }
        boolean flag1 = false;
        boolean flag2 = false;
        if (data != null && data.length != 0) {
            Block block = Main.params.getDefaultSerializer().makeBlock(data);
            boolean flag = true;
            if (block == null) {
                flag = false;
            }
            if (block.getTransactions() == null || block.getTransactions().isEmpty()) {
                flag = false;
            }
            if (flag) {
                Transaction transaction = block.getTransactions().get(0);
                byte[] buf = transaction.getData();
                try {
                    WatchedInfo watchedInfo = new WatchedInfo().parse(buf);
                    List<UserSettingData> list = watchedInfo.getUserSettingDatas();
                    if (list != null && !list.isEmpty()) {
                        for (UserSettingData userSettingData : list) {
                            if (userSettingData.getDomain().equals(DataClassName.SERVERURL.name())) {
                                if (userSettingData.getValue() != null
                                        || !userSettingData.getValue().trim().isEmpty()) {
                                    flag1 = true;
                                    IpAddress = userSettingData.getValue();
                                }

                            }
                            if (userSettingData.getDomain().equals(DataClassName.LANG.name())) {
                                if (userSettingData.getValue() != null
                                        || !userSettingData.getValue().trim().isEmpty()) {
                                    flag2 = true;
                                    lang = userSettingData.getValue();
                                }

                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("", e);
                }
            }

        }
        if (args == null || args.length == 0) {
            if (!flag2) {
                lang = systemLang;
            }

            keyFileDirectory = System.getProperty("user.home");
            keyFilePrefix = System.getProperty("user.name");
        }
        if (args != null && args.length >= 2) {
            if (!flag2)
                lang = args[0];
            keyFileDirectory = new File(args[1]).getParent();
            String temp = new File(args[1]).getName();
            if (temp.contains(".")) {
                keyFilePrefix = temp.substring(0, temp.lastIndexOf("."));
            } else {
                keyFilePrefix = temp;
            }
            if (args.length >= 3) {
                if (!flag1)
                    IpAddress = args[2];
            }

        }

        launch(args);
    }

    public static void addUsersettingData() {
        try {
            addToken(getContextRoot(), lang, DataClassName.LANG.name(), DataClassName.LANG.name());
            addToken(getContextRoot(), IpAddress, DataClassName.SERVERURL.name(), DataClassName.SERVERURL.name());
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
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

    public void sentEmpstyBlock(int number) {

        for (int i = 0; i < number; i++) {
            try {
                sentEmpstyBlock();
                log.debug("empty block " + i);
            } catch (Exception e) {
                // Ignore
                log.debug("", e);
            }

        }
        ;

        // Threading.USER_THREAD.execute(r);

    }

    public String sentEmpstyBlock() throws JsonProcessingException, Exception {
        String CONTEXT_ROOT = Main.IpAddress + "/"; // http://" + Main.IpAddress
                                                    // + ":" + Main.port + "/";
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = params.getDefaultSerializer().makeBlock(data);
        rollingBlock.solve();

        return OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

    }

    public static List<UTXO> getUTXOWithECKeyList(List<ECKey> ecKeys, String tokenid) throws Exception {
        List<String> pubKeyHashs = new ArrayList<String>();

        for (ECKey ecKey : ecKeys) {
            pubKeyHashs.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }
        return getUTXOWithPubKeyHash(pubKeyHashs, tokenid);
    }

    public static List<UTXO> getUTXOWithPubKeyHash(byte[] pubKeyHash, String tokenid) throws Exception {
        List<String> pubKeyHashs = new ArrayList<String>();
        pubKeyHashs.add(Utils.HEX.encode(pubKeyHash));
        return getUTXOWithPubKeyHash(pubKeyHashs, tokenid);
    }

    public static List<UTXO> getUTXOWithPubKeyHash(List<String> pubKeyHashs, String tokenid) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        String ContextRoot = Main.IpAddress + "/"; // http://" + Main.IpAddress
                                                   // + ":" + Main.port + "/";

        String response = OkHttp3Util.post(ContextRoot + ReqCmd.getOutputs.name(),
                Json.jsonmapper().writeValueAsString(pubKeyHashs).getBytes());
        log.debug("tokenid:" + tokenid);
        log.debug("response:" + response);
        GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(response, GetOutputsResponse.class);
        for (UTXO utxo : getOutputsResponse.getOutputs()) {
            if (!utxo.getTokenId().equals(tokenid)) {
                continue;
            }
            if (utxo.getValue().getValue() > 0) {
                listUTXO.add(utxo);
            }
        }
        return listUTXO;
    }

    public static Coin calculateTotalUTXOList(byte[] pubKeyHash, String tokenid) throws Exception {
        List<String> pubKeyHashs = new ArrayList<String>();
        pubKeyHashs.add(Utils.HEX.encode(pubKeyHash));

        List<UTXO> listUTXO = getUTXOWithPubKeyHash(pubKeyHashs, tokenid);
        Coin amount = Coin.valueOf(0, tokenid);
        if (listUTXO == null || listUTXO.isEmpty()) {
            return amount;
        }
        for (UTXO utxo : listUTXO) {
            amount = amount.add(utxo.getValue());
        }
        return amount;
    }

    public static boolean checkResponse(String resp) throws JsonParseException, JsonMappingException, IOException {
        @SuppressWarnings("unchecked")
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int error = (Integer) result2.get("errorcode");
        return error > 0;
    }

    /**
     * is HH:mm:ss
     * 
     * @param time
     * @return
     */
    public static boolean isTime(String time) {
        Pattern p = Pattern
                .compile("((((0?[0-9])|([1][0-9])|([2][0-4]))\\:([0-5]?[0-9])((\\s)|(\\:([0-5]?[0-9])))))?$");
        return p.matcher(time).matches();
    }

    public static String getContextRoot() {
        return Main.IpAddress + "/"; // http://" + Main.IpAddress + ":" +
                                     // Main.port + "/";
    }

    public static Serializable getUserdata(String type) throws Exception {
        String CONTEXT_ROOT = Main.IpAddress + "/"; // http://" + Main.IpAddress
                                                    // + ":" + Main.port + "/";
        HashMap<String, String> requestParam = new HashMap<String, String>();

        KeyParameter aesKey = null;
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> issuedKeys = Main.bitcoin.wallet().walletKeys(aesKey);

        ECKey pubKeyTo = null;
        if (bitcoin.wallet().isEncrypted()) {
            pubKeyTo = issuedKeys.get(0);
        } else {
            pubKeyTo = Main.bitcoin.wallet().currentReceiveKey();
        }

        requestParam.put("pubKey", pubKeyTo.getPublicKeyAsHex());
        requestParam.put("dataclassname", type);
        byte[] bytes = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getUserData.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        if (DataClassName.CONTACTINFO.name().equals(type)) {
            if (bytes == null || bytes.length == 0) {
                return new ContactInfo();
            }
            ContactInfo contactInfo = new ContactInfo().parse(bytes);
            return contactInfo;
        } else if (DataClassName.MYHOMEADDRESS.name().equals(type)) {
            if (bytes == null || bytes.length == 0) {
                return new MyHomeAddress();
            }
            MyHomeAddress myHomeAddress = new MyHomeAddress().parse(bytes);
            return myHomeAddress;
        } else if (DataClassName.UPLOADFILE.name().equals(type)) {
            if (bytes == null || bytes.length == 0) {
                return new UploadfileInfo();
            }
            UploadfileInfo uploadfileInfo = new UploadfileInfo().parse(bytes);
            return uploadfileInfo;
        } else if (DataClassName.SERVERURL.name().equals(type) || DataClassName.LANG.name().equals(type)
                || DataClassName.TOKEN.name().equals(type) || DataClassName.WATCHED.name().equals(type)) {
            if (bytes == null || bytes.length == 0) {
                return new WatchedInfo();
            }
            WatchedInfo watchedInfo = new WatchedInfo().parse(bytes);
            return watchedInfo;
        } else {
            return null;
        }

    }

}

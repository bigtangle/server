/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import static com.google.common.base.Preconditions.checkState;
import static net.bigtangle.ui.wallet.utils.GuiUtils.checkGuiThread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.spongycastle.crypto.params.KeyParameter;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.TextFieldValidator;
import net.bigtangle.ui.wallet.utils.WTUtils;
import net.bigtangle.utils.OkHttp3Util;

public class StockController extends TokensController {
    @FXML
    public TabPane tabPane;
    @FXML
    public Tab multiPublishTab;

    @FXML
    public CheckBox tokenstopCheckBox;

    @FXML
    public ComboBox<String> tokenid;

    @FXML
    public TextField stockName;

    @FXML
    public TextField stockAmount;

    @FXML
    public ComboBox<String> tokenid1;
    @FXML
    public ChoiceBox<String> signAddrChoiceBox;

    @FXML
    public TextField stockName1;

    @FXML
    public TextField stockAmount1;

    @FXML
    public TextArea stockDescription1;

    @FXML
    public TextField urlTF;
    @FXML
    public TextField signnumberTF;

    @FXML
    public TextArea stockDescription;

    @FXML
    public TextField marketName;
    @FXML
    public ComboBox<String> marketid;
    @FXML
    public TextField marketurl;
    @FXML
    public TextArea marketDescription;

    @FXML
    public TableView<Map> signAddressTable;
    @FXML
    public TableColumn<Map, String> addressColumn;
    @FXML
    public TableColumn<Map, String> signColumn;
    @FXML
    public TextField addressTF;
    @FXML
    public CheckBox isSignCheckBox;

    public Main.OverlayUI overlayUI;

    @FXML
    public void initialize() {
        try {
            new TextFieldValidator(signnumberTF,
                    text -> !WTUtils.didThrow(() -> checkState(text.matches("[1-9]\\d*"))));
            tabPane.getSelectionModel().selectedItemProperty().addListener((ov, t, t1) -> {
                try {
                    initPositveTableView();
                } catch (Exception e) {

                }
            });
            initTableView();
            initPositveTableView();
            initSerialTableView();
            initCombobox();

        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void searchTokenSerial(ActionEvent event) throws Exception {

    }

    public void editToken(ActionEvent event) throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        Map<String, Object> rowData = tokenserialTable.getSelectionModel().getSelectedItem();
        if (rowData == null || rowData.isEmpty()) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m1"), Main.getText("ex_c_m1"));
            return;
        }
        String tokenid = (String) rowData.get("tokenid");
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", tokenid);
        String resp = OkHttp3Util.postString(CONTEXT_ROOT + "getTokenById",
                Json.jsonmapper().writeValueAsString(requestParam));
        tabPane.getSelectionModel().clearAndSelect(3);
        tokenid1.setValue(tokenid);

    }

    public void addSIgnAddress(ActionEvent event) {
        try {
            showAddAddressDialog();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void showAddAddressDialog() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        int signnumber = Integer.parseInt(signnumberTF.getText());
        if (signnumber > 1) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle(Main.getText("Address"));
            dialog.setHeaderText(null);
            dialog.setContentText(Main.getText("Address"));
            dialog.setWidth(900);
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                String address = result.get();
                if (!signAddrChoiceBox.getItems().contains(address)) {
                    signAddrChoiceBox.getItems().add(address);
                }

            }
        }
    }

    public void initCombobox() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("name", null);
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokens",
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");
        List<String> names = new ArrayList<String>();
        // wallet keys minus used from token list with one time (blocktype false
        KeyParameter aeskey = null;
        // Main.initAeskey(aeskey);
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aeskey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(aeskey);
        for (ECKey key : keys) {
            String temp = Utils.HEX.encode(key.getPubKeyHash());
            boolean flag = true;
            for (Map<String, Object> map : list) {
                String tokenHex = (String) map.get("tokenid");
                if (temp.equals(tokenHex)) {
                    if (!(boolean) map.get("multiserial")) {
                        flag = false;
                    }
                }
            }
            if (flag && !tokenData.contains(temp)) {
                tokenData.add(temp);
            }
        }
        tokenid.setItems(tokenData);
        tokenid1.setItems(tokenData);
        marketid.setItems(tokenData);
        tokenid1.getSelectionModel().selectedItemProperty().addListener((ov, oldv, newv) -> {
            try {
                showToken(newv);
            } catch (Exception e) {
                GuiUtils.crashAlert(e);
            }
        });

    }

    public void showToken(String tokenid) throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        HashMap<String, Object> request = new HashMap<String, Object>();
        request.put("tokenid", tokenid);
        String resp = OkHttp3Util.postString(CONTEXT_ROOT + "getTokenById",
                Json.jsonmapper().writeValueAsString(request));
        final Map<String, Object> data = Json.jsonmapper().readValue(resp, Map.class);
        if (data.containsKey("token")) {
            Map<String, Object> map = (Map<String, Object>) data.get("token");
            if (map != null && !map.isEmpty()) {
                stockName1.setText(Main.getString(map.get("tokenname")));
                stockName1.setEditable(false);
            }
        }
    }

    public void saveStock(ActionEvent event) {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ECKey outKey = Main.bitcoin.wallet().currentReceiveKey();

        try {
            byte[] pubKey = outKey.getPubKey();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
            requestParam.put("amount",
                    Coin.parseCoin(stockAmount.getText(), Utils.HEX.decode(tokenid.getValue())).getValue());
            requestParam.put("tokenname", stockName.getText());
            requestParam.put("description", stockDescription.getText());
            requestParam.put("tokenHex", tokenid.getValue());
            requestParam.put("multiserial", false);
            requestParam.put("asmarket", false);
            // requestParam.put("tokenstop",
            // tokenstopCheckBox.selectedProperty().get());

            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "createGenesisBlock",
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block block = Main.params.getDefaultSerializer().makeBlock(data);
            // TODO no post to off tangle data, send it to kafka for broadcast
            Main.instance.sendMessage(block.bitcoinSerialize());

            GuiUtils.informationalAlert(Main.getText("s_c_m"), Main.getText("s_c_m"));
            Main.instance.controller.initTableView();
            checkGuiThread();
            initTableView();
            overlayUI.done();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public void saveMarket(ActionEvent event) {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ECKey outKey = Main.bitcoin.wallet().currentReceiveKey();

        try {
            byte[] pubKey = outKey.getPubKey();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));

            requestParam.put("tokenname", marketName.getText());
            requestParam.put("url", marketurl.getText());
            requestParam.put("description", marketDescription.getText());
            requestParam.put("tokenHex", marketid.getValue());
            requestParam.put("multiserial", false);
            requestParam.put("asmarket", true);
            requestParam.put("tokenstop", false);

            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "createGenesisBlock",
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block block = Main.params.getDefaultSerializer().makeBlock(data);
            // TODO no post to off tangle data, send it to kafka for broadcast
            Main.instance.sendMessage(block.bitcoinSerialize());

            GuiUtils.informationalAlert(Main.getText("s_c_m"), Main.getText("s_c_m"));
            Main.instance.controller.initTableView();
            checkGuiThread();
            initTableView();
            overlayUI.done();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public void saveMultiToken(ActionEvent event) {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ECKey outKey = Main.bitcoin.wallet().currentReceiveKey();

        try {
            byte[] pubKey = outKey.getPubKey();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
            requestParam.put("amount",
                    Coin.parseCoin(stockAmount1.getText(), Utils.HEX.decode(tokenid1.getValue())).getValue());
            requestParam.put("tokenname", stockName1.getText());
            requestParam.put("url", urlTF.getText());
            requestParam.put("signnumber", signnumberTF.getText());
            requestParam.put("description", stockDescription1.getText());
            requestParam.put("tokenHex", tokenid1.getValue());
            requestParam.put("multiserial", true);
            requestParam.put("asmarket", false);
            requestParam.put("tokenstop", tokenstopCheckBox.selectedProperty().get());
            noSignBlock(requestParam);
            GuiUtils.informationalAlert(Main.getText("s_c_m"), Main.getText("s_c_m"));
            Main.instance.controller.initTableView();
            checkGuiThread();
            initTableView();
            overlayUI.done();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public void doMultiSign(ActionEvent event) {
    }

    public void noSignBlock(HashMap<String, Object> map) throws Exception {
        KeyParameter aesKey = null;
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(aesKey);
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";

        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(Main.getString(map.get("tokenHex")).trim(),
                Main.getString(map.get("tokenname")).trim(), Main.getString(map.get("description")).trim(),
                Main.getString(map.get("url")).trim(), signAddrChoiceBox.getItems().size(), true, false,
                (boolean) map.get("tokenstop"));
        tokenInfo.setTokens(tokens);
        if (signAddrChoiceBox.getItems() != null && !signAddrChoiceBox.getItems().isEmpty()) {
            for (String address : signAddrChoiceBox.getItems()) {
                tokenInfo.getMultiSignAddresses()
                        .add(new MultiSignAddress(Main.getString(map.get("tokenHex")).trim(), address));

            }
        }
        long amount = Coin.parseCoin(stockAmount1.getText(), Utils.HEX.decode(tokenid1.getValue())).getValue();
        Coin basecoin = Coin.valueOf(amount, Main.getString(map.get("tokenHex")).trim());
        tokenInfo.getTokenSerials().add(new TokenSerial(Main.getString(map.get("tokenHex")).trim(), 0, amount));
        HashMap<String, String> requestParam = new HashMap<String, String>();
        String resp000 = OkHttp3Util.postString(CONTEXT_ROOT + "getGenesisBlockLR",
                Json.jsonmapper().writeValueAsString(requestParam));

        HashMap<String, Object> result000 = Json.jsonmapper().readValue(resp000, HashMap.class);
        String leftBlockHex = (String) result000.get("leftBlockHex");
        String rightBlockHex = (String) result000.get("rightBlockHex");

        Block r1 = Main.params.getDefaultSerializer().makeBlock(Utils.HEX.decode(leftBlockHex));
        Block r2 = Main.params.getDefaultSerializer().makeBlock(Utils.HEX.decode(rightBlockHex));
        long blocktype0 = NetworkParameters.BLOCKTYPE_TOKEN_CREATION;
        Block block = new Block(Main.params, r1.getHash(), r2.getHash(), blocktype0,
                Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));
        ECKey key1 = keys.get(0);
        block.addCoinbaseTransaction(key1.getPubKey(), basecoin, tokenInfo);
        block.solve();

        // save block
        OkHttp3Util.post(CONTEXT_ROOT + "multiSign", block.bitcoinSerialize());
    }

    public void add2positve(ActionEvent event) {
        Map<String, Object> rowData = tokensTable.getSelectionModel().getSelectedItem();
        if (rowData == null || rowData.isEmpty()) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m1"), Main.getText("ex_c_d1"));
            return;
        }
        String tokeninfo = "";
        tokeninfo += Main.getString(rowData.get("tokenHex")) + "," + Main.getString(rowData.get("tokenname"));
        try {
            Main.addText2file(tokeninfo, Main.keyFileDirectory + Main.positiveFile);
        } catch (Exception e) {

        }
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

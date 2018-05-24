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
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
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

    public Main.OverlayUI overlayUI;

    @FXML
    public void initialize() {
        try {
            initCombobox();
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
            initMultisignTableView();
            // initSerialTableView();

        } catch (Exception e) {
            e.printStackTrace();
            GuiUtils.crashAlert(e);
        }
    }

    public void searchTokenSerial(ActionEvent event) {
        try {
            // initSerialTableView();
            initMultisignTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
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
        String temp = signnumberTF.getText();
        if (temp != null && !temp.isEmpty() && temp.matches("[1-9]\\d*")) {

            int signnumber = Integer.parseInt(temp);
            if (signnumber > 1) {

                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle(Main.getText("Address"));
                dialog.setHeaderText(null);
                dialog.setContentText(Main.getText("Address"));
                dialog.setWidth(500);
                dialog.getDialogPane().setPrefWidth(500);
                Optional<String> result = dialog.showAndWait();
                if (result.isPresent()) {
                    String address = result.get();
                    if (address != null && !address.isEmpty() && !signAddrChoiceBox.getItems().contains(address)) {
                        signAddrChoiceBox.getItems().add(address);
                    }

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
        ECKey outKey = Main.bitcoin.wallet().currentReceiveKey();
        if (signnumberTF.getText() == null || signnumberTF.getText().trim().isEmpty()) {
            GuiUtils.informationalAlert("", Main.getText("signnumberNoEq"), "");
            return;
        }
        if (!signnumberTF.getText().matches("[1-9]\\d*")) {
            GuiUtils.informationalAlert("", Main.getText("signnumberNoEq"), "");
            return;
        }
        if (signnumberTF.getText() != null && !signnumberTF.getText().trim().isEmpty()
                && signnumberTF.getText().matches("[1-9]\\d*")
                && Long.parseLong(signnumberTF.getText().trim()) != signAddrChoiceBox.getItems().size()) {

            GuiUtils.informationalAlert("", Main.getText("signnumberNoEq"), "");
            return;
        }
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
            initMultisignTableView();
            // overlayUI.done();
            tabPane.getSelectionModel().clearAndSelect(4);
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public void multiSign(ActionEvent event) {
        try {
            doMultiSign();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void doMultiSign() throws Exception {

        KeyParameter aesKey = null;
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(aesKey);

        Map<String, Object> rowdata = tokenserialTable.getSelectionModel().getSelectedItem();
        if (rowdata == null || rowdata.isEmpty()) {
            GuiUtils.informationalAlert("", Main.getText("pleaseSelect"), "");
            return;
        }
        if (!"0".equals(rowdata.get("sign").toString())) {
            return;
        }
        ECKey myKey = null;
        for (ECKey ecKey : keys) {
            if (rowdata.get("address").toString().equals((ecKey.toAddress(Main.params).toBase58()))) {
                myKey = ecKey;
                break;
            }
        }
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";

        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("address", rowdata.get("address").toString());
        String resp = OkHttp3Util.postString(CONTEXT_ROOT + "getMultiSignWithAddress",
                Json.jsonmapper().writeValueAsString(requestParam0));
        System.out.println(resp);

        HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);
        List<HashMap<String, Object>> multiSigns = (List<HashMap<String, Object>>) result.get("multiSigns");
        HashMap<String, Object> multiSign000 = null;
        for (HashMap<String, Object> multiSign : multiSigns) {
            if (multiSign.get("id").toString().equals(rowdata.get("id").toString())) {
                multiSign000 = multiSign;
            }
        }
        byte[] payloadBytes = Utils.HEX.decode((String) multiSign000.get("blockhashHex"));
        Block block0 = Main.params.getDefaultSerializer().makeBlock(payloadBytes);
        Transaction transaction = block0.getTransactions().get(0);

        List<MultiSignBy> multiSignBies = null;
        if (transaction.getDatasignatire() == null) {
            multiSignBies = new ArrayList<MultiSignBy>();
        } else {
            multiSignBies = Json.jsonmapper().readValue(transaction.getDatasignatire(), List.class);
        }
        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = myKey.sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        MultiSignBy multiSignBy0 = new MultiSignBy();

        multiSignBy0.setTokenid(Main.getString(rowdata.get("tokenid")).trim());
        multiSignBy0.setTokenindex((Integer) multiSign000.get("tokenindex"));
        multiSignBy0.setAddress(rowdata.get("address").toString());
        multiSignBy0.setPublickey(Utils.HEX.encode(myKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);

        transaction.setDatasignatire(Json.jsonmapper().writeValueAsBytes(multiSignBies));
        OkHttp3Util.post(CONTEXT_ROOT + "multiSign", block0.bitcoinSerialize());
        Main.instance.controller.initTableView();
        initTableView();
        initMultisignTableView();

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

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", Main.getString(map.get("tokenHex")).trim());
        String resp2 = OkHttp3Util.postString(CONTEXT_ROOT + "getCalTokenIndex",
                Json.jsonmapper().writeValueAsString(requestParam00));
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp2, HashMap.class);
        Integer tokenindex_ = (Integer) result2.get("tokenindex");

        tokenInfo.setTokenSerial(new TokenSerial(Main.getString(map.get("tokenHex")).trim(), tokenindex_, amount));

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
        ECKey key1 = Main.bitcoin.wallet().currentReceiveKey();
        List<ECKey> myEcKeys = new ArrayList<ECKey>();
        if (signAddrChoiceBox.getItems() != null && !signAddrChoiceBox.getItems().isEmpty()) {
            ObservableList<String> addresses = signAddrChoiceBox.getItems();
            for (ECKey ecKey : keys) {
                // System.out.println(ecKey.toAddress(Main.params).toBase58());
                if (addresses.contains(ecKey.toAddress(Main.params).toBase58())) {
                    myEcKeys.add(ecKey);
                }
            }
            key1 = myEcKeys.get(0);
        }
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

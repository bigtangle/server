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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
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
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.core.http.server.resp.GetTokensResponse;
import net.bigtangle.core.http.server.resp.MultiSignResponse;
import net.bigtangle.core.http.server.resp.SearchMultiSignResponse;
import net.bigtangle.core.http.server.resp.TokenSerialIndexResponse;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.IgnoreServiceException;
import net.bigtangle.ui.wallet.utils.TextFieldValidator;
import net.bigtangle.ui.wallet.utils.WTUtils;
import net.bigtangle.utils.OkHttp3Util;

@SuppressWarnings("rawtypes")
public class TokenController extends TokenBaseController {

    private static final Logger log = LoggerFactory.getLogger(TokenController.class);

    @FXML
    public TabPane tabPane;
    @FXML
    public Tab multiPublishTab;
    @FXML
    public Tab multisignTab;

    @FXML
    public CheckBox tokenstopCheckBox;

    @FXML
    public ComboBox<String> tokenid;

    @FXML
    public TextField stockName;
    @FXML
    public TextField stockUrl;

    @FXML
    public TextField stockAmount;

    @FXML
    public TextArea stockDescription;

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
    public TextField stockName11;
    @FXML
    public ComboBox<String> tokenid11;
    @FXML
    public CheckBox tokenstopCheckBox1;

    @FXML
    public TextField stockAmount11;

    @FXML
    public TextArea stockDescription11;

    @FXML
    public TextField urlTF1;
    @FXML
    public TextField signnumberTF1;
    @FXML
    public TextField signPubkeyTF1;
    @FXML
    public ChoiceBox<String> signAddrChoiceBox1;

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
    public Button save1;
    @FXML
    public TextField signPubkeyTF;

    public String address;
    public String tokenUUID;
    public String tokenidString;
    public Main.OverlayUI overlayUI;

    @FXML
    public void initialize() {
        try {
            initCombobox();
            new TextFieldValidator(signnumberTF,
                    text -> !WTUtils.didThrow(() -> checkState(text.matches("[1-9]\\d*"))));

            tabPane.getSelectionModel().selectedIndexProperty().addListener((ov, t, t1) -> {
                int index = t1.intValue();
                switch (index) {
                case 0: {
                }

                    break;
                case 1: {
                }

                    break;
                case 2: {
                }

                    break;
                case 3: {
                }

                    break;
                case 4: {
                }

                    break;
                case 5: {
                }

                    break;
                default: {
                }
                    break;
                }
            });
            initTableView();
            initMultisignTableView();
            Main.bitcoin.wallet().setServerURL(Main.getContextRoot());
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

    @SuppressWarnings("unchecked")
    public void editToken(ActionEvent event) throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        Map<String, Object> rowdata = tokenserialTable.getSelectionModel().getSelectedItem();
        if (rowdata == null || rowdata.isEmpty()) {
            GuiUtils.informationalAlert("", Main.getText("pleaseSelect"), "");
            return;
        }
        if (!"0".equals(rowdata.get("sign").toString())) {
            // return;
        }
        String tokenid = (String) rowdata.get("tokenid");
        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("address", rowdata.get("address").toString());
        address = rowdata.get("address").toString();
        tokenUUID = rowdata.get("id").toString();
        tokenidString = rowdata.get("tokenid").toString();
        String resp = OkHttp3Util.postString(CONTEXT_ROOT + ReqCmd.getMultiSignWithAddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        MultiSign multiSign000 = null;
        for (MultiSign multiSign : multiSignResponse.getMultiSigns()) {
            if (multiSign.getId().equals(rowdata.get("id").toString())) {
                multiSign000 = multiSign;
            }
        }

        byte[] payloadBytes = Utils.HEX.decode((String) multiSign000.getBlockhashHex());
        Block block0 = Main.params.getDefaultSerializer().makeBlock(payloadBytes);
        Transaction transaction = block0.getTransactions().get(0);

        byte[] buf = transaction.getData();
        TokenInfo tokenInfo = new TokenInfo().parse(buf);

        tabPane.getSelectionModel().clearAndSelect(3);
        stockName1.setText(Main.getString(tokenInfo.getTokens().getTokenname()).trim());
        tokenid1.setValue(tokenid);
        String amountString = Coin.valueOf(tokenInfo.getTokenSerial().getAmount(), tokenid).toPlainString();
        stockAmount1.setText(amountString);
        tokenstopCheckBox.setSelected(tokenInfo.getTokens().isTokenstop());
        urlTF.setText(Main.getString(tokenInfo.getTokens().getUrl()).trim());
        stockDescription1.setText(Main.getString(tokenInfo.getTokens().getDescription()).trim());
        signnumberTF.setText(Main.getString(tokenInfo.getTokens().getSignnumber()).trim());
        signAddrChoiceBox.getItems().clear();
        List<MultiSignAddress> multiSignAddresses = tokenInfo.getMultiSignAddresses();
        if (multiSignAddresses != null && !multiSignAddresses.isEmpty()) {
            for (MultiSignAddress msa : multiSignAddresses) {
                signAddrChoiceBox.getItems().add(msa.getAddress());
            }
        }
        requestParam0 = new HashMap<String, Object>();
        requestParam0.put("tokenid", rowdata.get("tokenid").toString());
        requestParam0.put("tokenindex", Long.parseLong(rowdata.get("tokenindex").toString()));
        requestParam0.put("sign", 0);
        resp = OkHttp3Util.postString(CONTEXT_ROOT + ReqCmd.getCountSign.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        MultiSignResponse multiSignResponse2 = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        int count = multiSignResponse2.getSignCount();

        if (count == 0) {
            save1.setDisable(true);
        }

    }

    @SuppressWarnings("unchecked")
    public void againPublish(ActionEvent event) throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        Map<String, Object> rowdata = tokenserialTable.getSelectionModel().getSelectedItem();
        if (rowdata == null || rowdata.isEmpty()) {
            GuiUtils.informationalAlert("", Main.getText("pleaseSelect"), "");
            return;
        }
        if ("0".equals(rowdata.get("sign").toString())) {
            return;
        }
        String tokenid = (String) rowdata.get("tokenid");
        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("address", rowdata.get("address").toString());
        address = rowdata.get("address").toString();
        tokenUUID = rowdata.get("id").toString();
        tokenidString = rowdata.get("tokenid").toString();
        String resp = OkHttp3Util.postString(CONTEXT_ROOT + ReqCmd.getMultiSignWithAddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        MultiSign multiSign000 = null;
        for (MultiSign multiSign : multiSignResponse.getMultiSigns()) {
            if (multiSign.getId().equals(rowdata.get("id").toString())) {
                multiSign000 = multiSign;
            }
        }

        byte[] payloadBytes = Utils.HEX.decode((String) multiSign000.getBlockhashHex());
        Block block0 = Main.params.getDefaultSerializer().makeBlock(payloadBytes);
        Transaction transaction = block0.getTransactions().get(0);

        byte[] buf = transaction.getData();
        TokenInfo tokenInfo = new TokenInfo().parse(buf);

        tabPane.getSelectionModel().clearAndSelect(3);
        stockName1.setText(Main.getString(tokenInfo.getTokens().getTokenname()).trim());
        tokenid1.setValue(tokenid);
        // String amountString =
        // Coin.valueOf(tokenInfo.getTokenSerial().getAmount(),
        // tokenid).toPlainString();
        // stockAmount1.setText(amountString);
        tokenstopCheckBox.setSelected(tokenInfo.getTokens().isTokenstop());
        urlTF.setText(Main.getString(tokenInfo.getTokens().getUrl()).trim());
        stockDescription1.setText(Main.getString(tokenInfo.getTokens().getDescription()).trim());
        signnumberTF.setText(Main.getString(tokenInfo.getTokens().getSignnumber()).trim());
        signAddrChoiceBox.getItems().clear();
        List<MultiSignAddress> multiSignAddresses = tokenInfo.getMultiSignAddresses();
        if (multiSignAddresses != null && !multiSignAddresses.isEmpty()) {
            for (MultiSignAddress msa : multiSignAddresses) {
                signAddrChoiceBox.getItems().add(msa.getAddress());
            }
        }
        requestParam0 = new HashMap<String, Object>();
        requestParam0.put("tokenid", rowdata.get("tokenid").toString());
        requestParam0.put("tokenindex", Long.parseLong(rowdata.get("tokenindex").toString()));
        requestParam0.put("sign", 0);
        resp = OkHttp3Util.postString(CONTEXT_ROOT + ReqCmd.getCountSign.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        MultiSignResponse multiSignResponse2 = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        int count = multiSignResponse2.getSignCount();
        if (count == 0) {
            save1.setDisable(true);
        }

    }

    public void addSIgnAddress(ActionEvent event) {
        try {
            addPubkey();
            // showAddAddressDialog();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void addSIgnAddressA(ActionEvent event) {
        try {
            String temp = signnumberTF1.getText();
            if (temp != null && !temp.isEmpty() && temp.matches("[1-9]\\d*")) {

                int signnumber = Integer.parseInt(temp);
                if (signnumber >= 1) {
                    String address = signPubkeyTF1.getText();
                    if (address != null && !address.isEmpty() && !signAddrChoiceBox1.getItems().contains(address)) {
                        signAddrChoiceBox1.getItems().add(address);
                        signAddrChoiceBox1.getSelectionModel().selectLast();
                    }
                }
            }
            signPubkeyTF1.setText("");
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void addPubkey() {
        String temp = signnumberTF.getText();
        if (temp != null && !temp.isEmpty() && temp.matches("[1-9]\\d*")) {

            int signnumber = Integer.parseInt(temp);
            if (signnumber >= 1) {
                String address = signPubkeyTF.getText();
                if (address != null && !address.isEmpty() && !signAddrChoiceBox.getItems().contains(address)) {
                    signAddrChoiceBox.getItems().add(address);
                    signAddrChoiceBox.getSelectionModel().selectLast();
                }
            }
        }
        signPubkeyTF.setText("");
    }

    public void showAddAddressDialog() throws Exception {

        String temp = signnumberTF.getText();
        if (temp != null && !temp.isEmpty() && temp.matches("[1-9]\\d*")) {

            int signnumber = Integer.parseInt(temp);
            if (signnumber >= 1) {

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
                        signAddrChoiceBox.getSelectionModel().selectLast();
                    }

                }
            } else {
                GuiUtils.informationalAlert("", "", "");
            }
        } else {
            GuiUtils.informationalAlert("", "", "");
        }
    }

    public void initCombobox() throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("name", null);
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getTokens.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(response, GetTokensResponse.class);

        // wallet keys minus used from token list with one time (blocktype false
        KeyParameter aeskey = null;
        // Main.initAeskey(aeskey);
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aeskey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(aeskey);
        for (ECKey key : keys) {
            String temp = Utils.HEX.encode(key.getPubKey());
            boolean flag = true;
            for (Tokens tokens : getTokensResponse.getTokens()) {
                String tokenHex = tokens.getTokenid();
                if (temp.equals(tokenHex)) {
                    if (!tokens.isMultiserial()) {
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
        tokenid11.setItems(tokenData);
        marketid.setItems(tokenData);
        tokenid1.getSelectionModel().selectedItemProperty().addListener((ov, oldv, newv) -> {
            try {
                showToken(newv);
            } catch (Exception e) {
                GuiUtils.crashAlert(e);
            }
        });

    }

    public void removeSignAddress(ActionEvent event) {
        signAddrChoiceBox.getItems().remove(signAddrChoiceBox.getValue());
    }

    public void removeSignAddressA(ActionEvent event) {
        signAddrChoiceBox1.getItems().remove(signAddrChoiceBox1.getValue());
    }

    public void showToken(String newtokenid) throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();

        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", tokenid.getValue());
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getMultiSignWithTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        final SearchMultiSignResponse searchMultiSignResponse = Json.jsonmapper().readValue(response,
                SearchMultiSignResponse.class);
        if (searchMultiSignResponse.getMultiSignList() != null
                && !searchMultiSignResponse.getMultiSignList().isEmpty()) {
            Map<String, Object> multisignMap = searchMultiSignResponse.getMultiSignList().get(0);
            tokenUUID = multisignMap.get("id").toString();
        } else {
            tokenUUID = null;
        }
        if (tokenUUID != null && !tokenUUID.isEmpty()) {
            if (!newtokenid.equals(tokenidString)) {
                tabPane.getSelectionModel().clearSelection();
                // save1.setDisable(false);
            } else {
                // save1.setDisable(true);
            }
        } else {

        }

    }

    public void saveToken(ActionEvent event) {
        try {
            ECKey outKey = null;

            KeyParameter aesKey = null;
            final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
            if (!"".equals(Main.password.trim())) {
                aesKey = keyCrypter.deriveKey(Main.password);
            }
            List<ECKey> issuedKeys = Main.bitcoin.wallet().walletKeys(aesKey);

            if (Main.bitcoin.wallet().isEncrypted()) {
                outKey = issuedKeys.get(0);
            } else {
                outKey = Main.bitcoin.wallet().currentReceiveKey();
            }

            TokenInfo tokenInfo = new TokenInfo();
            Tokens tokens = new Tokens(tokenid.getValue().trim(), stockName.getText().trim(),
                    stockDescription.getText().trim(), stockUrl.getText().trim(), 1, false, TokenType.token.ordinal(),
                    false);
            tokenInfo.setTokens(tokens);

            // add MultiSignAddress item
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

            Coin basecoin = Coin.parseCoin(stockAmount.getText(), Utils.HEX.decode(tokenid.getValue()));

            long amount = basecoin.getValue();
            tokenInfo.setTokenSerial(new TokenSerial(tokens.getTokenid(), 0, amount));
            saveToken(tokenInfo, basecoin, outKey);
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void saveToken(TokenInfo tokenInfo, Coin basecoin, ECKey outKey) {

        try {

            KeyParameter aesKey = null;
            final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
            if (!"".equals(Main.password.trim())) {
                aesKey = keyCrypter.deriveKey(Main.password);
            }
            Main.bitcoin.wallet().saveToken(tokenInfo, basecoin, outKey, aesKey);

            GuiUtils.informationalAlert("", Main.getText("s_c_m"));
            Main.instance.controller.initTableView();
            checkGuiThread();
            initTableView();
            overlayUI.done();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void saveMarket(ActionEvent event) {
        try {
            ECKey outKey = null;

            KeyParameter aesKey = null;
            final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
            if (!"".equals(Main.password.trim())) {
                aesKey = keyCrypter.deriveKey(Main.password);
            }
            List<ECKey> issuedKeys = Main.bitcoin.wallet().walletKeys(aesKey);

            if (Main.bitcoin.wallet().isEncrypted()) {
                outKey = issuedKeys.get(0);
            } else {
                outKey = Main.bitcoin.wallet().currentReceiveKey();
            }

            TokenInfo tokenInfo = new TokenInfo();
            Tokens tokens = new Tokens(marketid.getValue().trim(), marketName.getText().trim(),
                    marketDescription.getText().trim(), marketurl.getText(), 1, false, TokenType.market.ordinal(),
                    false);
            tokenInfo.setTokens(tokens);

            // add MultiSignAddress item
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

            Coin basecoin = Coin.parseCoin("0", Utils.HEX.decode(marketid.getValue()));

            long amount = basecoin.getValue();
            tokenInfo.setTokenSerial(new TokenSerial(tokens.getTokenid(), 0, amount));

            saveToken(tokenInfo, basecoin, outKey);
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public void saveMultiSubtangle(ActionEvent event) {
        try {
            ECKey outKey = null;

            KeyParameter aesKey = null;
            final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
            if (!"".equals(Main.password.trim())) {
                aesKey = keyCrypter.deriveKey(Main.password);
            }
            List<ECKey> issuedKeys = Main.bitcoin.wallet().walletKeys(aesKey);

            if (Main.bitcoin.wallet().isEncrypted()) {
                outKey = issuedKeys.get(0);
            } else {
                outKey = Main.bitcoin.wallet().currentReceiveKey();
            }

            if (signnumberTF1.getText() == null || signnumberTF1.getText().trim().isEmpty()) {
                GuiUtils.informationalAlert("", Main.getText("signnumberNoEq"), "");
                return;
            }
            if (!signnumberTF1.getText().matches("[1-9]\\d*")) {
                GuiUtils.informationalAlert("", Main.getText("signnumberNoEq"), "");
                return;
            }
            if (signnumberTF1.getText() != null && !signnumberTF1.getText().trim().isEmpty()
                    && signnumberTF1.getText().matches("[1-9]\\d*")
                    && Long.parseLong(signnumberTF1.getText().trim()) > signAddrChoiceBox1.getItems().size()) {

                GuiUtils.informationalAlert("", Main.getText("signnumberNoEq"), "");
                return;
            }

            byte[] pubKey = outKey.getPubKey();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
            // requestParam.put("amount",
            // Coin.parseCoin(stockAmount11.getText(),
            // Utils.HEX.decode(tokenid11.getValue())).getValue());
            requestParam.put("tokenname", stockName11.getText());
            requestParam.put("url", urlTF1.getText());
            requestParam.put("signnumber", signnumberTF1.getText());
            requestParam.put("description", stockDescription11.getText());
            requestParam.put("tokenHex", tokenid11.getValue());
            requestParam.put("multiserial", true);
            requestParam.put("asmarket", false);
            requestParam.put("tokenstop", tokenstopCheckBox1.selectedProperty().get());
            doSaveSubtangle(requestParam);
            GuiUtils.informationalAlert("", Main.getText("s_c_m"));
            Main.instance.controller.initTableView();
            checkGuiThread();
            initTableView();
            initMultisignTableView();
            overlayUI.done();
            // tabPane.getSelectionModel().clearAndSelect(4);
        } catch (IgnoreServiceException e) {
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void doSaveSubtangle(HashMap<String, Object> map) throws Exception {
        KeyParameter aesKey = null;
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(aesKey);

        String CONTEXT_ROOT = Main.getContextRoot();

        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(Main.getString(map.get("tokenHex")).trim(),
                Main.getString(map.get("tokenname")).trim(), Main.getString(map.get("description")).trim(),
                Main.getString(map.get("url")).trim(), Long.parseLong(this.signnumberTF1.getText().trim()), true,
                TokenType.subtangle.ordinal(), (boolean) map.get("tokenstop"));
        tokenInfo.setTokens(tokens);
        if (signAddrChoiceBox1.getItems() != null && !signAddrChoiceBox1.getItems().isEmpty()) {
            for (String pubKeyHex : signAddrChoiceBox.getItems()) {
                ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(pubKeyHex));
                tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(Main.getString(map.get("tokenHex")).trim(),
                        "", ecKey.getPublicKeyAsHex()));
            }
        }
        Coin basecoin = Coin.valueOf(0, Main.getString(map.get("tokenHex")).trim());
        long amount = basecoin.getValue();
        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", Main.getString(map.get("tokenHex")).trim());
        String resp2 = OkHttp3Util.postString(CONTEXT_ROOT + ReqCmd.getCalTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenSerialIndexResponse tokenSerialIndexResponse = Json.jsonmapper().readValue(resp2,
                TokenSerialIndexResponse.class);
        Integer tokenindex_ = tokenSerialIndexResponse.getTokenindex();

        tokenInfo.setTokenSerial(new TokenSerial(Main.getString(map.get("tokenHex")).trim(), tokenindex_, amount));

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = Main.params.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
        ECKey key1 = null;

        if (Main.bitcoin.wallet().isEncrypted()) {
            key1 = keys.get(0);
        } else {
            key1 = Main.bitcoin.wallet().currentReceiveKey();
        }

        signAddrChoiceBox1.getItems().add(key1.toAddress(Main.params).toBase58());
        List<ECKey> myEcKeys = new ArrayList<ECKey>();
        if (signAddrChoiceBox1.getItems() != null && !signAddrChoiceBox1.getItems().isEmpty()) {
            ObservableList<String> addresses = signAddrChoiceBox1.getItems();
            for (ECKey ecKey : keys) {
                // log.debug(ecKey.toAddress(Main.params).toBase58());
                if (addresses.contains(ecKey.toAddress(Main.params).toBase58())) {
                    myEcKeys.add(ecKey);
                }
            }
            if (!myEcKeys.isEmpty()) {
                key1 = myEcKeys.get(0);
            }

        }

        block.addCoinbaseTransaction(key1.getPubKey(), basecoin, tokenInfo);
        block.solve();

        // save block
        String resp = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.multiSign.name(), block.bitcoinSerialize());
        @SuppressWarnings("unchecked")
        HashMap<String, Object> respRes = Json.jsonmapper().readValue(resp, HashMap.class);
        int errorcode = (Integer) respRes.get("errorcode");
        if (errorcode > 0) {
            String message = (String) respRes.get("message");
            GuiUtils.informationalAlert("SIGN ERROR : " + message, Main.getText("ex_c_d1"));
            throw new IgnoreServiceException();
        }

    }

    public void saveMultiToken(ActionEvent event) {
        try {
            ECKey outKey = null;

            KeyParameter aesKey = null;
            final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
            if (!"".equals(Main.password.trim())) {
                aesKey = keyCrypter.deriveKey(Main.password);
            }
            List<ECKey> issuedKeys = Main.bitcoin.wallet().walletKeys(aesKey);

            if (Main.bitcoin.wallet().isEncrypted()) {
                outKey = issuedKeys.get(0);
            } else {
                outKey = Main.bitcoin.wallet().currentReceiveKey();
            }

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
                    && Long.parseLong(signnumberTF.getText().trim()) > signAddrChoiceBox.getItems().size()) {

                GuiUtils.informationalAlert("", Main.getText("signnumberNoEq"), "");
                return;
            }

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
            GuiUtils.informationalAlert("", Main.getText("s_c_m"));
            Main.instance.controller.initTableView();
            checkGuiThread();
            initTableView();
            initMultisignTableView();
            overlayUI.done();
            // tabPane.getSelectionModel().clearAndSelect(4);
        } catch (IgnoreServiceException e) {
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

    @SuppressWarnings("unchecked")
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
            GuiUtils.informationalAlert("", Main.getText("mySignExist"), "");
            return;
        }
        ECKey myKey = null;
        for (ECKey ecKey : keys) {
            if (rowdata.get("address").toString().equals((ecKey.toAddress(Main.params).toBase58()))) {
                myKey = ecKey;
                break;
            }
        }
        String CONTEXT_ROOT = Main.getContextRoot();

        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("address", rowdata.get("address").toString());
        String resp = OkHttp3Util.postString(CONTEXT_ROOT + ReqCmd.getMultiSignWithAddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));
        log.debug(resp);

        MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        MultiSign multiSign000 = null;
        for (MultiSign multiSign : multiSignResponse.getMultiSigns()) {
            if (multiSign.getId().equals(rowdata.get("id").toString())) {
                multiSign000 = multiSign;
                break;
            }
        }

        byte[] payloadBytes = Utils.HEX.decode((String) multiSign000.getBlockhashHex());
        Block block0 = Main.params.getDefaultSerializer().makeBlock(payloadBytes);
        Transaction transaction = block0.getTransactions().get(0);

        List<MultiSignBy> multiSignBies = null;
        if (transaction.getDataSignature() == null) {
            multiSignBies = new ArrayList<MultiSignBy>();
        } else {
            MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(transaction.getDataSignature(),
                    MultiSignByRequest.class);
            multiSignBies = multiSignByRequest.getMultiSignBies();
        }
        Sha256Hash sighash = transaction.getHash();

        ECKey.ECDSASignature party1Signature = myKey.sign(sighash, aesKey);
        byte[] buf1 = party1Signature.encodeToDER();

        MultiSignBy multiSignBy0 = new MultiSignBy();

        multiSignBy0.setTokenid(Main.getString(rowdata.get("tokenid")).trim());
        multiSignBy0.setTokenindex(multiSign000.getTokenindex());
        multiSignBy0.setAddress(rowdata.get("address").toString());
        multiSignBy0.setPublickey(Utils.HEX.encode(myKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
        OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.multiSign.name(), block0.bitcoinSerialize());
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

        String CONTEXT_ROOT = Main.getContextRoot();

        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(Main.getString(map.get("tokenHex")).trim(),
                Main.getString(map.get("tokenname")).trim(), Main.getString(map.get("description")).trim(),
                Main.getString(map.get("url")).trim(), Long.parseLong(this.signnumberTF.getText().trim()), true,
                TokenType.token.ordinal(), (boolean) map.get("tokenstop"));
        tokenInfo.setTokens(tokens);
        if (signAddrChoiceBox.getItems() != null && !signAddrChoiceBox.getItems().isEmpty()) {
            for (String pubKeyHex : signAddrChoiceBox.getItems()) {
                ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(pubKeyHex));
                tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(Main.getString(map.get("tokenHex")).trim(),
                        "", ecKey.getPublicKeyAsHex()));
            }
        }
        long amount = Coin.parseCoin(stockAmount1.getText(), Utils.HEX.decode(tokenid1.getValue())).getValue();
        Coin basecoin = Coin.valueOf(amount, Main.getString(map.get("tokenHex")).trim());

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", Main.getString(map.get("tokenHex")).trim());
        String resp2 = OkHttp3Util.postString(CONTEXT_ROOT + ReqCmd.getCalTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenSerialIndexResponse tokenSerialIndexResponse = Json.jsonmapper().readValue(resp2,
                TokenSerialIndexResponse.class);
        Integer tokenindex_ = tokenSerialIndexResponse.getTokenindex();

        tokenInfo.setTokenSerial(new TokenSerial(Main.getString(map.get("tokenHex")).trim(), tokenindex_, amount));

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = Main.params.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
        ECKey key1 = null;

        if (Main.bitcoin.wallet().isEncrypted()) {
            key1 = keys.get(0);
        } else {
            key1 = Main.bitcoin.wallet().currentReceiveKey();
        }

        signAddrChoiceBox.getItems().add(key1.toAddress(Main.params).toBase58());
        List<ECKey> myEcKeys = new ArrayList<ECKey>();
        if (signAddrChoiceBox.getItems() != null && !signAddrChoiceBox.getItems().isEmpty()) {
            ObservableList<String> addresses = signAddrChoiceBox.getItems();
            for (ECKey ecKey : keys) {
                // log.debug(ecKey.toAddress(Main.params).toBase58());
                if (addresses.contains(ecKey.toAddress(Main.params).toBase58())) {
                    myEcKeys.add(ecKey);
                }
            }
            if (!myEcKeys.isEmpty()) {
                key1 = myEcKeys.get(0);
            }

        }

        block.addCoinbaseTransaction(key1.getPubKey(), basecoin, tokenInfo);
        block.solve();

        // save block
        String resp = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.multiSign.name(), block.bitcoinSerialize());
        @SuppressWarnings("unchecked")
        HashMap<String, Object> respRes = Json.jsonmapper().readValue(resp, HashMap.class);
        int errorcode = (Integer) respRes.get("errorcode");
        if (errorcode > 0) {
            String message = (String) respRes.get("message");
            GuiUtils.informationalAlert("SIGN ERROR : " + message, Main.getText("ex_c_d1"));
            throw new IgnoreServiceException();
        }
    }

    @SuppressWarnings("unchecked")
    public void add2positve(ActionEvent event) {
        String CONTEXT_ROOT = Main.getContextRoot();
        Map<String, Object> rowData = tokensTable.getSelectionModel().getSelectedItem();
        if (rowData == null || rowData.isEmpty()) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m1"), Main.getText("ex_c_d1"));
            return;
        }

        try {
            Main.addToken(CONTEXT_ROOT, rowData.get("tokenname").toString() + ":" + rowData.get("asmarket"),
                    rowData.get("tokenid").toString(), DataClassName.TOKEN.name());
            GuiUtils.informationalAlert("", Main.getText("addwatchedSuccess"), "");
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import static com.google.common.base.Preconditions.checkState;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.core.response.MultiSignResponse;
import net.bigtangle.core.response.SearchMultiSignResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.IgnoreServiceException;
import net.bigtangle.ui.wallet.utils.TextFieldValidator;
import net.bigtangle.ui.wallet.utils.WTUtils;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.OkHttp3Util;

@SuppressWarnings("rawtypes")
public class TokenPublishController extends TokenIdentityController {

    private static final Logger log = LoggerFactory.getLogger(TokenPublishController.class);

    @FXML
    public TabPane tabPane;
    @FXML
    public Tab multiPublishTab;
    @FXML
    public Tab multisignTab;

    @FXML
    public CheckBox tokenstopCheckBox;

    @FXML
    public ComboBox<String> tokenidCB;

    @FXML
    public TextField tokennameTF;
    @FXML
    public TextField urlTF;

    @FXML
    public TextField tokenamountTF;
    @FXML
    public TextField decimalsTF;

    @FXML
    public TextArea tokendescriptionTF;

    @FXML
    public ChoiceBox<String> signAddrChoiceBox;

    @FXML
    public TextField signnumberTF;

    @FXML
    public TextField signPubkeyTF;

    @FXML
    public RadioButton tokenRB;
    @FXML
    public RadioButton domainRB;
    @FXML
    public RadioButton marketRB;
    @FXML
    public RadioButton subtangleRB;
    @FXML
    public ToggleGroup tokentypeTG;

    @FXML
    public Button saveB;

    public String address;
    public String tokenUUID;
    public String tokenidString;
    public Main.OverlayUI overlayUI;

    public void initPublishTab() {
        try {
            tokenRB.setUserData("token");
            domainRB.setUserData("domain");
            marketRB.setUserData("market");
            subtangleRB.setUserData("subtangle");
            initCombobox();
            new TextFieldValidator(signnumberTF,
                    text -> !WTUtils.didThrow(() -> checkState(text.matches("[1-9]\\d*"))));

            tokenidCB.getSelectionModel().selectedItemProperty().addListener((ov, oldv, newv) -> {

                if (newv != null && !newv.isEmpty() && !signAddrChoiceBox.getItems().contains(newv)) {
                    signAddrChoiceBox.getItems().add(newv);
                    signAddrChoiceBox.getSelectionModel().selectLast();
                }
                if (oldv != null && !oldv.isEmpty() && signAddrChoiceBox.getItems().contains(oldv)) {
                    signAddrChoiceBox.getItems().remove(oldv);
                    signAddrChoiceBox.getSelectionModel().selectLast();
                }

            });
            Main.walletAppKit.wallet().setServerURL(Main.getContextRoot());
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void saveToken(ActionEvent event) {
        try {
            String tokentype = tokentypeTG.getSelectedToggle().getUserData().toString();

            String CONTEXT_ROOT = Main.getContextRoot();

            Main.walletAppKit.wallet().setServerURL(CONTEXT_ROOT);

            List<ECKey> issuedKeys = Main.walletAppKit.wallet().walletKeys(Main.getAesKey());

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

            ECKey outKey = null;
            for (ECKey key : issuedKeys) {
                if (key.getPublicKeyAsHex().equalsIgnoreCase(tokenidCB.getValue().trim())) {
                    outKey = key;
                }
            }

            List<MultiSignAddress> addresses = new ArrayList<MultiSignAddress>();

            if (signAddrChoiceBox.getItems() != null && !signAddrChoiceBox.getItems().isEmpty()) {
                for (String pubKeyHex : signAddrChoiceBox.getItems()) {
                    // ECKey ecKey =
                    // ECKey.fromPublicOnly(Utils.HEX.decode(pubKeyHex));
                    addresses.add(new MultiSignAddress(tokenidCB.getValue().trim(), "", pubKeyHex));
                }
            }
            addresses.add(new MultiSignAddress(tokenidCB.getValue(), "", outKey.getPublicKeyAsHex()));
            if (tokentype.equals("token")) {
                BigDecimal tokenamount = new BigDecimal(tokenamountTF.getText());

                Block block = Main.walletAppKit.wallet().createToken(outKey, tokennameTF.getText(),
                        Integer.valueOf(decimalsTF.getText()), urlTF.getText(), tokendescriptionTF.getText(),
                        tokenamount.toBigInteger(), !tokenstopCheckBox.isSelected(), null, TokenType.token.ordinal(),
                        addresses);
                TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
                Main.walletAppKit.wallet().multiSign(currentToken.getToken().getTokenid(), outKey, Main.getAesKey());
            } else if (tokentype.equals("domain")) {
                List<ECKey> walletKeys = new ArrayList<>();
                walletKeys.add(outKey);
                if (signAddrChoiceBox.getItems() != null && !signAddrChoiceBox.getItems().isEmpty()) {
                    for (String pubKeyHex : signAddrChoiceBox.getItems()) {
                        if (pubKeyHex.equals(outKey.getPublicKeyAsHex())) {
                            continue;
                        }
                        ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(pubKeyHex));
                        walletKeys.add(ecKey);
                    }
                }

                Main.walletAppKit.wallet().publishDomainName(walletKeys, walletKeys.get(0), tokenidCB.getValue().trim(),
                        urlTF.getText().trim(), Main.getAesKey(), new BigInteger(tokenamountTF.getText()),
                        tokendescriptionTF.getText().trim());

                for (int i = 1; i < walletKeys.size(); i++) {
                    ECKey sighKey = walletKeys.get(i);
                    Main.walletAppKit.wallet().multiSign(tokenidCB.getValue().trim(), sighKey, Main.getAesKey());
                }
            } else if (tokentype.equals("market")) {

                Block block = Main.walletAppKit.wallet().createToken(outKey, tokennameTF.getText(), 0, urlTF.getText(),
                        tokendescriptionTF.getText(), BigInteger.ONE, !tokenstopCheckBox.isSelected(), null,
                        TokenType.market.ordinal(), addresses);
                TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
                Main.walletAppKit.wallet().multiSign(currentToken.getToken().getTokenid(), outKey, Main.getAesKey());
            } else if (tokentype.equals("subtangle")) {
                Block block = Main.walletAppKit.wallet().createToken(outKey, tokennameTF.getText(), 0, urlTF.getText(),
                        tokendescriptionTF.getText(), BigInteger.ONE, !tokenstopCheckBox.isSelected(), null,
                        TokenType.subtangle.ordinal(), addresses);
                TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
                Main.walletAppKit.wallet().multiSign(currentToken.getToken().getTokenid(), outKey, Main.getAesKey());
            }

            // tabPane.getSelectionModel().clearAndSelect(4);
        } catch (IgnoreServiceException e) {
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initCombobox() throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("name", null);
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.searchTokens.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(response, GetTokensResponse.class);

        // wallet keys minus used from token list with one time (blocktype false

        List<ECKey> keys = Main.walletAppKit.wallet().walletKeys(Main.getAesKey());
        for (ECKey key : keys) {
            String temp = Utils.HEX.encode(key.getPubKey());
            boolean flag = true;
            if (flag && !tokenData.contains(temp)) {
                tokenData.add(temp);
            }
        }
        tokenidCB.setItems(tokenData);

        tokenidCB.getSelectionModel().selectedItemProperty().addListener((ov, oldv, newv) -> {
            try {
                showToken(newv);
            } catch (Exception e) {
                GuiUtils.crashAlert(e);
            }
        });

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
        String resp = OkHttp3Util.postString(CONTEXT_ROOT + ReqCmd.getTokenSignByAddress.name(),
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

        tabPane.getSelectionModel().clearAndSelect(1);
        tokennameTF.setText(Main.getString(tokenInfo.getToken().getTokenname()).trim());
        tokenidCB.setValue(tokenid);
        String amountString = MonetaryFormat.FIAT.noCode()
                .format(new Coin(tokenInfo.getToken().getAmount(), tokenid), tokenInfo.getToken().getDecimals())
                .toString();
        tokenamountTF.setText(amountString);
        tokenstopCheckBox.setSelected(tokenInfo.getToken().isTokenstop());
        urlTF.setText(Main.getString(tokenInfo.getToken().getDomainName()).trim());
        tokendescriptionTF.setText(Main.getString(tokenInfo.getToken().getDescription()).trim());
        signnumberTF.setText(Main.getString(tokenInfo.getToken().getSignnumber()).trim());
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
        resp = OkHttp3Util.postString(CONTEXT_ROOT + ReqCmd.getTokenSigns.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        MultiSignResponse multiSignResponse2 = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        int count = multiSignResponse2.getSignCount();

        if (count == 0) {
            saveB.setDisable(true);
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
        String resp = OkHttp3Util.postString(CONTEXT_ROOT + ReqCmd.getTokenSignByAddress.name(),
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

        tabPane.getSelectionModel().clearAndSelect(1);
        tokennameTF.setText(Main.getString(tokenInfo.getToken().getTokenname()).trim());
        tokenidCB.setValue(tokenid);
        // String amountString =
        // Coin.valueOf(tokenInfo.getTokenSerial().getAmount(),
        // tokenid).toPlainString();
        // stockAmount1.setText(amountString);
        tokenstopCheckBox.setSelected(tokenInfo.getToken().isTokenstop());
        urlTF.setText(Main.getString(tokenInfo.getToken().getDomainName()).trim());
        tokendescriptionTF.setText(Main.getString(tokenInfo.getToken().getDescription()).trim());
        signnumberTF.setText(Main.getString(tokenInfo.getToken().getSignnumber()).trim());
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
        resp = OkHttp3Util.postString(CONTEXT_ROOT + ReqCmd.getTokenSigns.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        MultiSignResponse multiSignResponse2 = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        int count = multiSignResponse2.getSignCount();
        if (count == 0) {
            saveB.setDisable(true);
        }

    }

    public void showToken(String newtokenid) throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();

        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", tokenidCB.getValue());
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getTokenSignByTokenid.name(),
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
                // saveB.setDisable(false);
            } else {
                // saveB.setDisable(true);
            }
        } else {

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

    public void removeSignAddress(ActionEvent event) {
        signAddrChoiceBox.getItems().remove(signAddrChoiceBox.getValue());
    }
}

/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import static net.bigtangle.ui.wallet.utils.GuiUtils.checkGuiThread;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import com.fasterxml.jackson.core.JsonProcessingException;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.core.response.TokenIndexResponse;
import net.bigtangle.data.identity.Identity;
import net.bigtangle.data.identity.IdentityCore;
import net.bigtangle.encrypt.ECIESCoder;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.ui.wallet.utils.FileUtil;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.IgnoreServiceException;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.OkHttp3Util;

@SuppressWarnings("rawtypes")
public class TokenIdentityController extends TokenBaseController {

    private static final Logger log = LoggerFactory.getLogger(TokenIdentityController.class);

    @FXML
    public TextField identificationnumber2id;
    @FXML
    public TextField tokenname2id;
    @FXML
    public ComboBox<String> tokenid2id;

    @FXML
    public TextField surname2id;

    @FXML
    public TextField forenames2id;

    @FXML
    public TextField photo2id;

    @FXML
    public TextField sex2id;

    @FXML
    public TextField dateofissue2id;

    @FXML
    public TextField dateofexpiry2id;
    @FXML
    public TextField signnumberTF2id;

    @FXML
    public TextField signPubkeyTF2id;
    @FXML
    public ChoiceBox<String> signAddrChoiceBox2id;

    @FXML
    public void initialize2id() {
        try {
            initCombobox2id();
        } catch (Exception e) {
            e.printStackTrace();
            GuiUtils.crashAlert(e);
        }
    }

    public void initCombobox2id() throws Exception {
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
        tokenid2id.setItems(tokenData);
        tokenid2id.getSelectionModel().selectedItemProperty().addListener((ov, oldv, newv) -> {

            if (newv != null && !newv.isEmpty() && !signAddrChoiceBox2id.getItems().contains(newv)) {
                signAddrChoiceBox2id.getItems().add(newv);
                signAddrChoiceBox2id.getSelectionModel().selectLast();
            }
            if (oldv != null && !oldv.isEmpty() && signAddrChoiceBox2id.getItems().contains(oldv)) {
                signAddrChoiceBox2id.getItems().remove(oldv);
                signAddrChoiceBox2id.getSelectionModel().selectLast();
            }

        });

    }

    public void selectFile(ActionEvent event) {

        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        // final Desktop desktop = Desktop.getDesktop();
        if (file != null) {
            photo2id.setText(file.getAbsolutePath());
        } else {
            return;
        }

    }

    public void saveIdentityToken(ActionEvent event) {
        try {

            String CONTEXT_ROOT = Main.getContextRoot();

            Main.walletAppKit.wallet().setServerURL(CONTEXT_ROOT);

            List<ECKey> issuedKeys = Main.walletAppKit.wallet().walletKeys(Main.getAesKey());

            if (signnumberTF2id.getText() == null || signnumberTF2id.getText().trim().isEmpty()) {
                GuiUtils.informationalAlert("", Main.getText("signnumberNoEq"), "");
                return;
            }
            if (!signnumberTF2id.getText().matches("[1-9]\\d*")) {
                GuiUtils.informationalAlert("", Main.getText("signnumberNoEq"), "");
                return;
            }

            if (signnumberTF2id.getText() != null && !signnumberTF2id.getText().trim().isEmpty()
                    && signnumberTF2id.getText().matches("[1-9]\\d*")
                    && Long.parseLong(signnumberTF2id.getText().trim()) == 1) {

                multiSinglePublish2id(Main.getAesKey(), issuedKeys);
                return;
            }
            if (signnumberTF2id.getText() != null && !signnumberTF2id.getText().trim().isEmpty()
                    && signnumberTF2id.getText().matches("[1-9]\\d*")
                    && Long.parseLong(signnumberTF2id.getText().trim()) > signAddrChoiceBox2id.getItems().size()) {

                GuiUtils.informationalAlert("", Main.getText("signnumberNoEq"), "");
                return;
            }

            ECKey outKey = null;
            for (ECKey key : issuedKeys) {
                if (key.getPublicKeyAsHex().equalsIgnoreCase(tokenid2id.getValue().trim())) {
                    outKey = key;
                }
            }
            KeyValue kv = new KeyValue();
            kv.setKey("identity");
            Identity identity = new Identity();
            IdentityCore identityCore = new IdentityCore();
            identityCore.setSurname(surname2id.getText());
            identityCore.setForenames(forenames2id.getText());
            identityCore.setSex(sex2id.getText());
            identityCore.setDateofissue(dateofissue2id.getText());
            identityCore.setDateofexpiry(dateofexpiry2id.getText());
            identity.setIdentityCore(identityCore);
            identity.setIdentificationnumber(identificationnumber2id.getText());
            byte[] photo = FileUtil.readFile(new File(photo2id.getText()));
            identity.setPhoto(photo);
            byte[] cipher = ECIESCoder.encrypt(outKey.getPubKeyPoint(), Json.jsonmapper().writeValueAsBytes(identity));

            kv.setValue(Utils.HEX.encode(cipher));
            Block block = Main.walletAppKit.wallet().createToken(outKey, "identity", 0, "id.shop", "test",
                    BigInteger.ONE, true, kv, TokenType.identity.ordinal());
            TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
            Main.walletAppKit.wallet().multiSign(currentToken.getToken().getTokenid(), outKey, Main.getAesKey());

            // tabPane.getSelectionModel().clearAndSelect(4);
        } catch (IgnoreServiceException e) {
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    private void multiSinglePublish2id(KeyParameter aesKey, List<ECKey> issuedKeys)
            throws JsonProcessingException, Exception {
        TokenInfo tokenInfo = new TokenInfo();
        int decimals = 1;
        BigInteger amount = BigInteger.ZERO;
        Token tokens = Token.buildSimpleTokenInfo(false, null, tokenid2id.getValue().trim(),
                tokenname2id.getText().trim(), "", 1, 0, amount, true, decimals, null);

        tokenInfo.setToken(tokens);
        ECKey mykey = null;
        for (ECKey key : issuedKeys) {
            if (key.getPublicKeyAsHex().equalsIgnoreCase(tokens.getTokenid())) {
                mykey = key;
            }
        }

        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokens.getTokenid(), "", mykey.getPublicKeyAsHex()));
        Coin basecoin = MonetaryFormat.FIAT.noCode().parse("1", Utils.HEX.decode(tokenid2id.getValue()), decimals);

        Main.walletAppKit.wallet().saveToken(tokenInfo, basecoin, mykey, aesKey);
        GuiUtils.informationalAlert("", Main.getText("s_c_m"));
        Main.instance.controller.initTableView();
        checkGuiThread();
        return;
    }

    private void multiPublsih(List<ECKey> issuedKeys) throws Exception {
        ECKey outKey = null;

        outKey = issuedKeys.get(0);

        byte[] pubKey = outKey.getPubKey();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
        requestParam.put("amount",
                MonetaryFormat.FIAT.noCode().parse("1", Utils.HEX.decode(tokenid2id.getValue()), 0).getValue());
        requestParam.put("tokenname", tokenname2id.getText());
        requestParam.put("signnumber", signnumberTF2id.getText());
        requestParam.put("tokenHex", tokenid2id.getValue());
        requestParam.put("multiserial", true);
        requestParam.put("asmarket", false);
        requestParam.put("tokenstop", false);
        noSignBlock(requestParam);
        GuiUtils.informationalAlert("", Main.getText("s_c_m_sign"));
        Main.instance.controller.initTableView();
        checkGuiThread();
    }

    public void noSignBlock(HashMap<String, Object> map) throws Exception {

        List<ECKey> keys = Main.walletAppKit.wallet().walletKeys(Main.getAesKey());

        String CONTEXT_ROOT = Main.getContextRoot();

        TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", Main.getString(map.get("tokenHex")).trim());
        String resp2 = OkHttp3Util.postString(CONTEXT_ROOT + ReqCmd.getTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        Long tokenindex_ = tokenIndexResponse.getTokenindex();

        BigInteger amount = BigInteger.ONE;
        Coin basecoin = new Coin(amount, Main.getString(map.get("tokenHex")).trim());

        Token tokens = Token.buildSimpleTokenInfo(false, tokenIndexResponse.getBlockhash(),
                Main.getString(map.get("tokenHex")).trim(), Main.getString(map.get("tokenname")).trim(),
                Main.getString(map.get("description")).trim(), Integer.parseInt(this.signnumberTF2id.getText().trim()),
                tokenindex_, amount, (boolean) map.get("tokenstop"), 0, null);
        tokenInfo.setToken(tokens);
        KeyValue kv = new KeyValue();
        kv.setKey("identity");
        Identity identity = new Identity();
        IdentityCore identityCore = new IdentityCore();
        identityCore.setSurname(surname2id.getText());
        identityCore.setForenames(forenames2id.getText());
        identityCore.setSex(sex2id.getText());
        identityCore.setDateofissue(dateofissue2id.getText());
        identityCore.setDateofexpiry(dateofexpiry2id.getText());
        identity.setIdentityCore(identityCore);
        identity.setIdentificationnumber(identificationnumber2id.getText());
        byte[] photo = FileUtil.readFile(new File(photo2id.getText()));
        identity.setPhoto(photo);
        kv.setValue(Json.jsonmapper().writeValueAsString(identity));
        tokens.addKeyvalue(kv);
        if (signAddrChoiceBox2id.getItems() != null && !signAddrChoiceBox2id.getItems().isEmpty()) {
            for (String pubKeyHex : signAddrChoiceBox2id.getItems()) {
                ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(pubKeyHex));
                tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(Main.getString(map.get("tokenHex")).trim(),
                        "", ecKey.getPublicKeyAsHex()));
            }
        }

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(CONTEXT_ROOT + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = Main.params.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        ECKey key1 = null;

        key1 = keys.get(0);

        signAddrChoiceBox2id.getItems().add(key1.toAddress(Main.params).toBase58());
        List<ECKey> myEcKeys = new ArrayList<ECKey>();
        if (signAddrChoiceBox2id.getItems() != null && !signAddrChoiceBox2id.getItems().isEmpty()) {
            ObservableList<String> addresses = signAddrChoiceBox2id.getItems();
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
        String resp = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.signToken.name(), block.bitcoinSerialize());
        @SuppressWarnings("unchecked")
        HashMap<String, Object> respRes = Json.jsonmapper().readValue(resp, HashMap.class);
        int errorcode = (Integer) respRes.get("errorcode");
        if (errorcode > 0) {
            String message = (String) respRes.get("message");
            GuiUtils.informationalAlert("SIGN ERROR : " + message, Main.getText("ex_c_d1"));
            throw new IgnoreServiceException();
        }
    }

    public void addSIgnAddress2id(ActionEvent event) {
        try {
            addPubkey2id();
            // showAddAddressDialog();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void addPubkey2id() {
        String temp = signnumberTF2id.getText();
        if (temp != null && !temp.isEmpty() && temp.matches("[1-9]\\d*")) {

            int signnumber = Integer.parseInt(temp);
            if (signnumber >= 1) {
                String address = signPubkeyTF2id.getText();
                if (address != null && !address.isEmpty() && !signAddrChoiceBox2id.getItems().contains(address)) {
                    signAddrChoiceBox2id.getItems().add(address);
                    signAddrChoiceBox2id.getSelectionModel().selectLast();
                }
            }
        }
        signPubkeyTF2id.setText("");
    }

    public void removeSignAddress2id(ActionEvent event) {
        signAddrChoiceBox2id.getItems().remove(signAddrChoiceBox2id.getValue());
    }

}

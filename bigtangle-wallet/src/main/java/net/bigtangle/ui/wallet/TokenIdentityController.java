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
public class TokenIdentityController extends TokenSignsController {

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
            List<MultiSignAddress> addresses = new ArrayList<MultiSignAddress>();

            if (signAddrChoiceBox2id.getItems() != null && !signAddrChoiceBox2id.getItems().isEmpty()) {
                for (String pubKeyHex : signAddrChoiceBox2id.getItems()) {
                    ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(pubKeyHex));
                    addresses.add(new MultiSignAddress(tokenid2id.getValue().trim(), "", ecKey.getPublicKeyAsHex()));
                }
            }

            Block block = Main.walletAppKit.wallet().createToken(outKey, tokenname2id.getText(), 0, "identity.shop", "identity",
                    BigInteger.ONE, true, kv, TokenType.identity.ordinal(), addresses);
            TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
            Main.walletAppKit.wallet().multiSign(currentToken.getToken().getTokenid(), outKey, Main.getAesKey());

            // tabPane.getSelectionModel().clearAndSelect(4);
        } catch (IgnoreServiceException e) {
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
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

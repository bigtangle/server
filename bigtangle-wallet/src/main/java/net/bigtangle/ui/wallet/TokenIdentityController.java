/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.io.File;
import java.math.BigInteger;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import net.bigtangle.core.Block;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Utils;
import net.bigtangle.data.identity.Identity;
import net.bigtangle.data.identity.IdentityCore;
import net.bigtangle.data.identity.IdentityData;
import net.bigtangle.ui.wallet.utils.FileUtil;
import net.bigtangle.ui.wallet.utils.GuiUtils;

public class TokenIdentityController extends TokenSignsController {

    private static final Logger log = LoggerFactory.getLogger(TokenIdentityController.class);

    @FXML
    public TextField identificationnumber2id;
    @FXML
    public TextField tokenname2id;
    @FXML
    public TextField domainname2id;
    @FXML
    public ComboBox<String> tokenid2id;

    @FXML
    public TextField surname2id;

    @FXML
    public TextField forenames2id;

    @FXML
    public TextField photo2id;

    @FXML
    public ChoiceBox<String> sex2idCB;

    @FXML
    public DatePicker dateofissue2idDatePicker;

    @FXML
    public DatePicker dateofexpiry2idDatePicker;

    @FXML
    public TextField signnumberTF2id;

    @FXML
    public TextField signPubkeyTF2id;
    @FXML
    public ChoiceBox<String> signAddrChoiceBox2id;

    @FXML
    public void initIdentityTab() {
        try {
            sex2idCB.setItems(FXCollections.observableArrayList(Main.getText("man"), Main.getText("woman")));
            initCombobox2id();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initCombobox2id() throws Exception {
        ObservableList<String> tokenData = FXCollections.observableArrayList();
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
                    outKey = key; 
            }
            KeyValue kv = new KeyValue();
            kv.setKey("identity");
            Identity identity = new Identity();
            IdentityCore identityCore = new IdentityCore();
            identityCore.setSurname(surname2id.getText());
            identityCore.setForenames(forenames2id.getText());
            identityCore.setSex(sex2idCB.getValue());
            DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            if (dateofissue2idDatePicker.getValue() != null)
                identityCore.setDateofissue(df.format(dateofissue2idDatePicker.getValue()));
            if (dateofexpiry2idDatePicker.getValue() != null)
                identityCore.setDateofexpiry(df.format(dateofexpiry2idDatePicker.getValue()));

            IdentityData identityData = new IdentityData();
            identityData.setIdentityCore(identityCore);
            identityData.setIdentificationnumber(identificationnumber2id.getText());
            if (photo2id.getText() != null) {
                byte[] photo = FileUtil.readFile(new File(photo2id.getText()));
                identityData.setPhoto(photo);
            }
            ECKey userkey = ECKey.fromPublicOnly(Utils.HEX.decode(tokenname2id.getText().trim()));
            identity.getTokenKeyValues(outKey, userkey, identityData.toByteArray(), DataClassName.IdentityData.name());

            List<MultiSignAddress> addresses = new ArrayList<MultiSignAddress>();

            if (signAddrChoiceBox2id.getItems() != null && !signAddrChoiceBox2id.getItems().isEmpty()) {
                for (String pubKeyHex : signAddrChoiceBox2id.getItems()) {
                    // ECKey ecKey =
                    // ECKey.fromPublicOnly(Utils.HEX.decode(pubKeyHex));
                    addresses.add(new MultiSignAddress(tokenid2id.getValue().trim(), "", pubKeyHex));
                }
            }
            addresses.add(new MultiSignAddress(tokenid2id.getValue().trim(), "", outKey.getPublicKeyAsHex()));
            Block block = Main.walletAppKit.wallet().createToken(outKey, tokenname2id.getText(), 0,
                    domainname2id.getText(), "identity", BigInteger.ONE, true, kv, TokenType.identity.ordinal(),
                    addresses, new ECKey().getPublicKeyAsHex());
            TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
            Main.walletAppKit.wallet().multiSign(currentToken.getToken().getTokenid(), outKey, Main.getAesKey());

            // tabPane.getSelectionModel().clearAndSelect(4);
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

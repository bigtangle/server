/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Utils;
import net.bigtangle.encrypt.ECIESCoder;
import net.bigtangle.ui.wallet.utils.FileUtil;
import net.bigtangle.ui.wallet.utils.GuiUtils;

@SuppressWarnings("rawtypes")
public class VosController {

    private static final Logger log = LoggerFactory.getLogger(VosController.class);

    @FXML
    public TextField tokennameTF;
    @FXML
    public ComboBox<String> tokenidCB;
    @FXML
    public TextArea tokendescriptionTF;
    @FXML
    public TextField vosfileTF;
    @FXML
    public TextField urlTF;

    @FXML
    public TextField signnumberTF;

    @FXML
    public TextField signPubkeyTF;
    @FXML
    public ChoiceBox<String> signAddrChoiceBox;

    public Main.OverlayUI overlayUI;

    public void saveVosToken(ActionEvent event) {
        try {

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
            KeyValue kv = new KeyValue();
            kv.setKey("vos");

            byte[] vos = FileUtil.readFile(new File(vosfileTF.getText()));
            byte[] cipher = ECIESCoder.encrypt(outKey.getPubKeyPoint(), vos);

            kv.setValue(Utils.HEX.encode(cipher));
            List<MultiSignAddress> addresses = new ArrayList<MultiSignAddress>();

            if (signAddrChoiceBox.getItems() != null && !signAddrChoiceBox.getItems().isEmpty()) {
                for (String pubKeyHex : signAddrChoiceBox.getItems()) {
                    // ECKey ecKey =
                    // ECKey.fromPublicOnly(Utils.HEX.decode(pubKeyHex));
                    addresses.add(new MultiSignAddress(tokenidCB.getValue().trim(), "", pubKeyHex));
                }
            }
            addresses.add(new MultiSignAddress(tokenidCB.getValue().trim(), "", outKey.getPublicKeyAsHex()));
            Block block = Main.walletAppKit.wallet().createToken(outKey, tokennameTF.getText(), 0, urlTF.getText(),
                    tokendescriptionTF.getText(), BigInteger.ONE, true, kv, TokenType.identity.ordinal(), addresses);
            TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
            Main.walletAppKit.wallet().multiSign(currentToken.getToken().getTokenid(), outKey, Main.getAesKey());

            // tabPane.getSelectionModel().clearAndSelect(4);
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void selectFile(ActionEvent event) {

        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        // final Desktop desktop = Desktop.getDesktop();
        if (file != null) {
            vosfileTF.setText(file.getAbsolutePath());
        } else {
            return;
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

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

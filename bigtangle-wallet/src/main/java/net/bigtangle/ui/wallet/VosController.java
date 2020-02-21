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
    public TextField tokenname2id;
    @FXML
    public ComboBox<String> tokenid2id;

    @FXML
    public TextField vosfileTF;

    @FXML
    public TextField signnumberTF2id;

    @FXML
    public TextField signPubkeyTF2id;
    @FXML
    public ChoiceBox<String> signAddrChoiceBox2id;

    public Main.OverlayUI overlayUI;

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
            kv.setKey("vos");

            byte[] vos = FileUtil.readFile(new File(vosfileTF.getText()));
            byte[] cipher = ECIESCoder.encrypt(outKey.getPubKeyPoint(), vos);

            kv.setValue(Utils.HEX.encode(cipher));
            List<MultiSignAddress> addresses = new ArrayList<MultiSignAddress>();

            if (signAddrChoiceBox2id.getItems() != null && !signAddrChoiceBox2id.getItems().isEmpty()) {
                for (String pubKeyHex : signAddrChoiceBox2id.getItems()) {
                    ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(pubKeyHex));
                    addresses.add(new MultiSignAddress(tokenid2id.getValue().trim(), "", ecKey.getPublicKeyAsHex()));
                }
            }

            Block block = Main.walletAppKit.wallet().createToken(outKey, "identity", 0, "id.shop", "test",
                    BigInteger.ONE, true, kv, TokenType.identity.ordinal(), addresses);
            TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
            Main.walletAppKit.wallet().multiSign(currentToken.getToken().getTokenid(), outKey, Main.getAesKey());

            // tabPane.getSelectionModel().clearAndSelect(4);
        }  
        catch (Exception e) {
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

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import static net.bigtangle.ui.wallet.Main.bitcoin;
import static net.bigtangle.ui.wallet.Main.params;

import java.io.File;
import java.util.List;

import org.spongycastle.crypto.params.KeyParameter;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.FileChooser;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.ui.wallet.utils.GuiUtils;

public class EckeyController {
    @FXML
    public TableView<EckeyModel> issuedReceiveKeysTable;
    @FXML
    public TableColumn<EckeyModel, String> pubkeyColumn;
    @FXML
    public TableColumn<EckeyModel, String> addressColumn;
    @FXML
    public TableColumn<EckeyModel, String> privkeyColumn;

    @FXML
    public TextField keyFileDirTextField;
    @FXML
    public TextField keyFilePrefixTextField;

    @FXML
    public TextField newPubkeyTextField;
    @FXML
    public TextField newPrivateKeyTextField;

    private ObservableList<EckeyModel> issuedKeyData = FXCollections.observableArrayList();

    public Main.OverlayUI overlayUI;

    @FXML
    public void initialize() {
        try {
            initEcKeyList();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void addKey2wallet(ActionEvent event) {
        ECKey newKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(newPrivateKeyTextField.getText()),
                Utils.HEX.decode(newPubkeyTextField.getText()));
        bitcoin.wallet().importKey(newKey);
        bitcoin = new WalletAppKit(params, new File(Main.keyFileDirectory), Main.keyFilePrefix);
        try {
            initEcKeyList();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initEcKeyList() throws Exception {
        issuedKeyData.clear();

        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        KeyParameter aesKey = null;
        if (!"".equals(Main.password.trim())) {
            aesKey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> issuedKeys = Main.bitcoin.wallet().walletKeys(aesKey);
        if (issuedKeys != null && !issuedKeys.isEmpty()) {
            for (ECKey ecKey : issuedKeys) {
                issuedKeyData.add(new EckeyModel(ecKey.getPublicKeyAsHex(), ecKey.getPrivateKeyAsHex(),
                        ecKey.toAddress(Main.params).toBase58()));
            }
            issuedReceiveKeysTable.setItems(issuedKeyData);
            pubkeyColumn.setCellValueFactory(cellData -> cellData.getValue().pubkeyHex());
            privkeyColumn.setCellValueFactory(cellData -> cellData.getValue().privkeyHex());
            addressColumn.setCellValueFactory(cellData -> cellData.getValue().addressHex());

            pubkeyColumn.setCellFactory(TextFieldTableCell.<EckeyModel>forTableColumn());
            privkeyColumn.setCellFactory(TextFieldTableCell.<EckeyModel>forTableColumn());
            addressColumn.setCellFactory(TextFieldTableCell.<EckeyModel>forTableColumn());
        }

    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

    public void selectFile(ActionEvent event) {

        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        // final Desktop desktop = Desktop.getDesktop();
        if (file != null) {
            // try {
            // desktop.open(file);
            keyFileDirTextField.setText(file.getAbsolutePath());
            // } catch (IOException e) {

            // GuiUtils.crashAlert(e);
            // }
        }
        Main.keyFileDirectory = file.getParent() + "/";
        String filename = file.getName();

        Main.keyFilePrefix = filename.contains(".") ? filename.substring(0, filename.lastIndexOf(".")) : filename;
        bitcoin = new WalletAppKit(params, new File(Main.keyFileDirectory), Main.keyFilePrefix);
        GuiUtils.informationalAlert("set key file is ok", "", "");

        try {
            initEcKeyList();
            Main.instance.controller.initTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public ObservableList<EckeyModel> getIssuedKeyData() {
        return issuedKeyData;
    }

    public void setIssuedKeyData(ObservableList<EckeyModel> issuedKeyData) {
        this.issuedKeyData = issuedKeyData;
    }

}

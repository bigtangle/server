/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import static net.bigtangle.ui.wallet.Main.walletAppKit;
import static net.bigtangle.ui.wallet.Main.params;

import java.io.File;
import java.util.List;
import java.util.Optional;

import org.spongycastle.crypto.params.KeyParameter;

import com.google.common.collect.Lists;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
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
    // @FXML
    // public TableColumn<EckeyModel, String> privkeyColumn;

    @FXML
    public TextField keyFileDirTextField;
    @FXML
    public TextField keyFilePrefixTextField;

    @FXML
    public TextField newPubkeyTextField;
    @FXML
    public TextField newPrivateKeyTextField;

    private ObservableList<EckeyModel> issuedKeyData = FXCollections.observableArrayList();

    public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {
        try {
            keyFileDirTextField.setText(Main.keyFileDirectory + File.separator + Main.keyFilePrefix + ".wallet");
            initEcKeyList();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void addKey2wallet(ActionEvent event) {
        if (newPrivateKeyTextField.getText().equals("")) {
            return;
        }

        if (walletAppKit.wallet().isEncrypted()) {
            test("addKey");
        } else {
            addKey("");
        }

    }


    public void addKey(String password) {
        ECKey newKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(newPrivateKeyTextField.getText()),
                Utils.HEX.decode(newPubkeyTextField.getText()));
        if ("".equals(password)) {
            walletAppKit.wallet().importKey(newKey);
        } else {
            walletAppKit.wallet().importKeysAndEncrypt(Lists.newArrayList(newKey), password);
        }
        walletAppKit = new WalletAppKit(params, new File(Main.keyFileDirectory), Main.keyFilePrefix);
        try {
            initEcKeyList();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void newKey2wallet(ActionEvent event) {
        if (walletAppKit.wallet().isEncrypted()) {
            test("newKey");
        } else {
            newKey("");
        }

    }

    public void newKey(String password) {
        ECKey newKey = new ECKey();
        if ("".equals(password)) {
            walletAppKit.wallet().importKey(newKey);
        } else {
            walletAppKit.wallet().importKeysAndEncrypt(Lists.newArrayList(newKey), password);
        }
        walletAppKit = new WalletAppKit(params, new File(Main.keyFileDirectory), Main.keyFilePrefix);
        try {
            initEcKeyList();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initEcKeyList() throws Exception {
        issuedKeyData.clear();

        
        List<ECKey> issuedKeys = Main.walletAppKit.wallet().walletKeys(Main.getAesKey());
        if (issuedKeys != null && !issuedKeys.isEmpty()) {
            for (ECKey ecKey : issuedKeys) {
                issuedKeyData.add(new EckeyModel(ecKey.getPublicKeyAsHex(), ecKey.getPrivateKeyAsHex(),
                        ecKey.toAddress(Main.params).toBase58()));
            }
            issuedReceiveKeysTable.setItems(issuedKeyData);
            pubkeyColumn.setCellValueFactory(cellData -> cellData.getValue().pubkeyHex());
            // privkeyColumn.setCellValueFactory(cellData ->
            // cellData.getValue().privkeyHex());
            addressColumn.setCellValueFactory(cellData -> cellData.getValue().addressHex());

            pubkeyColumn.setCellFactory(TextFieldTableCell.<EckeyModel>forTableColumn());
            // privkeyColumn.setCellFactory(TextFieldTableCell.<EckeyModel>forTableColumn());
            addressColumn.setCellFactory(TextFieldTableCell.<EckeyModel>forTableColumn());
        }

    }

    public void test(String methodName) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(Main.getText("Enter_password"));
        dialog.setHeaderText(null);

        ButtonType loginButtonType = new ButtonType(Main.getText("OK"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        PasswordField password = new PasswordField();

        grid.add(new Label(Main.getText("Password")), 0, 0);
        grid.add(password, 1, 0);
 

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return password.getText();
            }
            return "";
        });
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(usernamePassword -> {
            final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.walletAppKit.wallet().getKeyCrypter();
            try {
                if (Main.password.trim().equals(usernamePassword.trim())) {

                    if ("showKey".equals(methodName)) {
                        keyCrypter.deriveKey(usernamePassword.trim());
                        showKey();
                    } else if ("addKey".equals(methodName)) {
                        addKey(Main.password);
                    } else if ("newKey".equals(methodName)) {
                        newKey(Main.password);
                    }
                } else {
                    Alert alert = new Alert(AlertType.WARNING);
                    alert.setWidth(500);
                    alert.setTitle("");
                    alert.setHeaderText(null);
                    alert.setContentText(Main.getText("wrongpassword"));

                    alert.showAndWait();
                }
            } catch (Exception e) {
                Alert alert = new Alert(AlertType.WARNING);
                alert.setWidth(500);
                alert.setTitle("");
                alert.setHeaderText(null);
                alert.setContentText(Main.getText("wrongpassword"));

                alert.showAndWait();
            }
        });
    }

    public void showPrivateKey(ActionEvent event) {
        EckeyModel temp = issuedReceiveKeysTable.getSelectionModel().getSelectedItem();
        if (temp == null) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m"), Main.getText("ex_c_m1"));
            return;
        }
        if (walletAppKit.wallet().isEncrypted()) {
            test("showKey");
        } else {
            showKey();
        }
    }

    public void showKey() {
        EckeyModel temp = issuedReceiveKeysTable.getSelectionModel().getSelectedItem();
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setWidth(500);
        alert.setTitle("");
        alert.setHeaderText(null);
        alert.setContentText(temp.getPrivkeyHex());

        alert.showAndWait();
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

    public void selectFile(ActionEvent event) {

        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        // final Desktop desktop = Desktop.getDesktop();
        if (file != null) {
            keyFileDirTextField.setText(file.getAbsolutePath());
        } else {
            return;
        }
        Main.keyFileDirectory = file.getParent() + "/";
        String filename = file.getName();

        Main.keyFilePrefix = filename.contains(".") ? filename.substring(0, filename.lastIndexOf(".")) : filename;
        walletAppKit = new WalletAppKit(params, new File(Main.keyFileDirectory), Main.keyFilePrefix);
        GuiUtils.informationalAlert("", Main.getText("e_c"), "");
        Main.password = "";
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

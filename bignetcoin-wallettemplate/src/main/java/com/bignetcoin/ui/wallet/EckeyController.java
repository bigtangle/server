package com.bignetcoin.ui.wallet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.wallet.DecryptingKeyBag;
import org.bitcoinj.wallet.DeterministicKeyChain;

import com.bignetcoin.ui.wallet.utils.GuiUtils;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.FileChooser;

public class EckeyController {
    @FXML
    public TableView<EckeyModel> issuedReceiveKeysTable;
    @FXML
    public TableColumn<EckeyModel, String> pubkeyColumn;
    @FXML
    public TableColumn<EckeyModel, String> addressColumn;

    @FXML
    public TableView<EckeyModel> importedKeysTable;
    @FXML
    public TableColumn<EckeyModel, String> pubkeyColumnA;
    @FXML
    public TableColumn<EckeyModel, String> addressColumnA;

    @FXML
    public TextField keyFileDirTextField;
    @FXML
    public TextField keyFilePrefixTextField;

    private ObservableList<EckeyModel> issuedKeyData = FXCollections.observableArrayList();
    private ObservableList<EckeyModel> importedKeyData = FXCollections.observableArrayList();

    public Main.OverlayUI overlayUI;

    @FXML
    public void initialize() {
        initEcKeyList();
    }

    public void initEcKeyList() {
        issuedKeyData.clear();
        importedKeyData.clear();
        DecryptingKeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(Main.bitcoin.wallet(), null);
        List<ECKey> issuedKeys = new ArrayList<>();
        for (ECKey key : Main.bitcoin.wallet().getImportedKeys()) {
            ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
            System.out.println(
                    "realKey, pubKey : " + ecKey.getPublicKeyAsHex() + ", prvKey : " + ecKey.getPrivateKeyAsHex());
            issuedKeys.add(ecKey);
        }
        for (DeterministicKeyChain chain : Main.bitcoin.wallet().getKeyChainGroup().getDeterministicKeyChains()) {
            for (ECKey key : chain.getLeafKeys()) {
                ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
                System.out.println(
                        "realKey, pubKey : " + ecKey.getPublicKeyAsHex() + ", priKey : " + ecKey.getPrivateKeyAsHex());
                issuedKeys.add(ecKey);
            }
        }
        List<ECKey> importedKeys = Main.bitcoin.wallet().getImportedKeys();
        if (issuedKeys != null && !issuedKeys.isEmpty()) {
            for (ECKey ecKey : issuedKeys) {
                issuedKeyData.add(new EckeyModel(ecKey.getPublicKeyAsHex(), ecKey.toAddress(Main.params).toBase58()));
            }
            issuedReceiveKeysTable.setItems(issuedKeyData);
            pubkeyColumn.setCellValueFactory(cellData -> cellData.getValue().pubkeyHex());
            addressColumn.setCellValueFactory(cellData -> cellData.getValue().addressHex());

            pubkeyColumn.setCellFactory(TextFieldTableCell.<EckeyModel>forTableColumn());
            pubkeyColumn.setCellFactory(TextFieldTableCell.<EckeyModel>forTableColumn());
        }
        if (importedKeys != null && !importedKeys.isEmpty()) {
            for (ECKey ecKey : importedKeys) {
                importedKeyData.add(new EckeyModel(ecKey.getPublicKeyAsHex(), ecKey.toAddress(Main.params).toBase58()));
            }
            importedKeysTable.setItems(importedKeyData);
            pubkeyColumnA.setCellValueFactory(cellData -> cellData.getValue().pubkeyHex());
            addressColumnA.setCellValueFactory(cellData -> cellData.getValue().addressHex());
            pubkeyColumnA.setCellFactory(TextFieldTableCell.<EckeyModel>forTableColumn());
            pubkeyColumnA.setCellFactory(TextFieldTableCell.<EckeyModel>forTableColumn());
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
        Main.keyFileDirectory = file.getParent()+"/";
        String filename = file.getName();

        Main.keyFilePrefix = filename.contains(".") ? filename.substring(0, filename.lastIndexOf(".")) : filename;

        GuiUtils.informationalAlert("set key file is ok", "", "");

        initEcKeyList();
    }

    public ObservableList<EckeyModel> getIssuedKeyData() {
        return issuedKeyData;
    }

    public void setIssuedKeyData(ObservableList<EckeyModel> issuedKeyData) {
        this.issuedKeyData = issuedKeyData;
    }

    public ObservableList<EckeyModel> getImportedKeyData() {
        return importedKeyData;
    }

    public void setImportedKeyData(ObservableList<EckeyModel> importedKeyData) {
        this.importedKeyData = importedKeyData;
    }

}

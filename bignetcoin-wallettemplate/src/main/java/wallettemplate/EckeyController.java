package wallettemplate;

import java.util.List;

import org.bitcoinj.core.ECKey;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

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

    private ObservableList<EckeyModel> issuedKeyData = FXCollections.observableArrayList();
    private ObservableList<EckeyModel> importedKeyData = FXCollections.observableArrayList();

    public Main.OverlayUI overlayUI;

    @FXML
    public void initialize() throws Exception {
        List<ECKey> issuedKeys = Main.bitcoin.wallet().getIssuedReceiveKeys();
        List<ECKey> importedKeys = Main.bitcoin.wallet().getImportedKeys();
        if (issuedKeys != null && !issuedKeys.isEmpty()) {
            for (ECKey ecKey : issuedKeys) {
                issuedKeyData.add(new EckeyModel(ecKey.getPublicKeyAsHex(), ecKey.toAddress(Main.params).toBase58()));
            }
            issuedReceiveKeysTable.setItems(issuedKeyData);
            pubkeyColumn.setCellValueFactory(cellData -> cellData.getValue().pubkeyHex());
            addressColumn.setCellValueFactory(cellData -> cellData.getValue().addressHex());
        }
        if (importedKeys != null && !importedKeys.isEmpty()) {
            for (ECKey ecKey : importedKeys) {
                importedKeyData.add(new EckeyModel(ecKey.getPublicKeyAsHex(), ecKey.toAddress(Main.params).toBase58()));
            }
            importedKeysTable.setItems(importedKeyData);
            pubkeyColumnA.setCellValueFactory(cellData -> cellData.getValue().pubkeyHex());
            addressColumnA.setCellValueFactory(cellData -> cellData.getValue().addressHex());
        }
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
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

/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.util.Map;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import net.bigtangle.ui.wallet.utils.GuiUtils;

public class VOSController {

    @FXML
    public TableView<Map<String, Object>> vosTable;
    @FXML
    public TableColumn<Map<String, Object>, String> addressCol;
    @FXML
    public TableColumn<Map<String, Object>, String> numberCol;

    @FXML
    public TableColumn<Map<String, Object>, String> priceCol;
    @FXML
    public TableColumn<Map<String, Object>, String> frequenceCol;

    @FXML
    public TableColumn<Map<String, Object>, String> urlCol;
    @FXML
    public TableColumn<Map<String, Object>, String> contentCol;

    @FXML
    public ComboBox<String> addressComboBox;
    @FXML
    public TextField numberTF;
    @FXML
    public TextField priceTF;

    @FXML
    public TextField frequenceTF;
    @FXML
    public TextField urlTF;

    @FXML
    public TextArea contentTA;

    public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {
        try {

        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void saveUserdata(ActionEvent event) {

    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

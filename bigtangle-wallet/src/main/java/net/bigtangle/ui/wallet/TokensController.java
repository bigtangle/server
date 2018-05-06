/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.MapValueFactory;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Utils;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.OkHttp3Util;

public class TokensController {
    @FXML
    public TableView<Map> tokensTable;
    @FXML
    public TableColumn<Map, String> tokenHexColumn;
    @FXML
    public TableColumn<Map, String> tokennameColumn;
    @FXML
    public TableColumn<Map, Number> amountColumn;
    @FXML
    public TableColumn<Map, Number> blocktypeColumn;
    @FXML
    public TableColumn<Map, String> descriptionColumn;

    @FXML
    public TableView<Map> positveTokensTable;
    @FXML
    public TableColumn<Map, String> positveTokenHexColumn;
    @FXML
    public TableColumn<Map, String> positveTokennameColumn;
    @FXML
    public TextField nameTextField;
    private static final Logger log = LoggerFactory.getLogger(TokensController.class);

    @FXML
    public void initialize() {

        try {
            initTableView();
            initPositveTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void searchTokens(ActionEvent event) {
        try {
            initTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initPositveTableView() throws Exception {
        List<String> tokens = Main.initToken4file();
        ObservableList<Map> tokenData = FXCollections.observableArrayList();
        if (tokens != null && !tokens.isEmpty()) {
            for (String temp : tokens) {
                // ONLY log System.out.println("temp:" + temp);
                if (!temp.equals("")) {
                    Map map = new HashMap();
                    map.put("tokenHex", temp.split(",")[0]);
                    map.put("tokenname", temp.split(",")[1]);
                    tokenData.add(map);
                }

            }
            positveTokennameColumn.setCellValueFactory(new MapValueFactory("tokenname"));
            positveTokenHexColumn.setCellValueFactory(new MapValueFactory("tokenHex"));
            positveTokensTable.setItems(tokenData);
        }
    }

    public void removePositvle(ActionEvent event) {
        Map<String, Object> rowData = positveTokensTable.getSelectionModel().getSelectedItem();
        if (rowData == null || rowData.isEmpty()) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m1"), Main.getText("ex_c_d1"));
        }
        String tokenHex = Main.getString(rowData.get("tokenHex"));
        String tokenname = Main.getString(rowData.get("tokenname"));
        try {
            String myPositvleTokens = Main.getString4file(Main.keyFileDirectory + "Main.positiveFile");
            log.debug(myPositvleTokens);
            myPositvleTokens.replace(tokenHex + "," + tokenname, "");
            File temp = new File(Main.keyFileDirectory + Main.positiveFile);
            if (temp.exists()) {
                temp.delete();
            }
            Main.addText2file(myPositvleTokens, Main.keyFileDirectory + Main.positiveFile);
            initPositveTableView();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void initTableView() throws Exception {
        String name = nameTextField.getText();
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<Map> tokenData = FXCollections.observableArrayList();
      
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("name", Main.getString(name));
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokens",
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");
        if (list != null) {
            for (Map<String, Object> map : list) {
                Coin fromAmount = Coin.valueOf(Long.parseLong(map.get("amount").toString()),
                        Utils.HEX.decode((String) map.get("tokenHex")));
                map.put("amount", fromAmount.toPlainString());
                tokenData.add(map);
            }
        }
        tokennameColumn.setCellValueFactory(new MapValueFactory("tokenname"));
        amountColumn.setCellValueFactory(new MapValueFactory("amount"));
        descriptionColumn.setCellValueFactory(new MapValueFactory("description"));
        blocktypeColumn.setCellValueFactory(new MapValueFactory("blocktype"));
        tokenHexColumn.setCellValueFactory(new MapValueFactory("tokenHex"));
        tokensTable.setItems(tokenData);
    }
}

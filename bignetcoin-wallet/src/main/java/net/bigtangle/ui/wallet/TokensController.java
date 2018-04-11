package net.bigtangle.ui.wallet;

import java.util.List;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.MapValueFactory;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
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
    public void initialize() {

        try {
            initTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initTableView() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<Map> tokenData = FXCollections.observableArrayList();
        ECKey ecKey = Main.bitcoin.wallet().currentReceiveKey();
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokens", ecKey.getPubKeyHash());

        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");
        for (Map<String, Object> map : list) {
            tokenData.add(map);
        }
        tokennameColumn.setCellValueFactory(new MapValueFactory("tokenname"));
        amountColumn.setCellValueFactory(new MapValueFactory("amount"));
        descriptionColumn.setCellValueFactory(new MapValueFactory("description"));
        blocktypeColumn.setCellValueFactory(new MapValueFactory("blocktype"));
        tokenHexColumn.setCellValueFactory(new MapValueFactory("tokenHex"));
        tokensTable.setItems(tokenData);
    }
}

/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.MapValueFactory;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Json;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.OkHttp3Util;

@SuppressWarnings("rawtypes")
public class TokenSearchController extends TokenBaseController {
    @FXML
    public TableView<Map> tokensTable;
    @FXML
    public TableColumn<Map, String> tokenHexColumn;
    @FXML
    public TableColumn<Map, String> tokennameColumn;
    @FXML
    public TableColumn<Map, String> amountColumn;
    @FXML
    public TableColumn<Map, Number> blocktypeColumn;
    @FXML
    public TableColumn<Map, String> descriptionColumn;
    @FXML
    public TableColumn<Map, String> urlColumn;
    @FXML
    public TableColumn<Map, Number> signnumberColumn;
    @FXML
    public TableColumn<Map, String> multiserialColumn;
    @FXML
    public TableColumn<Map, String> asmarketColumn;
    @FXML
    public TableColumn<Map, String> tokenstopColumn;

    @FXML
    public TextField nameTextField;

    @FXML
    public TextField tokenidTF;
    @FXML
    public CheckBox isSignCheckBox;
    public static Map<String, Boolean> multiMap = new HashMap<String, Boolean>();
    private static final Logger log = LoggerFactory.getLogger(TokenSearchController.class);

    public void initSearchTab() {
        this.isSignCheckBox.setSelected(true);
        try {
            initSearchResultTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void searchTokens(ActionEvent event) {
        try {

            initSearchResultTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    @SuppressWarnings({ "unchecked" })
    public void initSearchResultTableView() throws Exception {
        String name = nameTextField.getText();
        String CONTEXT_ROOT = Main.getContextRoot();
        ObservableList<Map> tokenData = FXCollections.observableArrayList();

        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("name", Main.getString(name));
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.searchTokens.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        log.debug(response);
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

        List<Map<String, Object>> listTokens = (List<Map<String, Object>>) data.get("tokens");
        Map<String, Object> amountMap = (Map<String, Object>) data.get("amountMap");
        if (listTokens != null) {
            for (Map<String, Object> map : listTokens) {
                // multiMap.put((String) map.get("tokenid"), (boolean)
                // map.get("multiserial"));
                // String temp = ((boolean) map.get("multiserial")) ?
                // Main.getText("yes") : Main.getText("no");
                int tokentype = (int) map.get("tokentype");
                String temp1 = Main.getText("Token");
                if (tokentype == 1) {
                    temp1 = Main.getText("market");
                }
                if (tokentype == 2) {
                    temp1 = Main.getText("subtangle");
                }
                if (tokentype == 3) {
                    temp1 = Main.getText("domainname");
                }
                String temp2 = ((boolean) map.get("tokenstop")) ? Main.getText("yes") : Main.getText("no");
                // map.put("multiserial", temp);
                map.put("asmarket", temp1);
                map.put("tokenstop", temp2);
                if (amountMap.containsKey(map.get("tokenid"))) {
                    long count = Long.parseLong(amountMap.get((String) map.get("tokenid")).toString());
                    Coin fromAmount = Coin.valueOf(count, (String) map.get("tokenid"));
                    String amountString = MonetaryFormat.FIAT.noCode().format(fromAmount, (int) map.get("decimals"));
                    if (amountString.startsWith("0"))
                        amountString = "";
                    map.put("amount", amountString);
                } else {
                    map.put("amount", "");
                }

                tokenData.add(map);
            }
        }
        tokennameColumn.setCellValueFactory(new MapValueFactory("tokennameDisplay"));
        amountColumn.setCellValueFactory(new MapValueFactory("amount"));
        descriptionColumn.setCellValueFactory(new MapValueFactory("description"));
        blocktypeColumn.setCellValueFactory(new MapValueFactory("blocktype"));
        tokenHexColumn.setCellValueFactory(new MapValueFactory("tokenid"));

        urlColumn.setCellValueFactory(new MapValueFactory("url"));
        signnumberColumn.setCellValueFactory(new MapValueFactory("signnumber"));
        multiserialColumn.setCellValueFactory(new MapValueFactory("multiserial"));
        asmarketColumn.setCellValueFactory(new MapValueFactory("asmarket"));
        tokenstopColumn.setCellValueFactory(new MapValueFactory("tokenstop"));

        tokensTable.setItems(tokenData);
    }

}

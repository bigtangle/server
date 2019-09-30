/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Token;
import net.bigtangle.core.response.SearchMultiSignResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.OkHttp3Util;

@SuppressWarnings("rawtypes")
public class TokenBaseController {
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
    public TableView<Map> positveTokensTable;
    @FXML
    public TableColumn<Map, String> positveTokenHexColumn;
    @FXML
    public TableColumn<Map, String> positveTokennameColumn;

    @FXML
    public TableView<Map> tokenserialTable;
    @FXML
    public TableColumn<Map, String> tokenidColumn;
    @FXML
    public TableColumn<Map, Number> tokenindexColumn;
    @FXML
    public TableColumn<Map, String> tokenAmountColumn;
    @FXML
    public TableColumn<Map, String> signnumColumn;
    @FXML
    public TableColumn<Map, String> realSignnumColumn;
    @FXML
    public TableColumn<Map, String> isSignAllColumn;
    @FXML
    public TableColumn<Map, String> isMySignColumn;
    @FXML
    public TableColumn<Map, String> multiTokennameColumn;

    @FXML
    public TextField nameTextField;

    @FXML
    public TextField tokenidTF;
    @FXML
    public CheckBox isSignCheckBox;
    public static Map<String, Boolean> multiMap = new HashMap<String, Boolean>();
    private static final Logger log = LoggerFactory.getLogger(TokenBaseController.class);

    @FXML
    public void initialize() {
        this.isSignCheckBox.setSelected(true);
        try {
            initTableView();
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

 

    public Token getToken(List<Token> tokens, String tokenid) throws Exception {
        for (Token t : tokens) {
            if (t.getTokenid().equals(tokenid))
                return t;
        }
        return null;
    }

    @SuppressWarnings({ "unchecked" })
    public void initTableView() throws Exception {
        String name = nameTextField.getText();
        String CONTEXT_ROOT = Main.getContextRoot();
        ObservableList<Map> tokenData = FXCollections.observableArrayList();

        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("name", Main.getString(name));
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.searchTokens.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        log.debug(response);
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");
        Map<String, Object> amountMap = (Map<String, Object>) data.get("amountMap");
        if (list != null) {
            for (Map<String, Object> map : list) {
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

    @SuppressWarnings({ "unchecked" })
    public void initMultisignTableView() throws Exception {

        String CONTEXT_ROOT = Main.getContextRoot();
        ObservableList<Map> tokenData = FXCollections.observableArrayList();

        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", tokenidTF.getText());
        List<ECKey> keys = Main.walletAppKit.wallet().walletKeys(Main.getAesKey());
        List<String> addresses = keys.stream().map(key -> key.toAddress(Main.params).toBase58())
                .collect(Collectors.toList());
        requestParam.put("addresses", addresses);
        requestParam.put("isSign", isSignCheckBox.isSelected());
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getMultiSignWithTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        log.debug(response);

        final SearchMultiSignResponse searchMultiSignResponse = Json.jsonmapper().readValue(response,
                SearchMultiSignResponse.class);
        if (searchMultiSignResponse.getMultiSignList() != null) {
            for (Map<String, Object> map : searchMultiSignResponse.getMultiSignList()) {
                int signnumber = (Integer) map.get("signnumber");
                int signcount = (Integer) map.get("signcount");
                if (signnumber <= signcount) {
                    map.put("isSignAll", Main.getText("yes"));
                } else {
                    map.put("isSignAll", Main.getText("no"));
                }
                tokenData.add(map);
            }
        }
        multiTokennameColumn.setCellValueFactory(new MapValueFactory("tokenname"));
        tokenidColumn.setCellValueFactory(new MapValueFactory("tokenid"));
        tokenindexColumn.setCellValueFactory(new MapValueFactory("tokenindex"));
        tokenAmountColumn.setCellValueFactory(new MapValueFactory("amount"));
        signnumColumn.setCellValueFactory(new MapValueFactory("signnumber"));
        isMySignColumn.setCellValueFactory(new MapValueFactory("sign"));
        realSignnumColumn.setCellValueFactory(new MapValueFactory("signcount"));
        isSignAllColumn.setCellValueFactory(new MapValueFactory("isSignAll"));
        tokenserialTable.setItems(tokenData);
    }
}

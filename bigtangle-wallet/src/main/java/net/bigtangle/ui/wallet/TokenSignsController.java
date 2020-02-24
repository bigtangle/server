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
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Token;
import net.bigtangle.core.response.SearchMultiSignResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.OkHttp3Util;

@SuppressWarnings("rawtypes")
public class TokenSignsController extends TokenSearchController{
  
    //sign table 
    
  
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
    private static final Logger log = LoggerFactory.getLogger(TokenSignsController.class);


    public void searchTokens(ActionEvent event) {
        try {

            initMultisignTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
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
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getTokenSignByTokenid.name(),
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

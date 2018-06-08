/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.util.HashMap;
import java.util.Map;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import net.bigtangle.core.Block;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.OkHttp3Util;

public class UserdataController {

    @FXML
    public TableView<Map<String, Object>> wachtedTokenTableview;
    @FXML
    public TableColumn<Map<String, Object>, String> tokennameColumn;
    @FXML
    public TableColumn<Map<String, Object>, String> tokenidColumn;
    @FXML
    public TableView<Map<String, Object>> linkmanTableview;
    @FXML
    public TableColumn<Map<String, Object>, String> linkmanColumn;
    @FXML
    public TableColumn<Map<String, Object>, String> linkaddressColumn;

    @FXML
    public TextField dataclass;
    public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {
        try {
            initTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void saveUserdata(ActionEvent event) {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        try {
            HashMap<String, String> requestParam = new HashMap<String, String>();
            String resp000 = OkHttp3Util.postString(CONTEXT_ROOT + "getGenesisBlockLR",
                    Json.jsonmapper().writeValueAsString(requestParam));

            HashMap<String, Object> result000 = Json.jsonmapper().readValue(resp000, HashMap.class);
            String leftBlockHex = (String) result000.get("leftBlockHex");
            String rightBlockHex = (String) result000.get("rightBlockHex");

            Block r1 = Main.params.getDefaultSerializer().makeBlock(Utils.HEX.decode(leftBlockHex));
            Block r2 = Main.params.getDefaultSerializer().makeBlock(Utils.HEX.decode(rightBlockHex));
            long blocktype0 = NetworkParameters.BLOCKTYPE_USERDATA;
            Block block = new Block(Main.params, r1.getHash(), r2.getHash(), blocktype0,
                    Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));
            ECKey pubKeyTo = Main.bitcoin.wallet().currentReceiveKey();

            block.solve();

            OkHttp3Util.post(CONTEXT_ROOT + "saveBlock", block.bitcoinSerialize());

        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

    public void removeToken(ActionEvent event) {
    }

    public void removeLinkman(ActionEvent event) {
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void initTableView() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ECKey pubKeyTo = Main.bitcoin.wallet().currentReceiveKey();
        HashMap<String, String> requestParam = new HashMap<String, String>();
        requestParam.put("dataclassname", DataClassName.USERDATA.name());
        requestParam.put("pubKey", Utils.HEX.encode(pubKeyTo.getPubKey()));
        byte[] buf = OkHttp3Util.post(CONTEXT_ROOT + "getUserData", Json.jsonmapper().writeValueAsString(requestParam));
    }
}

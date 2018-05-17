/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import static net.bigtangle.ui.wallet.utils.GuiUtils.checkGuiThread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.spongycastle.crypto.params.KeyParameter;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.OkHttp3Util;

public class StockController extends TokensController {
    @FXML
    public TabPane tabPane;

    @FXML
    public CheckBox multiserialCheckBox;
    @FXML
    public CheckBox asmarketCheckBox;
    @FXML
    public CheckBox tokenstopCheckBox;

    @FXML
    public ComboBox<String> tokenid;

    @FXML
    public TextField stockName;

    @FXML
    public TextField stockAmount;

    @FXML
    public TextField urlTF;
    @FXML
    public TextField signnumberTF;

    @FXML
    public TextArea stockDescription;

    public Main.OverlayUI overlayUI;

    @FXML
    public void initialize() {
        try {
            tabPane.getSelectionModel().selectedItemProperty().addListener((ov, t, t1) -> {
                try {
                    initPositveTableView();
                } catch (Exception e) {

                }
            });
            initTableView();
            initPositveTableView();
            initCombobox();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initCombobox() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("name", null);
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokens",
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");
        List<String> names = new ArrayList<String>();
        // wallet keys minus used from token list with one time (blocktype false
        KeyParameter aeskey = null;
        // Main.initAeskey(aeskey);
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
        if (!"".equals(Main.password.trim())) {
            aeskey = keyCrypter.deriveKey(Main.password);
        }
        List<ECKey> keys = Main.bitcoin.wallet().walletKeys(aeskey);
        for (ECKey key : keys) {
            String temp = Utils.HEX.encode(key.getPubKeyHash());
            boolean flag = true;
            for (Map<String, Object> map : list) {
                String tokenHex = (String) map.get("tokenid");
                if (temp.equals(tokenHex)) {
                    if (!(boolean) map.get("multiserial")) {
                        flag = false;
                    }
                }
            }
            if (flag && !tokenData.contains(temp)) {
                tokenData.add(temp);
            }
        }
        tokenid.setItems(tokenData);
        // tokenid.getSelectionModel().selectedIndexProperty().addListener((ov,
        // oldv, newv) -> {
        // stockName.setText(names.get(newv.intValue()));
        // });

    }

    public void saveStock(ActionEvent event) {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ECKey outKey = Main.bitcoin.wallet().currentReceiveKey();

        try {
            byte[] pubKey = outKey.getPubKey();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
            requestParam.put("amount",
                    Coin.parseCoin(stockAmount.getText(), Utils.HEX.decode(tokenid.getValue())).getValue());
            requestParam.put("tokenname", stockName.getText());
            requestParam.put("url", urlTF.getText());
            requestParam.put("signnumber", signnumberTF.getText());
            requestParam.put("description", stockDescription.getText());
            requestParam.put("tokenHex", tokenid.getValue());
            requestParam.put("multiserial", multiserialCheckBox.selectedProperty().get());
            requestParam.put("asmarket", asmarketCheckBox.selectedProperty().get());
            requestParam.put("tokenstop", tokenstopCheckBox.selectedProperty().get());

            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "createGenesisBlock",
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block block = Main.params.getDefaultSerializer().makeBlock(data);
            // TODO no post to off tangle data, send it to kafka for broadcast
            Main.instance.sendMessage(block.bitcoinSerialize());

            GuiUtils.informationalAlert(Main.getText("s_c_m"), Main.getText("s_c_m"));
            Main.instance.controller.initTableView();
            checkGuiThread();
            initTableView();
            overlayUI.done();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public void add2positve(ActionEvent event) {
        Map<String, Object> rowData = tokensTable.getSelectionModel().getSelectedItem();
        if (rowData == null || rowData.isEmpty()) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m1"), Main.getText("ex_c_d1"));
            return;
        }
        String tokeninfo = "";
        tokeninfo += Main.getString(rowData.get("tokenHex")) + "," + Main.getString(rowData.get("tokenname"));
        try {
            Main.addText2file(tokeninfo, Main.keyFileDirectory + Main.positiveFile);
        } catch (Exception e) {

        }
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

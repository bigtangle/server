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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.OkHttp3Util;

public class StockController extends TokensController {
    @FXML
    public CheckBox firstPublishCheckBox;
    @FXML
    public ComboBox<String> tokenid;

    @FXML
    public TextField stockName;

    @FXML
    public TextField stockAmount;
    @FXML
    public TextArea stockDescription;

    public Main.OverlayUI overlayUI;

    @FXML
    public void initialize() {
        firstPublishCheckBox.setAllowIndeterminate(false);
        try {
            initTableView();
            initCombobox();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void initCombobox() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        ECKey ecKey = Main.bitcoin.wallet().currentReceiveKey();
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokens", ecKey.getPubKeyHash());

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
            for (Map<String, Object> map : list) {

                String tokenHex = (String) map.get("tokenHex");
                int blocktype = (int) map.get("blocktype");
                if (blocktype == NetworkParameters.BLOCKTYPE_GENESIS && !temp.equals(tokenHex)
                        && !tokenData.contains(temp)) {
                    tokenData.add(temp);
                    break;
                    // names.add(map.get("tokenname").toString());
                }
                if (blocktype == NetworkParameters.BLOCKTYPE_GENESIS_MULTIPLE && !tokenData.contains(temp)) {
                    tokenData.add(temp);
                    break;
                    // names.add(map.get("tokenname").toString());
                }

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
            requestParam.put("description", stockDescription.getText());
            requestParam.put("tokenHex", tokenid.getValue());
            requestParam.put("blocktype", firstPublishCheckBox.selectedProperty().get());

            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "createGenesisBlock",
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block block = Main.params.getDefaultSerializer().makeBlock(data);

            // Main.sentEmpstyBlock(Main.numberOfEmptyBlocks);
            GuiUtils.informationalAlert(Main.getText("s_c_m"), Main.getText("s_c_m"));
            Main.instance.controller.initTableView();
            checkGuiThread();
            initTableView();
            overlayUI.done();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

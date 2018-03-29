/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.ui.wallet;

import java.util.HashMap;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.OkHttp3Util;

import com.bignetcoin.ui.wallet.utils.GuiUtils;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class StockController {
    @FXML
    public TextField stockName;
    @FXML
    public TextField stockCode;
    @FXML
    public TextField stockAmount;
    @FXML
    public TextArea stockDescription;

    public Main.OverlayUI overlayUI;

    public void saveStock(ActionEvent event) {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ECKey outKey = Main.bitcoin.wallet().currentReceiveKey();
           
        try {
            byte[] pubKey = outKey.getPubKey();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
            requestParam.put("amount", 100000L);
            requestParam.put("tokenname", "Test");
            requestParam.put("description", "Test");
            
            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "createGenesisBlock", Json.jsonmapper().writeValueAsString(requestParam));
            Block block = Main.params.getDefaultSerializer().makeBlock(data);
            
      
            stockCode.setText(block.getTokenid() + "");
            GuiUtils.informationalAlert("token publish is ok", "", "");
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void closeStock(ActionEvent event) {
        overlayUI.done();
    }

}

/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.ui.wallet;

import java.nio.ByteBuffer;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.ECKey;
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
        byte[] pubKey = outKey.getPubKey();
        ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 4 + pubKey.length);
        byteBuffer.putInt(Integer.parseInt(stockAmount.getText()));
        byteBuffer.putInt(pubKey.length);
        byteBuffer.put(pubKey);
        try {
            byte[] blockByte = OkHttp3Util.postByte(CONTEXT_ROOT + "createGenesisBlock", byteBuffer.array());
            Block block = Main.params.getDefaultSerializer().makeBlock(blockByte);
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

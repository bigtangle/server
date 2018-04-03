/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.ui.wallet;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.OkHttp3Util;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.MissingSigsMode;

import com.bignetcoin.ui.wallet.utils.GuiUtils;
import com.fasterxml.jackson.core.JsonProcessingException;

import javafx.event.ActionEvent;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

public class ExchangeController {
    public TextField stockAddress;
    public TextField stockCode;
    public TextField stockNumber;

    public TextField coinAddress;
    public TextField coinAmount;
    public TextField coinTokenid;
    public Main.OverlayUI overlayUI;

    @SuppressWarnings("deprecation")
    public void exchangeCoin(ActionEvent event) throws Exception {
        String ContextRoot = "http://" + Main.IpAddress + ":" + Main.port + "/";
        
        String fromAddress = "";
        String fromTokenHex = "";
        String fromAmount = "";

        String toAddress = "";
        String toTokenHex = "";
        String toAmount = "";

        Coin amountCoin0 = Coin.parseCoin(toAmount, Utils.HEX.decode(toTokenHex));
        Coin amountCoin1 = Coin.parseCoin(fromAmount, Utils.HEX.decode(fromTokenHex));
        SendRequest req = SendRequest.to(new Address(Main.params, fromAddress), amountCoin0);
        req.tx.addOutput(amountCoin1, new Address(Main.params, toAddress));
        req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
        
        List<UTXO> outputs = new ArrayList<UTXO>();
        List<TransactionOutput> candidates = Main.bitcoin.wallet().transforSpendCandidates(outputs);
        Main.bitcoin.wallet().setServerURL(ContextRoot);
        Main.bitcoin.wallet().completeTx(req, candidates, false);
        Main.bitcoin.wallet().signTransaction(req);

//        SendRequest request = SendRequest.forTx(transaction);
//        walletAppKit1.wallet().signTransaction(request);
        
        exchangeTokenComplete(ContextRoot, new Address(Main.params, toAddress), req.tx);
    }

    private void exchangeTokenComplete(String ContextRoot, Address toAddress, Transaction transaction) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(ContextRoot + "askTransaction", Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = Main.params.getDefaultSerializer().makeBlock(data);
        
        rollingBlock.addTransaction(transaction);
        rollingBlock.solve();
        
        OkHttp3Util.post(ContextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
    }

    public void importBlock(ActionEvent event) {
        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        // final Desktop desktop = Desktop.getDesktop();
        if (file != null) {
            ByteArrayOutputStream bos = null;
            BufferedInputStream in = null;
            bos = new ByteArrayOutputStream((int) file.length());
            try {
                in = new BufferedInputStream(new FileInputStream(file));
                int buf_size = 1024;
                byte[] buffer = new byte[buf_size];
                int len = 0;
                while (-1 != (len = in.read(buffer, 0, buf_size))) {
                    bos.write(buffer, 0, len);
                }
                byte[] blockByte = bos.toByteArray();
            } catch (Exception e) {
                GuiUtils.crashAlert(e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                        if (bos != null) {
                            bos.close();
                        }
                    } catch (IOException e) {
                        GuiUtils.crashAlert(e);
                    }
                }

            }
        }

    }

    public void exportBlock(ActionEvent event) {
        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showSaveDialog(null);
        if (file == null)
            return;
        if (file.exists()) {// �ļ��Ѵ��ڣ���ɾ�������ļ�
            file.delete();
        }
        byte[] blockByte = null;
        BufferedOutputStream bos = null;
        FileOutputStream fos = null;

        try {

            fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(blockByte);

        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        } finally {
            try {
                if (bos != null) {
                    bos.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                GuiUtils.crashAlert(e);
            }
        }

    }

    public void refund(ActionEvent event) {
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

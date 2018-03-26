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

import com.bignetcoin.ui.wallet.utils.GuiUtils;

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

    public void exchangeCoin(ActionEvent event) {

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
        if (file.exists()) {// 文件已存在，则删除覆盖文件
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

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

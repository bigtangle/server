/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.ui.wallet;

import javafx.event.ActionEvent;
import javafx.scene.control.TextField;

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

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

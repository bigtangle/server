/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.event.ActionEvent;

public class TokenController extends TokenPublishController {

    private static final Logger log = LoggerFactory.getLogger(TokenController.class);

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }
}

/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.event.ActionEvent;
import net.bigtangle.core.DataClassName;
import net.bigtangle.ui.wallet.utils.GuiUtils;

public class TokenController extends TokenPublishController {

    private static final Logger log = LoggerFactory.getLogger(TokenController.class);

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

    @SuppressWarnings("unchecked")
    public void add2positve(ActionEvent event) {
        String CONTEXT_ROOT = Main.getContextRoot();
        Map<String, Object> rowData = tokensTable.getSelectionModel().getSelectedItem();
        if (rowData == null || rowData.isEmpty()) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m1"), Main.getText("ex_c_d1"));
            return;
        }

        try {
            Main.addToken(CONTEXT_ROOT, rowData.get("tokenname").toString() + ":" + rowData.get("asmarket"),
                    rowData.get("tokenid").toString(), DataClassName.TOKEN.name());
            GuiUtils.informationalAlert("", Main.getText("addwatchedSuccess"), "");
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }
}

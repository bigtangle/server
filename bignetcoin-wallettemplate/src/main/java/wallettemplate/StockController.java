package wallettemplate;

import javafx.event.ActionEvent;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class StockController {
    public TextField stockName;
    public TextField stockCode;
    public TextField stockNumber;
    public TextArea stockDescription;
    
    public Main.OverlayUI overlayUI;
    
    public void saveStock(ActionEvent event) {

    }
    public void closeStock(ActionEvent event) {
        overlayUI.done();
    }

}

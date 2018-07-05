/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.spongycastle.crypto.params.KeyParameter;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.MapToBeanMapperUtil;
import net.bigtangle.utils.OkHttp3Util;

public class BlockEvaluationController {
    @FXML
    public TableView<Map> blockEvaluationTable;
    @FXML
    public TableColumn<Map, String> blockhashColumn;
    @FXML
    public TableColumn<Map, String> ratingColumn;
    @FXML
    public TableColumn<Map, Number> depthColumn;
    @FXML
    public TableColumn<Map, Number> cumulativeWeightColumn;
    @FXML
    public TableColumn<Map, Number> heightColumn;

    @FXML
    public TableColumn<Map, String> solidColumn;
    @FXML
    public TableColumn<Map, String> milestoneColumn;
    @FXML
    public TableColumn<Map, String> maintainedColumn;
    @FXML
    public TableColumn<Map, String> rewardValidColumn;
    @FXML
    public TableColumn<Map, String> milestoneLastUpdateTimeColumn;
    @FXML
    public TableColumn<Map, Number> milestoneDepthColumn;
    @FXML
    public TableColumn<Map, String> insertTimeColumn;

    @FXML
    public ComboBox<String> addressComboBox;
    @FXML
    public TextField latestAmountTextField;
    @FXML
    public TextField compareTF1;
    @FXML
    public TextField compareTF2;

    public List<String> hashList = new ArrayList<String>();
    public List<String> hashList1 = new ArrayList<String>();
    public List<String> hashList2 = new ArrayList<String>();

    public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {
        try {
            initTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    private void initCompare() throws Exception {
        List<Map<String, Object>> mainList = this.getBlockInfos(Main.getContextRoot());
        if (mainList != null && !mainList.isEmpty()) {

            for (Map<String, Object> map : mainList) {
                if (!hashList.contains(map.get("blockHexStr").toString())) {
                    hashList.add(map.get("blockHexStr").toString());
                }

            }
        } else {
            mainList = new ArrayList<Map<String, Object>>();
        }
        List<Map<String, Object>> compareList1 = null;
        List<Map<String, Object>> compareList2 = null;
        int length = mainList.size();
        int length1 = 0;
        int length2 = 0;
        if (compareTF1.getText() != null && !compareTF1.getText().isEmpty()) {
            compareList1 = this.getBlockInfos(compareTF1.getText().trim());

            if (compareList1 != null && !compareList1.isEmpty()) {
                length1 = compareList1.size();
                mainList.addAll(compareList1);
            }

        }
        if (compareTF2.getText() != null && !compareTF2.getText().isEmpty()) {
            compareList2 = this.getBlockInfos(compareTF1.getText().trim());

            if (compareList2 != null && !compareList2.isEmpty()) {
                length2 = compareList2.size();
                mainList.addAll(compareList2);
            }
        }

        for (Map<String, Object> map : mainList) {

        }
    }

    private void initMainServer() throws Exception {
        List<Map<String, Object>> mainList = this.getBlockInfos(Main.getContextRoot());
    }

    private List<Map<String, Object>> getBlockInfos(String server) throws Exception {
        String CONTEXT_ROOT = server;
        String lastestAmount = latestAmountTextField.getText();
        String address = addressComboBox.getValue();
        List<String> addresses = new ArrayList<String>();
        if (address == null || address.equals("")) {
            KeyParameter aesKey = null;
            // Main.initAeskey(aesKey);
            final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
            if (!"".equals(Main.password.trim())) {
                aesKey = keyCrypter.deriveKey(Main.password);
            }
            List<ECKey> keys = Main.bitcoin.wallet().walletKeys(aesKey);
            for (ECKey key : keys) {
                addresses.add(key.toAddress(Main.params).toString());
            }
        } else {
            addresses.add(address);
        }
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("address", addresses);
        requestParam.put("lastestAmount", lastestAmount);
        String response = OkHttp3Util.postString(CONTEXT_ROOT + "searchBlock",
                Json.jsonmapper().writeValueAsString(requestParam));
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> temp = (List<Map<String, Object>>) data.get("evaluations");
        return temp;

    }

    public void searchBlock(ActionEvent event) {
        try {
            initTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

    public void showBlock(ActionEvent event) throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        Map<String, Object> rowData = blockEvaluationTable.getSelectionModel().getSelectedItem();
        if (rowData == null || rowData.isEmpty()) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m"), Main.getText("ex_c_m1"));
            return;
        }
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("hashHex", Main.getString(rowData.get("hash")));

        byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "getBlock", Json.jsonmapper().writeValueAsString(requestParam));
        Block re = Main.params.getDefaultSerializer().makeBlock(data);
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setHeight(800);
        alert.setWidth(800);
        alert.setTitle("");
        alert.setHeaderText(null);
        alert.setResizable(true);
        String blockinfo = Main.block2string(re);
        alert.setContentText(blockinfo);

        alert.showAndWait();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void initTableView() throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        String lastestAmount = latestAmountTextField.getText();
        String address = addressComboBox.getValue();
        List<String> addresses = new ArrayList<String>();
        if (address == null || address.equals("")) {
            KeyParameter aesKey = null;
            // Main.initAeskey(aesKey);
            final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
            if (!"".equals(Main.password.trim())) {
                aesKey = keyCrypter.deriveKey(Main.password);
            }
            List<ECKey> keys = Main.bitcoin.wallet().walletKeys(aesKey);
            for (ECKey key : keys) {
                addresses.add(key.toAddress(Main.params).toString());
            }
        } else {
            addresses.add(address);
        }
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("address", addresses);
        requestParam.put("lastestAmount", lastestAmount);
        String response = OkHttp3Util.postString(CONTEXT_ROOT + "searchBlock",
                Json.jsonmapper().writeValueAsString(requestParam));
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> temp = (List<Map<String, Object>>) data.get("evaluations");
        if (temp != null && !temp.isEmpty()) {

            List<BlockEvaluation> list = temp.stream().map(map -> MapToBeanMapperUtil.parseBlockEvaluation(map))
                    .collect(Collectors.toList());
            ObservableList<Map> allData = FXCollections.observableArrayList();
            if (list != null && !list.isEmpty()) {
                for (BlockEvaluation blockEvaluation : list) {
                    Map<String, Object> dataRow = new HashMap<>();
                    dataRow.put("hash",
                            blockEvaluation.getBlockhash() == null ? "" : blockEvaluation.getBlockhash().toString());
                    dataRow.put("rating", blockEvaluation.getRating());
                    dataRow.put("depth", blockEvaluation.getDepth());
                    dataRow.put("cumulativeWeight", blockEvaluation.getCumulativeWeight());
                    dataRow.put("height", blockEvaluation.getHeight());

                    dataRow.put("solid", blockEvaluation.isSolid() ? Main.getText("yes") : Main.getText("no"));
                    dataRow.put("milestone", blockEvaluation.isMilestone() ? Main.getText("yes") : Main.getText("no"));
                    dataRow.put("milestoneDepth", blockEvaluation.getMilestoneDepth());
                    dataRow.put("maintained",
                            blockEvaluation.isMaintained() ? Main.getText("yes") : Main.getText("no"));
                    dataRow.put("rewardValid",
                            blockEvaluation.isRewardValid() ? Main.getText("yes") : Main.getText("no"));
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    java.util.Date date = new Date(blockEvaluation.getMilestoneLastUpdateTime());
                    String str = sdf.format(date);
                    dataRow.put("milestoneLastUpdateTime", str);
                    date = new Date(blockEvaluation.getInsertTime());
                    str = sdf.format(date);
                    dataRow.put("insertTime", str);

                    allData.add(dataRow);
                }
                blockhashColumn.setCellValueFactory(new MapValueFactory("hash"));
                ratingColumn.setCellValueFactory(new MapValueFactory("rating"));
                depthColumn.setCellValueFactory(new MapValueFactory("depth"));
                cumulativeWeightColumn.setCellValueFactory(new MapValueFactory("cumulativeWeight"));
                heightColumn.setCellValueFactory(new MapValueFactory("height"));

                solidColumn.setCellValueFactory(new MapValueFactory("solid"));
                milestoneColumn.setCellValueFactory(new MapValueFactory("milestone"));
                milestoneDepthColumn.setCellValueFactory(new MapValueFactory("milestoneDepth"));
                maintainedColumn.setCellValueFactory(new MapValueFactory("maintained"));
                rewardValidColumn.setCellValueFactory(new MapValueFactory("rewardValid"));
                milestoneLastUpdateTimeColumn.setCellValueFactory(new MapValueFactory("milestoneLastUpdateTime"));
                insertTimeColumn.setCellValueFactory(new MapValueFactory("insertTime"));

                blockhashColumn.setCellFactory(TextFieldTableCell.forTableColumn());
            }
            blockEvaluationTable.setItems(allData);
        }
    }
}

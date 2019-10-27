/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.response.GetBlockEvaluationsResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.ui.wallet.utils.BlockFormat;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.OkHttp3Util;

public class BlockEvaluationController {
    @SuppressWarnings("rawtypes")
    @FXML
    public TableView<Map> blockEvaluationTable;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, String> blockhashColumn;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, String> ratingColumn;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, Number> depthColumn;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, Number> cumulativeWeightColumn;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, Number> heightColumn;

    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, String> blocktypeColumn;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, String> milestoneColumn;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, String> maintainedColumn;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, String> rewardValidColumn;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, String> milestoneLastUpdateTimeColumn;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, Number> milestoneDepthColumn;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, String> insertTimeColumn;

    @FXML
    public ComboBox<String> addressComboBox;
    @FXML
    public TextField latestAmountTextField;

    @FXML
    public ComboBox<String> addressComboBox1;
    @FXML
    public TextField latestAmountTextField1;

    @FXML
    public TextField compareTF1;
    @FXML
    public TextField compareTF2;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableView<Map> compareTable;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, Object> blockhashColumn1;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, Object> ratingColumn1;
    @SuppressWarnings("rawtypes")
    @FXML
    public TableColumn<Map, Object> cumulativeWeightColumn1;
    @FXML
    public TabPane tabPane;
    public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {
        try {
            initTableView();
            tabPane.getSelectionModel().selectedIndexProperty().addListener((ov, t, t1) -> {
                int index = t1.intValue();
                switch (index) {
                case 0: {
                }

                    break;
                case 1: {
                    initCompare();
                }

                    break;

                }
            });
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void initCompare() {
        try {
            Map<String, String> keyMap = new HashMap<String, String>();
            List<BlockEvaluationDisplay> blockEvaluations = this.getBlockInfos(Main.getContextRoot());
            if (blockEvaluations != null && !blockEvaluations.isEmpty()) {
                for (BlockEvaluation blockEvaluation : blockEvaluations) {
                    String key = blockEvaluation.getBlockHexStr();
                    if (!keyMap.containsKey(key)) {
                        keyMap.put(key, "m-0-0");
                    }
                }
            } else {
                blockEvaluations = new ArrayList<BlockEvaluationDisplay>();
            }

            List<BlockEvaluationDisplay> compareList1 = new ArrayList<BlockEvaluationDisplay>();
            List<BlockEvaluationDisplay> compareList2 = new ArrayList<BlockEvaluationDisplay>();

            if (compareTF1.getText() != null && !compareTF1.getText().isEmpty()) {
                compareList1 = this.getBlockInfos(compareTF1.getText().trim());
                if (compareList1 != null && !compareList1.isEmpty()) {
                    for (BlockEvaluation blockEvaluation : compareList1) {
                        String key = blockEvaluation.getBlockHexStr();
                        if (!keyMap.containsKey(key)) {
                            keyMap.put(key, "0-c1-0");
                        } else {
                            keyMap.put(key, "m-c1-0");
                        }
                    }
                    blockEvaluations.addAll(compareList1);
                }
            }
            if (compareTF2.getText() != null && !compareTF2.getText().isEmpty()) {
                compareList2 = this.getBlockInfos(compareTF1.getText().trim());
                if (compareList2 != null && !compareList2.isEmpty()) {
                    for (BlockEvaluation blockEvaluation : compareList2) {
                        String key = blockEvaluation.getBlockHexStr();
                        if (!keyMap.containsKey(key)) {
                            keyMap.put(key, "0-0-c2");
                        } else {
                            String value = keyMap.get(key).substring(0, keyMap.get(key).length() - 1);
                            keyMap.put(key, value + "c2");
                        }
                    }
                    blockEvaluations.addAll(compareList1);
                }
            }
            Collections.sort(blockEvaluations, new Comparator<BlockEvaluation>() {

                @Override
                public int compare(BlockEvaluation o1, BlockEvaluation o2) {
                    String k1 = o1.getBlockHexStr();
                    String k2 = o2.getBlockHexStr();

                    return k1.compareTo(k2);
                }
            });
            ObservableList<Map> allData = FXCollections.observableArrayList();
            for (int i = 0; i < blockEvaluations.size(); i++) {
                BlockEvaluationDisplay blockEvaluation = blockEvaluations.get(i);
                String tempKey = blockEvaluation.getBlockHexStr();
                String tempValue = keyMap.get(tempKey);
                String tempRating = String.valueOf(blockEvaluation.getRating());
                String tempCumulativeWeight = String.valueOf(blockEvaluation.getCumulativeWeight());

                Map<String, Object> map = new HashMap<String, Object>();

                map.put("blockHexStr", blockEvaluation.getBlockHexStr());
                map.put("rating", blockEvaluation.getRating());
                map.put("depth", blockEvaluation.getDepth());
                map.put("cumulativeWeight", blockEvaluation.getCumulativeWeight());

                map.put("height", blockEvaluation.getHeight());
                map.put("milestone", blockEvaluation.getMilestone());
                map.put("milestoneLastUpdateTime", blockEvaluation.getMilestoneLastUpdateTime());
                map.put("milestoneDepth", "");
                map.put("insertTime", blockEvaluation.getInsertTime());
                map.put("maintained", "");
                map.put("blocktype", blockEvaluation.getBlockType().name());
                if (tempValue.equalsIgnoreCase("m-c1-c2")) {
                    BlockEvaluation map1 = blockEvaluations.get(i + 1);
                    String tempRating1 = String.valueOf(map1.getRating());
                    String tempCumulativeWeight1 = String.valueOf(map1.getCumulativeWeight());

                    BlockEvaluation map2 = blockEvaluations.get(i + 2);
                    String tempRating2 = String.valueOf(map2.getRating());
                    String tempCumulativeWeight2 = String.valueOf(map2.getCumulativeWeight());
                    map.put("rating", tempRating + ";" + tempRating1 + ";" + tempRating2);
                    map.put("cumulativeWeight",
                            tempCumulativeWeight + ";" + tempCumulativeWeight1 + ";" + tempCumulativeWeight2);
                    i = i + 2;
                } else if (tempValue.equalsIgnoreCase("m-c1-0")) {
                    BlockEvaluation map1 = blockEvaluations.get(i + 1);
                    String tempRating1 = String.valueOf(map1.getRating());
                    String tempCumulativeWeight1 = String.valueOf(map1.getCumulativeWeight());

                    String tempRating2 = "-";
                    String tempCumulativeWeight2 = "-";
                    map.put("rating", tempRating + ";" + tempRating1 + ";" + tempRating2);
                    map.put("cumulativeWeight",
                            tempCumulativeWeight + ";" + tempCumulativeWeight1 + ";" + tempCumulativeWeight2);
                    i = i + 1;
                } else if (tempValue.equalsIgnoreCase("m-0-c2")) {
                    BlockEvaluation map2 = blockEvaluations.get(i + 1);
                    String tempRating1 = "-";
                    String tempCumulativeWeight1 = "-";

                    String tempRating2 = String.valueOf(map2.getRating());
                    String tempCumulativeWeight2 = String.valueOf(map2.getCumulativeWeight());
                    map.put("rating", tempRating + ";" + tempRating1 + ";" + tempRating2);
                    map.put("cumulativeWeight",
                            tempCumulativeWeight + ";" + tempCumulativeWeight1 + ";" + tempCumulativeWeight2);
                    i = i + 1;
                } else if (tempValue.equalsIgnoreCase("0-c1-c2")) {
                    BlockEvaluation map1 = blockEvaluations.get(i + 1);
                    String tempRating1 = String.valueOf(map1.getRating());
                    String tempCumulativeWeight1 = String.valueOf(map1.getCumulativeWeight());

                    map.put("rating", "-;" + tempRating + ";" + tempRating1);
                    map.put("cumulativeWeight", "-;" + tempCumulativeWeight + ";" + tempCumulativeWeight1);
                    i = i + 1;
                } else if (tempValue.equalsIgnoreCase("m-0-0")) {

                    map.put("rating", tempRating + ";-;-");
                    map.put("cumulativeWeight", tempCumulativeWeight + ";-;-");

                } else if (tempValue.equalsIgnoreCase("0-c1-0")) {
                    map.put("rating", "-;" + tempRating + ";-");
                    map.put("cumulativeWeight", "-;" + tempCumulativeWeight + ";-");
                } else if (tempValue.equalsIgnoreCase("0-0-c2")) {
                    map.put("rating", "-;-;" + tempRating);
                    map.put("cumulativeWeight", "-;-;" + tempCumulativeWeight);
                }
                allData.add(map);
            }
            if (allData != null && !allData.isEmpty()) {
                blockhashColumn1.setCellValueFactory(new MapValueFactory("blockHexStr"));
                ratingColumn1.setCellValueFactory(new MapValueFactory("rating"));
                cumulativeWeightColumn1.setCellValueFactory(new MapValueFactory("cumulativeWeight"));
                compareTable.setItems(allData);
            }
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    private List<BlockEvaluationDisplay> getBlockInfos(String server) throws Exception {
        String CONTEXT_ROOT = server;
        String lastestAmount = latestAmountTextField1.getText();
        String address = addressComboBox1.getValue();
        List<String> addresses = new ArrayList<String>();
        if (address == null || address.equals("")) {

            List<ECKey> keys = Main.walletAppKit.wallet().walletKeys(Main.getAesKey());
            for (ECKey key : keys) {
                addresses.add(key.toAddress(Main.params).toString());
            }
        } else {
            addresses.add(address);
        }
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("address", addresses);
        requestParam.put("lastestAmount", lastestAmount);
        String response = OkHttp3Util.postString(CONTEXT_ROOT + "/" + ReqCmd.findBlockEvaluation.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
        return getBlockEvaluationsResponse.getEvaluations();
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

    public void searchCompare(ActionEvent event) {
        initCompare();
    }

    public void showBlock(ActionEvent event) throws Exception {
        String CONTEXT_ROOT = Main.getContextRoot();
        @SuppressWarnings("unchecked")
        Map<String, Object> rowData = blockEvaluationTable.getSelectionModel().getSelectedItem();
        if (rowData == null || rowData.isEmpty()) {
            GuiUtils.informationalAlert(Main.getText("ex_c_m"), Main.getText("ex_c_m1"));
            return;
        }
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("hashHex", Main.getString(rowData.get("hash")));

        byte[] data = OkHttp3Util.postAndGetBlock(CONTEXT_ROOT + ReqCmd.getBlockByHash.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block re = Main.params.getDefaultSerializer().makeBlock(data);
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setHeight(800);
        alert.setWidth(800);
        alert.setTitle("");
        alert.setHeaderText(null);
        alert.setResizable(true);
        String blockinfo = BlockFormat.block2string(re, Main.params);
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

            List<ECKey> keys = Main.walletAppKit.wallet().walletKeys(Main.getAesKey());
            for (ECKey key : keys) {
                addresses.add(key.toAddress(Main.params).toString());
            }
        } else {
            addresses.add(address);
        }
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("address", addresses);
        requestParam.put("lastestAmount", lastestAmount);
        String response = OkHttp3Util.postString(CONTEXT_ROOT + ReqCmd.findBlockEvaluation.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
        List<BlockEvaluationDisplay> blockEvaluations = getBlockEvaluationsResponse.getEvaluations();

        ObservableList<Map> allData = FXCollections.observableArrayList();
        if (blockEvaluations != null && !blockEvaluations.isEmpty()) {
            for (BlockEvaluationDisplay blockEvaluation : blockEvaluations) {
                Map<String, Object> dataRow = new HashMap<>();
                dataRow.put("hash",
                        blockEvaluation.getBlockHash() == null ? "" : blockEvaluation.getBlockHash().toString());
                dataRow.put("rating", blockEvaluation.getRating());
                dataRow.put("depth", blockEvaluation.getDepth());
                dataRow.put("cumulativeWeight", blockEvaluation.getCumulativeWeight());
                dataRow.put("height", blockEvaluation.getHeight());

                dataRow.put("milestone", blockEvaluation.getMilestone());
                dataRow.put("milestoneDepth", "");
                dataRow.put("blocktype", blockEvaluation.getBlockType().name());

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

            milestoneColumn.setCellValueFactory(new MapValueFactory("milestone"));
            // milestoneDepthColumn.setCellValueFactory(new
            // MapValueFactory("milestoneDepth"));
            // maintainedColumn.setCellValueFactory(new
            // MapValueFactory("maintained"));

            milestoneLastUpdateTimeColumn.setCellValueFactory(new MapValueFactory("milestoneLastUpdateTime"));
            insertTimeColumn.setCellValueFactory(new MapValueFactory("insertTime"));
            blocktypeColumn.setCellValueFactory(new MapValueFactory("blocktype"));
            blockhashColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        }
        blockEvaluationTable.setItems(allData);
    }
}

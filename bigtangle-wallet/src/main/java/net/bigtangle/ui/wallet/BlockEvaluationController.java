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
import java.util.stream.Collectors;

import org.spongycastle.crypto.params.KeyParameter;

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
    public ComboBox<String> addressComboBox1;
    @FXML
    public TextField latestAmountTextField1;
    
    
    @FXML
    public TextField compareTF1;
    @FXML
    public TextField compareTF2;

    @FXML
    public TableView<Map> compareTable;
    @FXML
    public TableColumn<Map, Object> blockhashColumn1;
    @FXML
    public TableColumn<Map, Object> ratingColumn1;

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

    private void initCompare() {
        try {
            Map<String, String> keyMap = new HashMap<String, String>();
            List<Map<String, Object>> mainList = this.getBlockInfos(Main.getContextRoot());
            if (mainList != null && !mainList.isEmpty()) {

                for (Map<String, Object> map : mainList) {
                    String key = map.get("blockHexStr").toString();
                    if (!keyMap.containsKey(key)) {
                        keyMap.put(key, "m-0-0");
                    }

                }
            } else {
                mainList = new ArrayList<Map<String, Object>>();
            }
            List<Map<String, Object>> compareList1 = new ArrayList<Map<String, Object>>();
            List<Map<String, Object>> compareList2 = new ArrayList<Map<String, Object>>();

            if (compareTF1.getText() != null && !compareTF1.getText().isEmpty()) {
                compareList1 = this.getBlockInfos(compareTF1.getText().trim());

                if (compareList1 != null && !compareList1.isEmpty()) {
                    for (Map<String, Object> map : compareList1) {
                        String key = map.get("blockHexStr").toString();
                        if (!keyMap.containsKey(key)) {
                            keyMap.put(key, "0-c1-0");
                        } else {
                            keyMap.put(key, "m-c1-0");
                        }

                    }
                    mainList.addAll(compareList1);
                }

            }
            if (compareTF2.getText() != null && !compareTF2.getText().isEmpty()) {
                compareList2 = this.getBlockInfos(compareTF1.getText().trim());

                if (compareList2 != null && !compareList2.isEmpty()) {
                    for (Map<String, Object> map : compareList2) {
                        String key = map.get("blockHexStr").toString();
                        if (!keyMap.containsKey(key)) {
                            keyMap.put(key, "0-0-c2");
                        } else {
                            String value = keyMap.get(key).substring(0, keyMap.get(key).length() - 1);
                            keyMap.put(key, value + "c2");
                        }

                    }
                    mainList.addAll(compareList2);
                }
            }
            Collections.sort(mainList, new Comparator<Map<String, Object>>() {

                @Override
                public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                    String k1 = o1.get("blockHexStr").toString();
                    String k2 = o2.get("blockHexStr").toString();

                    return k1.compareTo(k2);
                }
            });
            ObservableList<Map> allData = FXCollections.observableArrayList();
            for (int i = 0; i < mainList.size(); i++) {
                Map<String, Object> map = mainList.get(i);
                String tempKey = map.get("blockHexStr").toString();
                String tempValue = keyMap.get(tempKey);
                String tempRating = map.get("rating").toString();
                String tempCumulativeWeight = map.get("cumulativeWeight").toString();
                if (tempValue.equalsIgnoreCase("m-c1-c2")) {
                    Map<String, Object> map1 = mainList.get(i + 1);
                    String tempRating1 = map1.get("rating").toString();
                    String tempCumulativeWeight1 = map1.get("cumulativeWeight").toString();

                    Map<String, Object> map2 = mainList.get(i + 2);
                    String tempRating2 = map2.get("rating").toString();
                    String tempCumulativeWeight2 = map2.get("cumulativeWeight").toString();
                    map.put("rating", tempRating + ";" + tempRating1 + ";" + tempRating2);
                    map.put("cumulativeWeight",
                            tempCumulativeWeight + ";" + tempCumulativeWeight1 + ";" + tempCumulativeWeight2);
                    i = i + 2;
                } else if (tempValue.equalsIgnoreCase("m-c1-0")) {
                    Map<String, Object> map1 = mainList.get(i + 1);
                    String tempRating1 = map1.get("rating").toString();
                    String tempCumulativeWeight1 = map1.get("cumulativeWeight").toString();

                    String tempRating2 = "-";
                    String tempCumulativeWeight2 = "-";
                    map.put("rating", tempRating + ";" + tempRating1 + ";" + tempRating2);
                    map.put("cumulativeWeight",
                            tempCumulativeWeight + ";" + tempCumulativeWeight1 + ";" + tempCumulativeWeight2);
                    i = i + 1;
                } else if (tempValue.equalsIgnoreCase("m-0-c2")) {
                    Map<String, Object> map2 = mainList.get(i + 1);
                    String tempRating1 = "-";
                    String tempCumulativeWeight1 = "-";

                    String tempRating2 = map2.get("rating").toString();
                    String tempCumulativeWeight2 = map2.get("cumulativeWeight").toString();
                    map.put("rating", tempRating + ";" + tempRating1 + ";" + tempRating2);
                    map.put("cumulativeWeight",
                            tempCumulativeWeight + ";" + tempCumulativeWeight1 + ";" + tempCumulativeWeight2);
                    i = i + 1;
                } else if (tempValue.equalsIgnoreCase("0-c1-c2")) {
                    Map<String, Object> map1 = mainList.get(i + 1);
                    String tempRating1 = map1.get("rating").toString();
                    String tempCumulativeWeight1 = map1.get("cumulativeWeight").toString();

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

    private void initMainServer() throws Exception {
        List<Map<String, Object>> mainList = this.getBlockInfos(Main.getContextRoot());
    }

    private List<Map<String, Object>> getBlockInfos(String server) throws Exception {
        String CONTEXT_ROOT = server;
        String lastestAmount = latestAmountTextField1.getText();
        String address = addressComboBox1.getValue();
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

    public void searchCompare(ActionEvent event) {
        initCompare();
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
                            blockEvaluation.getBlockHash() == null ? "" : blockEvaluation.getBlockHash().toString());
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

package net.bigtangle.ui.wallet;

import static net.bigtangle.ui.wallet.Main.bitcoin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.Json;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.MapToBeanMapperUtil;
import net.bigtangle.utils.OkHttp3Util;

public class BlockEvaluationController {
    @FXML
    public TableView<Map> blockEvaluationTable;
    @FXML
    public TableColumn<Map, String> blockhashColumn;
    @FXML
    public TableColumn<Map, Number> ratingColumn;
    @FXML
    public TableColumn<Map, Number> depthColumn;
    @FXML
    public TableColumn<Map, Number> cumulativeWeightColumn;
    @FXML
    public TableColumn<Map, Number> heightColumn;

    public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {

        try {
            initTableView();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

    public void initTableView() throws Exception {
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        String response = OkHttp3Util.post(CONTEXT_ROOT + "getAllEvaluations",
                bitcoin.wallet().currentReceiveKey().getPubKeyHash());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> temp = (List<Map<String, Object>>) data.get("evaluations");
      /*  if (temp != null && !temp.isEmpty()) {
            ObservableList<Map> allData = FXCollections.observableArrayList();
            for (Map<String, Object> evaluationMap : temp) {
                Map<String, Object> dataRow = new HashMap<>();
                dataRow.put("hash", ((Map<String, String>) evaluationMap.get("blockhash")).get("bytes"));
                dataRow.put("rating", evaluationMap.get("rating"));
                dataRow.put("depth", evaluationMap.get("depth"));
                dataRow.put("cumulativeWeight", evaluationMap.get("cumulativeWeight"));
                dataRow.put("height", evaluationMap.get("height"));
                allData.add(dataRow);
            }
            blockhashColumn.setCellValueFactory(new MapValueFactory("hash"));
            ratingColumn.setCellValueFactory(new MapValueFactory("rating"));
            depthColumn.setCellValueFactory(new MapValueFactory("depth"));
            cumulativeWeightColumn.setCellValueFactory(new MapValueFactory("cumulativeWeight"));
            heightColumn.setCellValueFactory(new MapValueFactory("height"));

            blockhashColumn.setCellFactory(TextFieldTableCell.forTableColumn());

            blockEvaluationTable.setItems(allData);
        }*/
      List<BlockEvaluation> list = temp.stream().map(map -> MapToBeanMapperUtil.parseBlockEvaluation(map))
                .collect(Collectors.toList());
        if (list != null && !list.isEmpty()) {
            ObservableList<Map> allData = FXCollections.observableArrayList();
            for (BlockEvaluation blockEvaluation : list) {
                Map<String, Object> dataRow = new HashMap<>();
                dataRow.put("hash",
                        blockEvaluation.getBlockhash() == null ? "" : blockEvaluation.getBlockhash().toString());
                dataRow.put("rating", blockEvaluation.getRating());
                dataRow.put("depth", blockEvaluation.getDepth());
                dataRow.put("cumulativeWeight", blockEvaluation.getCumulativeWeight());
                dataRow.put("height", blockEvaluation.getHeight());
                allData.add(dataRow);
            }
            blockhashColumn.setCellValueFactory(new MapValueFactory("hash"));
            ratingColumn.setCellValueFactory(new MapValueFactory("rating"));
            depthColumn.setCellValueFactory(new MapValueFactory("depth"));
            cumulativeWeightColumn.setCellValueFactory(new MapValueFactory("cumulativeWeight"));
            heightColumn.setCellValueFactory(new MapValueFactory("height"));

            blockhashColumn.setCellFactory(TextFieldTableCell.forTableColumn());

            blockEvaluationTable.setItems(allData);
        }
    }
}

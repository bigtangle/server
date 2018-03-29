package com.bignetcoin.ui.wallet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.BlockEvaluation;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;

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

    public Main.OverlayUI overlayUI;

    @FXML
    public void initialize() {

        initTableView();
    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

    public void initTableView() {
        List<BlockEvaluation> list = new ArrayList<BlockEvaluation>();
        if (list != null && !list.isEmpty()) {
            ObservableList<Map> allData = FXCollections.observableArrayList();
            for (BlockEvaluation blockEvaluation : list) {
                Map<String, Object> dataRow = new HashMap<>();
                dataRow.put("hash", blockEvaluation.getBlockhash().toString());
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

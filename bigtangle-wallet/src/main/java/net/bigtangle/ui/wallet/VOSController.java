/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.spongycastle.crypto.params.KeyParameter;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.MapValueFactory;
import net.bigtangle.core.Block;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VOS;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.utils.OkHttp3Util;

public class VOSController {

    @FXML
    public TableView<Map<String, Object>> vosTable;
    @FXML
    public TableColumn<Map<String, Object>, String> addressCol;
    @FXML
    public TableColumn<Map<String, Object>, String> numberCol;

    @FXML
    public TableColumn<Map<String, Object>, String> priceCol;

    @FXML
    public TableColumn<Map<String, Object>, String> frequenceCol;

    @FXML
    public TableColumn<Map<String, Object>, String> urlCol;

    @FXML
    public TableColumn<Map<String, Object>, String> contentCol;

    @FXML
    public ComboBox<String> addressComboBox;

    @FXML
    public TextField numberTF;

    @FXML
    public TextField priceTF;

    @FXML
    public TextField frequenceTF;
    @FXML
    public RadioButton monthRB;
    @FXML
    public RadioButton onetimeRB;
    @FXML
    public RadioButton dailyRB;
    @FXML
    public ToggleGroup frequenceTG;

    @FXML
    public TextField urlTF;

    @FXML
    public TextArea contentTA;

    public Main.OverlayUI<?> overlayUI;

    @FXML
    public void initialize() {
        try {
            monthRB.setUserData("month");
            onetimeRB.setUserData("onetime");
            dailyRB.setUserData("daily");

            KeyParameter aesKey = null;
            final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
            if (!"".equals(Main.password.trim())) {
                aesKey = keyCrypter.deriveKey(Main.password);
            }
            List<String> list = new ArrayList<String>();
            for (ECKey ecKey : Main.bitcoin.wallet().walletKeys(aesKey)) {
                list.add(ecKey.getPublicKeyAsHex());
            }
            ObservableList<String> addressData = FXCollections.observableArrayList(list);
            addressComboBox.setItems(addressData);
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
        initTableView();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void initTableView() {
        try {
            KeyParameter aesKey = null;
            final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.bitcoin.wallet().getKeyCrypter();
            if (!"".equals(Main.password.trim())) {
                aesKey = keyCrypter.deriveKey(Main.password);
            }
            List<String> pubKeyList = new ArrayList<String>();
            for (ECKey ecKey : Main.bitcoin.wallet().walletKeys(aesKey)) {
                pubKeyList.add(ecKey.getPublicKeyAsHex());
            }
            int blocktype = (int) NetworkParameters.BLOCKTYPE_VOS;
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("blocktype", blocktype);
            requestParam.put("pubKeyList", pubKeyList);

            String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
            String resp = OkHttp3Util.postString(CONTEXT_ROOT + "userDataList",
                    Json.jsonmapper().writeValueAsString(requestParam));
            HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);
            List<String> dataList = (List<String>) result.get("dataList");
            ObservableList<Map<String, Object>> allData = FXCollections.observableArrayList();
            for (String hexStr : dataList) {
                byte[] data = Utils.HEX.decode(hexStr);
                HashMap<String, Object> vos = Json.jsonmapper().readValue(new String(data), HashMap.class);
                vos.put("frequence", Main.getText(vos.get("frequence").toString()));
                allData.add(vos);
            }
            vosTable.setItems(allData);
            addressCol.setCellValueFactory(new MapValueFactory("pubKey"));
            numberCol.setCellValueFactory(new MapValueFactory("nodeNumber"));
            priceCol.setCellValueFactory(new MapValueFactory("price"));
            frequenceCol.setCellValueFactory(new MapValueFactory("frequence"));
            urlCol.setCellValueFactory(new MapValueFactory("url"));
            contentCol.setCellValueFactory(new MapValueFactory("content"));
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void saveUserdata(ActionEvent event) {
        try {
            String frequence=frequenceTG.getSelectedToggle().getUserData().toString();
            //TODO cui,jiang
            VOS vos = new VOS();
            vos.setPubKey(addressComboBox.getSelectionModel().getSelectedItem());
            vos.setNodeNumber(Integer.parseInt(numberTF.getText()));
            vos.setPrice(Integer.parseInt(priceTF.getText()));
            vos.setFrequence(frequence);
            vos.setUrl(urlTF.getText());
            vos.setContent(contentTA.getText());

            String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
            HashMap<String, String> requestParam = new HashMap<String, String>();
            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "askTransaction",
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block block = Main.params.getDefaultSerializer().makeBlock(data);
            block.setBlocktype(NetworkParameters.BLOCKTYPE_VOS);
            ECKey pubKeyTo = Main.bitcoin.wallet().currentReceiveKey();

            Transaction coinbase = new Transaction(Main.params);
            coinbase.setDataclassname(DataClassName.VOS.name());
            coinbase.setData(vos.toByteArray());

            Sha256Hash sighash = coinbase.getHash();
            ECKey.ECDSASignature party1Signature = pubKeyTo.sign(sighash);
            byte[] buf1 = party1Signature.encodeToDER();

            List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
            MultiSignBy multiSignBy0 = new MultiSignBy();
            multiSignBy0.setAddress(pubKeyTo.toAddress(Main.params).toBase58());
            multiSignBy0.setPublickey(Utils.HEX.encode(pubKeyTo.getPubKey()));
            multiSignBy0.setSignature(Utils.HEX.encode(buf1));
            multiSignBies.add(multiSignBy0);
            coinbase.setDatasignature(Json.jsonmapper().writeValueAsBytes(multiSignBies));

            block.addTransaction(coinbase);
            block.solve();

            OkHttp3Util.post(CONTEXT_ROOT + "saveBlock", block.bitcoinSerialize());
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public void closeUI(ActionEvent event) {
        overlayUI.done();
    }

}

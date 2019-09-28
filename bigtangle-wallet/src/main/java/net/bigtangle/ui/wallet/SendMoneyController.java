/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.bigtangle.ui.wallet;

import static com.google.common.base.Preconditions.checkState;
import static net.bigtangle.ui.wallet.Main.walletAppKit;

import java.io.File;
import java.nio.ByteBuffer;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.PayMultiSignExt;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UserSettingData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.WatchedInfo;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.response.GetOutputsResponse;
import net.bigtangle.core.response.OutputsDetailsResponse;
import net.bigtangle.core.response.PayMultiSignAddressListResponse;
import net.bigtangle.core.response.PayMultiSignDetailsResponse;
import net.bigtangle.core.response.PayMultiSignListResponse;
import net.bigtangle.core.response.PayMultiSignResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.ui.wallet.utils.FileUtil;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.TextFieldValidator;
import net.bigtangle.ui.wallet.utils.WTUtils;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.UUIDUtil;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.Wallet;

@SuppressWarnings({ "rawtypes", "unused" })
public class SendMoneyController {

    private static final Logger log = LoggerFactory.getLogger(SendMoneyController.class);

    public Button sendBtn;
    public Button cancelBtn;

    @FXML
    public ComboBox<String> addressComboBox;
    @FXML
    public TextField linknameTextField;
    @FXML
    public TextField memoTF;

    public Label titleLabel;
    public TextField amountEdit;
    @FXML
    public Label btcLabel;

    @FXML
    public ComboBox<String> addressComboBox1;
    @FXML
    public ComboBox<String> myAddressComboBox;
    @FXML
    public ChoiceBox<String> addressChoiceBox;
    @FXML
    public ChoiceBox<String> addressChoiceBox1;
    @FXML
    public ChoiceBox<String> multiUtxoChoiceBox;
    @FXML
    public ChoiceBox<String> multiUtxoChoiceBox1;

    @FXML
    public TextField memoTF1;

    @FXML
    public TextField amountEdit1;
    @FXML
    public TextField amountEdit12;
    @FXML
    public Label btcLabel1;

    @FXML
    public TextField memoTF11;
    @FXML
    public TextField memoTF111;
    @FXML
    public TextField amountEdit11;
    @FXML
    public Label btcLabel11;
    @FXML
    public Label btcLabel12;
    @FXML
    public TextField signnumberTF1;
    @FXML
    public TextField signnumberTFA;

    @FXML
    public ChoiceBox<String> tokeninfo;

    @FXML
    public ChoiceBox<String> tokeninfo11;

    public Main.OverlayUI<?> overlayUI;

    @SuppressWarnings("unused")
    private String mOrderid;
    private Transaction mTransaction;
    @FXML
    public TextField signnumberTF;
    @FXML
    public TextField signAddressTF;
    @FXML
    public TextField signAddressTF1;
    @FXML
    public TextField signAddressTF11;
    @FXML
    public ChoiceBox<String> signAddressChoiceBox;;
    @FXML
    public TableView<Map> signTable;
    @FXML
    public TableColumn<Map, String> addressColumn;
    @FXML
    public TableColumn<Map, String> signnumberColumn;
    @FXML
    public TableColumn<Map, String> realSignnumColumn;
    @FXML
    public TableColumn<Map, String> isSignAllColumn;
    @FXML
    public TableColumn<Map, String> isMySignColumn;
    @FXML
    public TableColumn<Map, String> amountColumn;
    @FXML
    public TableColumn<Map, String> orderidColumn;

    @FXML
    public TextField amountEdit2;
    @FXML
    public ChoiceBox<String> tokeninfo1;
    @FXML
    public ComboBox<String> addressComboBox2;
    @FXML
    public TextField memoTF2;
    @FXML
    public Label btcLabel2;

    @FXML
    public ComboBox<String> subtangleComboBox;

    String utxoKey;
    String signnumberString = "0";
    String signnumberStringA = "0";

    @FXML
    public TabPane tabPane;

    @FXML
    public TableView<Map> historyTable;
    @FXML
    public TableColumn<Map, String> tokenCol;
    @FXML
    public TableColumn<Map, String> fromAddressCol;
    @FXML
    public TableColumn<Map, String> toAddressCol;
    @FXML
    public TableColumn<Map, String> amountCol;
    @FXML
    public TableColumn<Map, String> timeCol;

    @FXML
    public TextField fromaddressTF;
    @FXML
    public TextField toaddressTF;
    @FXML
    public DatePicker starttimeDP;
    @FXML
    public DatePicker endtimeDP;

    public void initChoicebox() {

        try {
            initTokeninfo();
            initSubtangle();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    public void initSubtangle() throws Exception {
        ObservableList<String> allData = FXCollections.observableArrayList();
        WatchedInfo watchedInfo = (WatchedInfo) Main.getUserdata(DataClassName.WATCHED.name(), true);
        if (watchedInfo == null) {
            return;
        }
        List<UserSettingData> list = watchedInfo.getUserSettingDatas();
        if (list != null && !list.isEmpty()) {
            for (UserSettingData userSettingData : list) {
                if (userSettingData.getDomain().equals(DataClassName.TOKEN.name())
                        && userSettingData.getValue().endsWith(":" + Main.getText("subtangle"))) {
                    allData.add(userSettingData.getKey() + ":" + userSettingData.getValue());
                }
            }
        }
        subtangleComboBox.setItems(allData);
    }

    public void initTokeninfo() {
        ObservableList<UTXOModel> list = Main.instance.getUtxoData();
        ObservableList<String> tokenData = FXCollections.observableArrayList();
        List<String> names = new ArrayList<String>();
        if (list != null && !list.isEmpty()) {
            for (UTXOModel utxoModel : list) {
                String temp = utxoModel.getTokenid();
                String signnum = utxoModel.getMinimumsign();
                int signnumInt = Integer.parseInt(signnum);
                String tempTokenid = temp.contains(":") ? temp.substring(temp.indexOf(":") + 1) : temp;
                String tempTokenname = temp.contains(":") ? temp.substring(0, temp.indexOf(":")) : "";
                if (signnumInt <= 1) {
                    if (!tokenData.contains(tempTokenid)) {
                        tokenData.add(tempTokenid);
                    }
                    if (!names.contains(tempTokenname)) {
                        names.add(tempTokenname);
                    }

                }
            }
        }
        tokeninfo.setItems(tokenData);
        // tokeninfo1.setItems(tokenData);
        tokeninfo11.setItems(tokenData);
        tokeninfo.getSelectionModel().selectedIndexProperty().addListener((ov, oldv, newv) -> {
            btcLabel.setText(names.get(newv.intValue()));
        });
        // tokeninfo1.getSelectionModel().selectedIndexProperty().addListener((ov,
        // oldv, newv) -> {
        // btcLabel2.setText(names.get(newv.intValue()));
        // });
        tokeninfo11.getSelectionModel().selectedIndexProperty().addListener((ov, oldv, newv) -> {
            btcLabel11.setText(names.get(newv.intValue()));
        });
        tokeninfo.getSelectionModel().selectFirst();
        tokeninfo1.getSelectionModel().selectFirst();
        tokeninfo11.getSelectionModel().selectFirst();
    }

    public boolean isMyTokens(String tokenHex) {
        ObservableList<CoinModel> list = Main.instance.getCoinData();
        if (list != null && !list.isEmpty()) {
            for (CoinModel coinModel : list) {
                String temp = coinModel.getTokenid();
                String tempTokenid = temp.contains(":") ? temp.substring(temp.indexOf(":") + 1) : temp;
                if (tokenHex.equalsIgnoreCase(tempTokenid.trim())) {
                    return true;
                }
            }
        }
        return false;

    }

    public boolean isMyUTXOs(String tokenHex) {
        ObservableList<UTXOModel> list = Main.instance.getUtxoData();
        if (list != null && !list.isEmpty()) {
            for (UTXOModel utxoModel : list) {
                String temp = utxoModel.getTokenid();
                String signnum = utxoModel.getMinimumsign();
                int signnumInt = Integer.parseInt(signnum);
                String tempTokenid = temp.contains(":") ? temp.substring(temp.indexOf(":") + 1) : temp;
                if (tokenHex.equalsIgnoreCase(tempTokenid.trim()) && signnumInt <= 1) {
                    return true;
                }
            }
        }
        return false;

    }

    private List<String> hashHexList = new ArrayList<String>();
    private List<String> subtangleHashHexList = new ArrayList<String>();

    @FXML
    public void initialize() throws Exception {
        initChoicebox();
        List<String> list = Main.initAddress4block();
        ObservableList<UTXOModel> utxoModels = Main.instance.getUtxoData();
        if (utxoModels != null && !utxoModels.isEmpty()) {
            for (UTXOModel utxoModel : utxoModels) {
                String temp = utxoModel.getBalance() + "," + utxoModel.getTokenid() + "," + utxoModel.getMinimumsign();
                tokeninfo1.getItems().add(temp);
                subtangleHashHexList.add(utxoModel.getHash() + ":" + utxoModel.getOutputindex());
                if (!"".equals(utxoModel.getMinimumsign().trim()) && !utxoModel.getMinimumsign().trim().equals("0")
                        && !utxoModel.getMinimumsign().trim().equals("1")) {

                    multiUtxoChoiceBox.getItems().add(temp);
                    multiUtxoChoiceBox1.getItems().add(temp);

                    hashHexList.add(utxoModel.getHash() + ":" + utxoModel.getOutputindex());
                }

            }
            tokeninfo1.getSelectionModel().selectedItemProperty().addListener((ov, oldv, newv) -> {
                if (newv != null && !newv.trim().equals("")) {
                    amountEdit2.setText(newv.split(",")[0]);
                    signnumberString = newv.split(",")[2];
                    // signnumberTFA.setText(newv.split(",")[2]);
                    btcLabel2.setText(newv.split(",")[1]);
                }
            });
            multiUtxoChoiceBox.getSelectionModel().selectedItemProperty().addListener((ov, oldv, newv) -> {
                if (newv != null && !newv.trim().equals("")) {
                    amountEdit1.setText(newv.split(",")[0]);
                    signnumberString = newv.split(",")[2];
                    // signnumberTFA.setText(newv.split(",")[2]);
                    btcLabel1.setText(newv.split(",")[1]);
                }
            });
            multiUtxoChoiceBox1.getSelectionModel().selectedItemProperty().addListener((ov, oldv, newv) -> {
                if (newv != null && !newv.trim().equals("")) {
                    amountEdit12.setText(newv.split(",")[0]);
                    signnumberStringA = newv.split(",")[2];
                    // signnumberTFA.setText(newv.split(",")[2]);
                    btcLabel12.setText(newv.split(",")[1]);
                }
            });
            // multiUtxoChoiceBox.getSelectionModel().selectedIndexProperty().addListener((ov,
            // oldindex, newindex) -> {
            // utxoKey = hashHexList.get(newindex.intValue());// hash,index....
            // });
        }

        ObservableList<String> addressData = FXCollections.observableArrayList(list);
        addressComboBox.setItems(addressData);
        addressComboBox1.setItems(addressData);
        addressComboBox2.setItems(addressData);
        // new BitcoinAddressValidator(Main.params, addressComboBox, sendBtn);
        new TextFieldValidator(amountEdit,
                text -> !WTUtils.didThrow(() -> checkState(MonetaryFormat.FIAT.noCode().parse(text).isPositive())));
        new TextFieldValidator(amountEdit1,
                text -> !WTUtils.didThrow(() -> checkState(MonetaryFormat.FIAT.noCode().parse(text).isPositive())));
        tabPane.getSelectionModel().selectedIndexProperty().addListener((ov, t, t1) -> {
            int index = t1.intValue();
            switch (index) {
            case 0: {
            }

                break;
            case 1: {
            }

                break;
            case 2: {
            }

                break;
            case 3: {
            }

                break;
            case 4: {
            }

                break;
            case 5: {
            }

                break;
            case 6: {

            }

                break;
            default: {
            }
                break;
            }
        });
        initSignTable();
        // initHistoryTable();
    }

    @SuppressWarnings({ "unchecked" })
    public void initSignTable() throws Exception {

        List<String> pubKeys = new ArrayList<String>();
        for (ECKey ecKey : Main.walletAppKit.wallet().walletKeys(Main.getAesKey())) {
            pubKeys.add(ecKey.toAddress(Main.params).toString());
        }
        String ContextRoot = Main.getContextRoot();
        String resp = OkHttp3Util.postString(ContextRoot + ReqCmd.getPayMultiSignList.name(),
                Json.jsonmapper().writeValueAsString(pubKeys));

        PayMultiSignListResponse payMultiSignListResponse = Json.jsonmapper().readValue(resp,
                PayMultiSignListResponse.class);
        List<PayMultiSignExt> payMultiSigns = payMultiSignListResponse.getPayMultiSigns();

        ObservableList<Map> signData = FXCollections.observableArrayList();
        for (PayMultiSignExt payMultiSign : payMultiSigns) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            int sign = payMultiSign.getSign();
            map.put("signFlag", sign == 0 ? "-" : "*");
            map.put("toaddress", payMultiSign.getToaddress());
            map.put("minsignnumber", payMultiSign.getMinsignnumber());
            map.put("sign", payMultiSign.getSign());
            map.put("amount", payMultiSign.getAmount() );
            map.put("realSignnumber", payMultiSign.getRealSignnumber());
            map.put("orderid", payMultiSign.getOrderid());
            map.put("isSignAll",
                    payMultiSign.getRealSignnumber() >= payMultiSign.getMinsignnumber() ? Main.getText("yes")
                            : Main.getText("no"));
            signData.add(map);
        }

        addressColumn.setCellValueFactory(new MapValueFactory("toaddress"));
        signnumberColumn.setCellValueFactory(new MapValueFactory("minsignnumber"));
        isMySignColumn.setCellValueFactory(new MapValueFactory("sign"));
        amountColumn.setCellValueFactory(new MapValueFactory("amount"));
        isMySignColumn.setCellValueFactory(new MapValueFactory("signFlag"));
        realSignnumColumn.setCellValueFactory(new MapValueFactory("realSignnumber"));
        orderidColumn.setCellValueFactory(new MapValueFactory("orderid"));
        isSignAllColumn.setCellValueFactory(new MapValueFactory("isSignAll"));
        this.signTable.setItems(signData);
    }

    @SuppressWarnings({ "unchecked" })
    public void initHistoryTable() throws Exception {

        Map<String, Object> param = new HashMap<String, Object>();
        param.put("fromaddress", fromaddressTF.getText());
        param.put("toaddress", toaddressTF.getText());
        ZoneId zoneId = ZoneId.systemDefault();
        if (starttimeDP.getValue() != null) {
            ZonedDateTime zdt = starttimeDP.getValue().atStartOfDay(zoneId);
            Date date = Date.from(zdt.toInstant());
            param.put("starttime", date.getTime());
        }
        if (endtimeDP.getValue() != null) {
            ZonedDateTime zdt = endtimeDP.getValue().atStartOfDay(zoneId);
            Date date = Date.from(zdt.toInstant());
            param.put("endtime", date.getTime());
        }
        String ContextRoot = Main.getContextRoot();
        String resp = OkHttp3Util.postString(ContextRoot + ReqCmd.getOutputsHistory.name(),
                Json.jsonmapper().writeValueAsString(param));
        log.debug(resp);
        GetOutputsResponse response = Json.jsonmapper().readValue(resp, GetOutputsResponse.class);
        List<UTXO> utxos = response.getOutputs();

        MonetaryFormat mf = MonetaryFormat.FIAT.noCode();
        ObservableList<Map> signData = FXCollections.observableArrayList();
        for (UTXO utxo : utxos) {
            HashMap<String, Object> map = new HashMap<String, Object>();

            map.put("token", utxo.getTokenId());
            map.put("toaddress", utxo.getAddress());
            map.put("fromaddress", utxo.getFromaddress());
            map.put("time", utxo.getTime());
            map.put("amount",
                    mf.format(utxo.getValue(), response.getTokennames().get(utxo.getTokenId()).getDecimals()));

            signData.add(map);
        }

        tokenCol.setCellValueFactory(new MapValueFactory("token"));
        fromAddressCol.setCellValueFactory(new MapValueFactory("fromaddress"));
        toAddressCol.setCellValueFactory(new MapValueFactory("toaddress"));
        amountCol.setCellValueFactory(new MapValueFactory("amount"));
        timeCol.setCellValueFactory(new MapValueFactory("time"));

        this.signTable.setItems(signData);
    }

    public void importSign(ActionEvent event) {
        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        byte[] buf = FileUtil.readFile(file);
        if (buf == null) {
            return;
        }
        reloadTransaction(buf);
    }

    private void reloadTransaction(byte[] buf) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf);

        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            addressComboBox1.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            // tokeninfo1.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            amountEdit1.setText(new String(dst));
        }
        byte[] orderid = new byte[byteBuffer.getInt()];
        byteBuffer.get(orderid);

        mOrderid = new String(orderid);
        // log.debug ("orderid : " + new String(orderid));

        int len = byteBuffer.getInt();
        // log.debug("tx len : " + len);
        byte[] data = new byte[len];
        byteBuffer.get(data);
        try {
            mTransaction = (Transaction) Main.params.getDefaultSerializer().makeTransaction(data);
            // mTransaction = (Transaction)
            // Main.params.getDefaultSerializer().makeTransaction(data);
            if (mTransaction == null) {
                GuiUtils.informationalAlert(Main.getText("ex_c_m"), Main.getText("ex_c_d"));
            }
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void exportSign(ActionEvent event) {

        this.mOrderid = UUIDUtil.randomUUID();

        final FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showSaveDialog(null);
        if (file == null) {
            return;
        }

        // FileUtil.writeFile(file, buf);
        overlayUI.done();
    }

    public void cancel(ActionEvent event) {
        overlayUI.done();
    }

    public void sendSubtangle(ActionEvent event) {
        try {
            paySubtangle();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void paySubtangle() throws Exception {

        List<ECKey> issuedKeys = Main.walletAppKit.wallet().walletKeys(Main.getAesKey());
        ECKey genesiskey = null;

        genesiskey = issuedKeys.get(0);

        int index = tokeninfo1.getSelectionModel().getSelectedIndex();
        String outputStr = this.subtangleHashHexList.get(index);
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("hexStr", outputStr);
        String resp = OkHttp3Util.postString(Main.getContextRoot() + ReqCmd.getOutputWithKey.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Address address = ECKey
                .fromPublicOnly(Utils.HEX
                        .decode(subtangleComboBox.getValue().substring(0, subtangleComboBox.getValue().indexOf(":"))))
                .toAddress(Main.params);

        walletAppKit.wallet().paySubtangle(Main.getAesKey(), outputStr, genesiskey,
                Address.fromBase58(Main.params, addressComboBox2.getValue()),
                Coin.valueOf(Long.parseLong(amountEdit2.getText()), Utils.HEX.decode(btcLabel2.getText())), address);
    }

    public void send(ActionEvent event) {
        try {

            if (addressComboBox.getValue() == null) {
                GuiUtils.informationalAlert(Main.getText("address_empty"), "", "");
                return;
            }
            String CONTEXT_ROOT = Main.getContextRoot();
            Address destination = // Address.getParametersFromAddress(address)address.getText()
                    Address.fromBase58(Main.params,
                            !addressComboBox.getValue().contains(",") ? addressComboBox.getValue()
                                    : addressComboBox.getValue().split(",")[1]);

            Wallet wallet = Main.walletAppKit.wallet();
            wallet.setServerURL(CONTEXT_ROOT);
            Token t = wallet.checkTokenId(tokeninfo.getValue());
            Coin amount = MonetaryFormat.FIAT.noCode().parse(amountEdit.getText(),
                    Utils.HEX.decode(tokeninfo.getValue().toString()), t.getDecimals());
            // long factor =
            // Long.valueOf(unitToggleGroup.getSelectedToggle().getUserData().toString()).longValue();
            long factor = 1;
            amount = amount.multiply(factor);

            try {
                wallet.pay(Main.getAesKey(), destination, amount, memoTF.getText());
            } catch (InsufficientMoneyException e) {

                GuiUtils.informationalAlert(Main.getText("m_n_e"), "", "");
                return;
            }
            checkContact(event);
            // Main.instance.sentEmpstyBlock(2);
            overlayUI.done();
            Main.instance.controller.initTableView();

        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void sendMulti(ActionEvent event) {
        try {

            if (addressChoiceBox.getItems() == null || addressChoiceBox.getItems().isEmpty()) {
                GuiUtils.informationalAlert(Main.getText("signnumNull"), "", "");
                return;
            }
            String CONTEXT_ROOT = Main.getContextRoot();

            Main.walletAppKit.wallet().setServerURL(CONTEXT_ROOT);

            int signnum = signnumberTF1.getText() == null || signnumberTF1.getText().isEmpty() ? 1
                    : Integer.parseInt(signnumberTF1.getText().trim());
            if (signnum > addressChoiceBox.getItems().size()) {
                GuiUtils.informationalAlert(Main.getText("signnumberNoEq"), "", "");
                return;
            }
            List<ECKey> keys = new ArrayList<ECKey>();
            for (String keyString : addressChoiceBox.getItems()) {
                keys.add(ECKey.fromPublicOnly(Utils.HEX.decode(keyString)));
            }
            Wallet wallet = Main.walletAppKit.wallet();
            wallet.setServerURL(CONTEXT_ROOT);
            Token t = wallet.checkTokenId(tokeninfo11.getValue());

            Coin amount = MonetaryFormat.FIAT.noCode().parse(amountEdit11.getText(),
                    Utils.HEX.decode(tokeninfo11.getValue().toString()), t.getDecimals());

            Main.walletAppKit.wallet().payMultiSignatures(null, keys, signnum, amount, "");

            GuiUtils.informationalAlert("", Main.getText("alreadySend"), "");
            overlayUI.done();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void checkContact(ActionEvent event) throws Exception {

        String addresses = Main.getString4block(Main.initAddress4block());
        if (!addresses.contains(!addressComboBox.getValue().contains(",") ? addressComboBox.getValue()
                : addressComboBox.getValue().split(",")[1])) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle(Main.getText("linkman"));
            dialog.setHeaderText(!addressComboBox.getValue().contains(",") ? addressComboBox.getValue()
                    : addressComboBox.getValue().split(",")[1]);
            dialog.setContentText(Main.getText("linkman"));
            dialog.setWidth(900);
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                Main.addAddress2block(result.get(),
                        !addressComboBox.getValue().contains(",") ? addressComboBox.getValue()
                                : addressComboBox.getValue().split(",")[1]);
            }
        }

    }

    public void addSIgnAddress(ActionEvent event) {
        try {
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void showAddAddressDialog() throws Exception {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(Main.getText("Address"));
        dialog.setHeaderText(null);
        dialog.setContentText(Main.getText("Address"));
        dialog.setWidth(500);
        dialog.getDialogPane().setPrefWidth(500);
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String address = result.get();
            if (address != null && !address.isEmpty() && !addressChoiceBox.getItems().contains(address)) {
                addressChoiceBox.getItems().add(address);
                addressChoiceBox.getSelectionModel().selectLast();
            }

        }

    }

    public void removeSignAddress(ActionEvent event) {
        addressChoiceBox.getItems().remove(addressChoiceBox.getValue());
    }

    public void test() throws Exception {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(Main.getText("Address"));
        dialog.setHeaderText(null);

        ButtonType loginButtonType = new ButtonType(Main.getText("OK"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        ComboBox<String> addressComboBox = new ComboBox<String>();
        addressComboBox.setEditable(true);
        addressComboBox.setPrefWidth(300);
        List<String> list = Main.initAddress4block();

        ObservableList<String> addressData = FXCollections.observableArrayList(list);
        addressComboBox.setItems(addressData);
        grid.add(new Label(Main.getText("Address")), 0, 0);
        grid.add(addressComboBox, 1, 0);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return addressComboBox.getValue();
            }
            return "";
        });
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(address -> {
            try {
                if (address != null && !address.isEmpty() && !addressChoiceBox.getItems().contains(address)) {
                    addressChoiceBox.getItems().add(address);
                    addressChoiceBox.getSelectionModel().selectLast();
                }
            } catch (Exception e) {
                // GuiUtils.crashAlert(e);
            }
        });
    }

    public void addSignAddr(ActionEvent event) {
        if (signAddressTF.getText() == null || signAddressTF.getText().isEmpty()) {
            return;
        }
        if (!signAddressChoiceBox.getItems().contains(signAddressTF.getText())) {
            signAddressChoiceBox.getItems().add(signAddressTF.getText());
            signAddressChoiceBox.getSelectionModel().selectLast();
        }

        signAddressTF.setText("");
    }

    public void addSignAddrA(ActionEvent event) {
        if (signAddressTF1.getText() == null || signAddressTF1.getText().isEmpty()) {
            return;
        }
        if (!addressChoiceBox.getItems().contains(signAddressTF1.getText())) {
            addressChoiceBox.getItems().add(signAddressTF1.getText());
            addressChoiceBox.getSelectionModel().selectLast();
        }

        signAddressTF1.setText("");
    }

    public void addSignAddrB(ActionEvent event) {
        if (signAddressTF11.getText() == null || signAddressTF11.getText().isEmpty()) {
            return;
        }
        if (!addressChoiceBox1.getItems().contains(signAddressTF11.getText())) {
            addressChoiceBox1.getItems().add(signAddressTF11.getText());
            addressChoiceBox1.getSelectionModel().selectLast();
        }

        signAddressTF11.setText("");
    }

    public void sign(ActionEvent event) throws Exception {
        // multiUtxoChoiceBox
        // amountEdit1
        // addressComboBox1
        // signnumberTFA
        // memoTF1
        String ContextRoot = Main.getContextRoot();
        this.launchPayMultiSign(Main.params, ContextRoot);
        GuiUtils.informationalAlert("", Main.getText("alreadySend"), "");

        initSignTable();
        overlayUI.done();
    }

    public void searchHistory(ActionEvent event) {
        try {
            initHistoryTable();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    public void signA(ActionEvent event) throws Exception {

        String ContextRoot = Main.getContextRoot();
        this.launchPayMultiSignA(Main.params, ContextRoot);
        GuiUtils.informationalAlert("", Main.getText("alreadySend"), "");

        initSignTable();
        overlayUI.done();
    }

    public void launchPayMultiSign(NetworkParameters networkParameters, String contextRoot) throws Exception {
        int index = multiUtxoChoiceBox.getSelectionModel().getSelectedIndex();
        String outputStr = this.hashHexList.get(index);
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("hexStr", outputStr);
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getOutputWithKey.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        OutputsDetailsResponse outputsDetailsResponse = Json.jsonmapper().readValue(resp, OutputsDetailsResponse.class);
        UTXO utxo = outputsDetailsResponse.getOutputs();

        TransactionOutput multisigOutput = new FreeStandingTransactionOutput(networkParameters, utxo);
        Transaction transaction = new Transaction(Main.params);

        Wallet wallet = Main.walletAppKit.wallet();
        wallet.setServerURL(contextRoot);
        Token t = wallet.checkTokenId(Utils.HEX.encode(utxo.getValue().getTokenid()));

        Coin amount = MonetaryFormat.FIAT.noCode().parse(amountEdit1.getText(), utxo.getValue().getTokenid(),
                t.getDecimals());

        Address address = Address.fromBase58(networkParameters,
                !addressComboBox1.getValue().contains(",") ? addressComboBox1.getValue()
                        : addressComboBox1.getValue().split(",")[1]);
        transaction.addOutput(amount, address);

        Coin amount2 = multisigOutput.getValue().subtract(amount);
        transaction.addOutput(amount2, multisigOutput.getScriptPubKey());

        transaction.addInput(utxo.getBlockHash(), multisigOutput);
        transaction.setMemo(new MemoInfo(memoTF1.getText()) );

        PayMultiSign payMultiSign = new PayMultiSign();
        payMultiSign.setOutputindex(utxo.getIndex());
        payMultiSign.setTokenBlockhashHex(utxo.getBlockHashHex());
        payMultiSign.setOrderid(UUIDUtil.randomUUID());
        payMultiSign.setTokenid(utxo.getValue().getTokenHex());
        payMultiSign.setBlockhashHex(Utils.HEX.encode(transaction.bitcoinSerialize()));
        payMultiSign.setToaddress(address.toBase58());
        payMultiSign.setAmount(amount.getValue());

        int signnumber = Integer.parseInt(signnumberString);
        payMultiSign.setMinsignnumber(signnumber);
        payMultiSign.setOutputHashHex(utxo.getHashHex());

        OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.launchPayMultiSign.name(),
                Json.jsonmapper().writeValueAsString(payMultiSign));
    }

    public void launchPayMultiSignA(NetworkParameters networkParameters, String contextRoot) throws Exception {
        int index = multiUtxoChoiceBox1.getSelectionModel().getSelectedIndex();
        if (index == -1) {
            return;
        }
        String outputStr = this.hashHexList.get(index);
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("hexStr", outputStr);
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getOutputWithKey.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        OutputsDetailsResponse outputsDetailsResponse = Json.jsonmapper().readValue(resp, OutputsDetailsResponse.class);
        UTXO utxo = outputsDetailsResponse.getOutputs();

        TransactionOutput multisigOutput = new FreeStandingTransactionOutput(networkParameters, utxo);
        Transaction transaction = new Transaction(Main.params);

        Wallet wallet = Main.walletAppKit.wallet();
        wallet.setServerURL(contextRoot);
        Token t = wallet.checkTokenId(Utils.HEX.encode(utxo.getValue().getTokenid()));

        Coin amount = MonetaryFormat.FIAT.noCode().parse(amountEdit12.getText(), utxo.getValue().getTokenid(),
                t.getDecimals());

        List<ECKey> keys = new ArrayList<ECKey>();
        for (String keyString : addressChoiceBox1.getItems()) {
            keys.add(ECKey.fromPublicOnly(Utils.HEX.decode(keyString)));
        }

        Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(keys.size(), keys);
        transaction.addOutput(amount, scriptPubKey);

        Coin amount2 = multisigOutput.getValue().subtract(amount);
        transaction.addOutput(amount2, multisigOutput.getScriptPubKey());

        transaction.addInput(utxo.getBlockHash(), multisigOutput);
        transaction.setMemo(new MemoInfo(memoTF111.getText()) );

        PayMultiSign payMultiSign = new PayMultiSign();
        payMultiSign.setOrderid(UUIDUtil.randomUUID());
        payMultiSign.setTokenid(utxo.getValue().getTokenHex());
        payMultiSign.setBlockhashHex(Utils.HEX.encode(transaction.bitcoinSerialize()));
        payMultiSign.setToaddress("");
        payMultiSign.setAmount(amount.getValue());

        payMultiSign.setMinsignnumber(keys.size());
        payMultiSign.setOutputHashHex(utxo.getHashHex());
        payMultiSign.setOutputindex(utxo.getIndex());
        OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.launchPayMultiSign.name(),
                Json.jsonmapper().writeValueAsString(payMultiSign));
    }

    public void removeSignAddr(ActionEvent event) {
        signAddressChoiceBox.getItems().remove(signAddressChoiceBox.getValue());
    }

    public void removeSignAddrA(ActionEvent event) {
        addressChoiceBox.getItems().remove(addressChoiceBox.getValue());
    }

    public void removeSignAddrB(ActionEvent event) {
        addressChoiceBox1.getItems().remove(addressChoiceBox1.getValue());
    }

    public void saveSetting(ActionEvent event) {

    }

    @SuppressWarnings("unchecked")
    public void multiSign(ActionEvent event) throws Exception {
        Map<String, Object> map = signTable.getSelectionModel().getSelectedItem();
        String orderid = (String) map.get("orderid");
        String ContextRoot = Main.getContextRoot();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("orderid", orderid);
        String resp = OkHttp3Util.postString(ContextRoot + ReqCmd.getPayMultiSignAddressList.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        PayMultiSignAddressListResponse payMultiSignAddressListResponse = Json.jsonmapper().readValue(resp,
                PayMultiSignAddressListResponse.class);
        List<PayMultiSignAddress> payMultiSignAddresses = payMultiSignAddressListResponse.getPayMultiSignAddresses();

        ECKey currentECKey = null;

        for (PayMultiSignAddress payMultiSignAddress : payMultiSignAddresses) {
            if (payMultiSignAddress.getSign() == 1) {
                continue;
            }
            for (ECKey ecKey : Main.walletAppKit.wallet().walletKeys(Main.getAesKey())) {
                if (ecKey.toAddress(Main.params).toString().equals(payMultiSignAddress.getPubKey())) {
                    currentECKey = ecKey;
                    break;
                }
            }
        }
        if (currentECKey == null) {
            GuiUtils.informationalAlert("not found eckey sign", "sign error");
            return;
        }
        this.payMultiSign(currentECKey, orderid, Main.params, ContextRoot);
    }

    public void payMultiSign(ECKey ecKey, String orderid, NetworkParameters networkParameters, String contextRoot)
            throws Exception {
        List<String> pubKeys = new ArrayList<String>();
        pubKeys.add(ecKey.getPublicKeyAsHex());

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.clear();
        requestParam.put("orderid", orderid);
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.payMultiSignDetails.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        PayMultiSignDetailsResponse payMultiSignDetailsResponse = Json.jsonmapper().readValue(resp,
                PayMultiSignDetailsResponse.class);
        PayMultiSign payMultiSign_ = payMultiSignDetailsResponse.getPayMultiSign();

        requestParam.clear();
        requestParam.put("hexStr", payMultiSign_.getOutputHashHex() + ":" + payMultiSign_.getOutputindex());
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.getOutputWithKey.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.debug(resp);

        OutputsDetailsResponse outputsDetailsResponse = Json.jsonmapper().readValue(resp, OutputsDetailsResponse.class);
        UTXO u = outputsDetailsResponse.getOutputs();

        TransactionOutput multisigOutput_ = new FreeStandingTransactionOutput(networkParameters, u);
        Script multisigScript_ = multisigOutput_.getScriptPubKey();

        byte[] payloadBytes = Utils.HEX.decode((String) payMultiSign_.getBlockhashHex());
        Transaction transaction0 = networkParameters.getDefaultSerializer().makeTransaction(payloadBytes);

        Sha256Hash sighash = transaction0.hashForSignature(0, multisigScript_, Transaction.SigHash.ALL, false);

        TransactionSignature transactionSignature = new TransactionSignature(ecKey.sign(sighash, Main.getAesKey()),
                Transaction.SigHash.ALL, false);

        ECKey.ECDSASignature party1Signature = ecKey.sign(transaction0.getHash(), Main.getAesKey());
        byte[] buf1 = party1Signature.encodeToDER();

        requestParam.clear();
        requestParam.put("orderid", (String) payMultiSign_.getOrderid());
        requestParam.put("pubKey", ecKey.getPublicKeyAsHex());
        requestParam.put("signature", Utils.HEX.encode(buf1));
        requestParam.put("signInputData", Utils.HEX.encode(transactionSignature.encodeToBitcoin()));
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.payMultiSign.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.debug(resp);

        PayMultiSignResponse payMultiSignResponse = Json.jsonmapper().readValue(resp, PayMultiSignResponse.class);
        boolean success = payMultiSignResponse.isSuccess();
        initSignTable();
        if (success) {
            requestParam.clear();
            requestParam.put("orderid", (String) payMultiSign_.getOrderid());
            resp = OkHttp3Util.postString(contextRoot + ReqCmd.getPayMultiSignAddressList.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            log.debug(resp);

            PayMultiSignAddressListResponse payMultiSignAddressListResponse = Json.jsonmapper().readValue(resp,
                    PayMultiSignAddressListResponse.class);
            List<PayMultiSignAddress> payMultiSignAddresses = payMultiSignAddressListResponse
                    .getPayMultiSignAddresses();

            List<byte[]> sigs = new ArrayList<byte[]>();
            for (PayMultiSignAddress payMultiSignAddress : payMultiSignAddresses) {
                String signInputDataHex = payMultiSignAddress.getSignInputDataHex();
                sigs.add(Utils.HEX.decode(signInputDataHex));
            }

            Script inputScript = ScriptBuilder.createMultiSigInputScriptBytes(sigs);
            transaction0.getInput(0).setScriptSig(inputScript);

            byte[] buf = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(buf);
            rollingBlock.addTransaction(transaction0);
            rollingBlock.solve();
            OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

            Main.instance.controller.initTableView();
        }
    }

    public void editSign(ActionEvent event) {

    }
}

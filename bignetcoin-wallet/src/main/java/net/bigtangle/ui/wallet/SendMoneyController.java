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
import static net.bigtangle.ui.wallet.utils.GuiUtils.checkGuiThread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.DeterministicKey;
import net.bigtangle.ui.wallet.utils.GuiUtils;
import net.bigtangle.ui.wallet.utils.TextFieldValidator;
import net.bigtangle.ui.wallet.utils.WTUtils;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet;

public class SendMoneyController {
    public Button sendBtn;
    public Button cancelBtn;
    public TextField address;
    public Label titleLabel;
    public TextField amountEdit;
    @FXML
    public Label btcLabel;
    @FXML
    public ToggleGroup unitToggleGroup;
    @FXML
    public RadioButton basicRadioButton;
    @FXML
    public RadioButton kiloRadioButton;
    @FXML
    public RadioButton milionRadioButton;

    @FXML
    public ChoiceBox<Object> tokeninfo;

    public Main.OverlayUI<?> overlayUI;

    public void initChoicebox() {
        basicRadioButton.setUserData(1 + "");
        kiloRadioButton.setUserData(1000 + "");
        milionRadioButton.setUserData(1000 * 1000 + "");
        String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
        ObservableList<Object> tokenData = FXCollections.observableArrayList();
        ECKey ecKey = Main.bitcoin.wallet().currentReceiveKey();
        try {
            String response = OkHttp3Util.post(CONTEXT_ROOT + "getTokens", ecKey.getPubKeyHash());

            final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

            List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");
            List<String> names = new ArrayList<String>();
            for (Map<String, Object> map : list) {
                String tokenHex = (String) map.get("tokenHex");
                tokenData.add(tokenHex);
                names.add(map.get("tokenname").toString());
            }
            tokeninfo.setItems(tokenData);
            tokeninfo.getSelectionModel().selectedIndexProperty().addListener((ov, oldv, newv) -> {
                btcLabel.setText(names.get(newv.intValue()));
            });
            tokeninfo.getSelectionModel().selectFirst();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }

    }

    @FXML
    public void initialize() throws Exception {
        initChoicebox();
        DeterministicKey ecKey = Main.bitcoin.wallet().currentReceiveKey();
        address.setText(ecKey.toAddress(Main.params).toBase58());
        new TextFieldValidator(amountEdit, text -> !WTUtils
                .didThrow(() -> checkState(Coin.parseCoin(text, NetworkParameters.BIGNETCOIN_TOKENID).isPositive())));

    }

    public void importSign(ActionEvent event) {
    }

    public void exportSign(ActionEvent event) {
    }

    public void cancel(ActionEvent event) {
        overlayUI.done();
    }

    public void send(ActionEvent event) {
        try {
            // sendBtn.setDisable(true);
            checkGuiThread();
            overlayUI.done();
            String CONTEXT_ROOT = "http://" + Main.IpAddress + ":" + Main.port + "/";
            Address destination = // Address.getParametersFromAddress(address)address.getText()
                    Address.fromBase58(Main.params, address.getText());
            HashMap<String, String> requestParam = new HashMap<String, String>();
            byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "askTransaction",
                    Json.jsonmapper().writeValueAsString(requestParam));

            Block rollingBlock = Main.params.getDefaultSerializer().makeBlock(data);

            Wallet wallet = Main.bitcoin.wallet();
            wallet.setServerURL(CONTEXT_ROOT);

            Coin amount = Coin.parseCoin(amountEdit.getText(), Utils.HEX.decode(tokeninfo.getValue().toString()));
            long factor = Long.valueOf(unitToggleGroup.getSelectedToggle().getUserData().toString()).longValue();

            amount = amount.multiply(factor);
            SendRequest request = SendRequest.to(destination, amount);
            wallet.completeTx(request);
            rollingBlock.addTransaction(request.tx);
            rollingBlock.solve();
            // OkHttp3Util.post(CONTEXT_ROOT + "saveBlock",
            // rollingBlock.bitcoinSerialize());
            // Main.sentEmpstyBlock(Main.numberOfEmptyBlocks);
            Main.instance.sendMessage(rollingBlock.bitcoinSerialize());
            Main.instance.controller.initTableView();
            // address.setDisable(true);
            // ((HBox) amountEdit.getParent()).getChildren().remove(amountEdit);
            // ((HBox) btcLabel.getParent()).getChildren().remove(btcLabel);
            // updateTitleForBroadcast();
        } catch (Exception e) {
            GuiUtils.crashAlert(e);
        }
    }

    @SuppressWarnings("unused")
    private void askForPasswordAndRetry() {
        Main.OverlayUI<WalletPasswordController> pwd = Main.instance.overlayUI("wallet_password.fxml");
        final String addressStr = address.getText();
        final String amountStr = amountEdit.getText();
        pwd.controller.aesKeyProperty().addListener((observable, old, cur) -> {
            // We only get here if the user found the right password. If they
            // don't or they cancel, we end up back on
            // the main UI screen. By now the send money screen is history so we
            // must recreate it.
            checkGuiThread();
            Main.OverlayUI<SendMoneyController> screen = Main.instance.overlayUI("send_money.fxml");
            // TODO screen.controller.aesKey = cur;
            screen.controller.address.setText(addressStr);
            screen.controller.amountEdit.setText(amountStr);
            try {
                screen.controller.send(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

}

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

package wallettemplate;

import static com.google.common.base.Preconditions.checkState;
import static wallettemplate.utils.GuiUtils.checkGuiThread;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.wallet.Wallet;
import org.spongycastle.crypto.params.KeyParameter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import wallettemplate.controls.BitcoinAddressValidator;
import wallettemplate.utils.TextFieldValidator;
import wallettemplate.utils.WTUtils;

public class SendMoneyController {
    public Button sendBtn;
    public Button cancelBtn;
    public TextField address;
    public Label titleLabel;
    public TextField amountEdit;
    public Label btcLabel;

    public Main.OverlayUI overlayUI;

    private Wallet.SendResult sendResult;
    private KeyParameter aesKey;

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    OkHttpClient client = new OkHttpClient();

    // Called by FXMLLoader
    public void initialize() throws JsonProcessingException, IOException {

        String addr = "030d8952f6c079f60cd26eb3ba83cf16a81c51fc8e47b767721fa38b5e20092a75";
        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getBalances");
        request.put("addresses", new String[] { addr });
        request.put("threshold", 100);

        String response = post("http://localhost:14265", Json.jsonmapper().writeValueAsString(request));
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

        Coin balance = Coin.valueOf(10000, NetworkParameters.BIGNETCOIN_TOKENID);
        // Main.bitcoin.wallet().getBalance();
        checkState(!balance.isZero());
        new BitcoinAddressValidator(Main.params, address, sendBtn);
        new TextFieldValidator(amountEdit, text -> !WTUtils.didThrow(
                () -> checkState(Coin.parseCoin(text, NetworkParameters.BIGNETCOIN_TOKENID).compareTo(balance) <= 0)));
        amountEdit.setText(balance.toPlainString());
    }

    String post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    public void cancel(ActionEvent event) {
        overlayUI.done();
    }

    public void send(ActionEvent event) throws Exception {
        // Address exception cannot happen as we validated it beforehand.

        Coin amount = Coin.parseCoin(amountEdit.getText(), NetworkParameters.BIGNETCOIN_TOKENID);
        Address destination = Address.fromBase58(Main.params, address.getText());

        ECKey outKey = new ECKey();

        final Map<String, Object> reqParam0 = new HashMap<>();
        reqParam0.put("command", "askTransaction");
        reqParam0.put("pubkey", Utils.HEX.encode(outKey.getPubKey()));
        reqParam0.put("toaddress", Utils.HEX.encode(destination.getHash160()));
        reqParam0.put("amount", amountEdit.getText());
        reqParam0.put("tokenid", NetworkParameters.BIGNETCOIN_TOKENID);

        String response = post("http://localhost:14265", Json.jsonmapper().writeValueAsString(reqParam0));
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);

        // final Map<String, Object> request = new HashMap<>();
        // request.put("command", "signBlock");
        // request.put("blockString", (String) data.get("blockHex"));
        // response = post("http://localhost:14265",
        // Json.jsonmapper().writeValueAsString(request));

        String blockHex = (String) data.get("blockHex");
        byte[] bytes = Utils.HEX.decode(blockHex);
        // Block block = (Block)
        // networkParameters.getDefaultSerializer().makeBlock(bytes);
        // byte[] bytes = rollingBlock.bitcoinSerialize();
        Block block = (Block) Main.params.getDefaultSerializer().makeBlock(bytes);
        // sign transaction
        for (Transaction t : block.getTransactions()) {
            t.addSigned(outKey);
        }
        //proof nonce
        block.solve();

        final Map<String, Object> reqParam1 = new HashMap<>();
        reqParam1.put("command", "saveBlock");
        reqParam1.put("blockString", Utils.HEX.encode(block.bitcoinSerialize()));
        response = post("http://localhost:14265", Json.jsonmapper().writeValueAsString(reqParam0));

        checkGuiThread();
        overlayUI.done();
        // sendBtn.setDisable(true);
        // address.setDisable(true);
        // ((HBox) amountEdit.getParent()).getChildren().remove(amountEdit);
        // ((HBox) btcLabel.getParent()).getChildren().remove(btcLabel);
        // updateTitleForBroadcast();

    }

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
            screen.controller.aesKey = cur;
            screen.controller.address.setText(addressStr);
            screen.controller.amountEdit.setText(amountStr);
            try {
                screen.controller.send(null);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
    }

    private void updateTitleForBroadcast() {
        final int peers = sendResult.tx.getConfidence().numBroadcastPeers();
        titleLabel.setText(String.format("Broadcasting ... seen by %d peers", peers));
    }
}

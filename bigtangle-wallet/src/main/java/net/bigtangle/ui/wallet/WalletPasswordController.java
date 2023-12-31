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

import javafx.application.Platform;

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.ui.wallet.utils.KeyDerivationTasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.time.Duration;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.bigtangle.ui.wallet.utils.GuiUtils.*;

/**
 * User interface for entering a password on demand, e.g. to send money. Also used when encrypting a wallet. Shows a
 * progress meter as we scrypt the password.
 */
public class WalletPasswordController {
    private static final Logger log = LoggerFactory.getLogger(WalletPasswordController.class);

    @FXML HBox buttonsBox;
    @FXML PasswordField pass1;
    @FXML ImageView padlockImage;
    @FXML ProgressIndicator progressMeter;
    @FXML GridPane widgetGrid;
    @FXML Label explanationLabel;

    public Main.OverlayUI<?> overlayUI;

    private SimpleObjectProperty<KeyParameter> aesKey = new SimpleObjectProperty<>();

    public void initialize() {
        progressMeter.setOpacity(0);
        Platform.runLater(pass1::requestFocus);
    }

    @FXML void confirmClicked(ActionEvent event) {
        String password = pass1.getText();
        if (password.isEmpty() || password.length() < 4) {
            informationalAlert(Main.getText("wrongpassword"), Main.getText("passwordagain"));
            return;
        }

        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) Main.walletAppKit.wallet().getKeyCrypter();
        checkNotNull(keyCrypter);   // We should never arrive at this GUI if the wallet isn't actually encrypted.
        KeyDerivationTasks tasks = new KeyDerivationTasks(keyCrypter, password, getTargetTime()) {
            @Override
            protected final void onFinish(KeyParameter aesKey, int timeTakenMsec) {
                checkGuiThread();
                if (Main.walletAppKit.wallet().checkAESKey(aesKey)) {
                    WalletPasswordController.this.aesKey.set(aesKey);
                } else {
                    log.warn("User entered incorrect password");
                    fadeOut(progressMeter);
                    fadeIn(widgetGrid);
                    fadeIn(explanationLabel);
                    fadeIn(buttonsBox);
                    informationalAlert(Main.getText("wrongpassword1"), Main.getText("passwordagain1"));
                }
            }
        };
        progressMeter.progressProperty().bind(tasks.progress);
        tasks.start();

        fadeIn(progressMeter);
        fadeOut(widgetGrid);
        fadeOut(explanationLabel);
        fadeOut(buttonsBox);
    }

    public void cancelClicked(ActionEvent event) {
        overlayUI.done();
    }

    public ReadOnlyObjectProperty<KeyParameter> aesKeyProperty() {
        return aesKey;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static final String TAG = WalletPasswordController.class.getName() + ".target-time";

    // Writes the given time to the wallet as a tag so we can find it again in this class.
    public static void setTargetTime(Duration targetTime) {
        ByteString bytes = ByteString.copyFrom(Longs.toByteArray(targetTime.toMillis()));
        Main.walletAppKit.wallet().setTag(TAG, bytes);
    }

    // Reads target time or throws if not set yet (should never happen).
    public static Duration getTargetTime() throws IllegalArgumentException {
        return Duration.ofMillis(Longs.fromByteArray(Main.walletAppKit.wallet().getTag(TAG).toByteArray()));
    }
}

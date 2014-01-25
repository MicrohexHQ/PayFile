package net.plan99.payfile.gui;

import com.google.bitcoin.core.*;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import net.plan99.payfile.gui.controls.BitcoinAddressValidator;

import static net.plan99.payfile.gui.utils.GuiUtils.crashAlert;
import static net.plan99.payfile.gui.utils.GuiUtils.informationalAlert;
import static net.plan99.payfile.utils.Exceptions.evalUnchecked;

public class SendMoneyController {
    public Button sendBtn;
    public Button cancelBtn;
    public TextField address;
    public Label titleLabel;

    public Main.OverlayUI overlayUi;

    private Wallet.SendResult sendResult;

    // Called by FXMLLoader
    public void initialize() {
        new BitcoinAddressValidator(Main.params, address, sendBtn);
    }

    public void cancel(ActionEvent event) {
        overlayUi.done();
    }

    public void send(ActionEvent event) {
        Address destination = evalUnchecked(() -> new Address(Main.params, address.getText()));
        Wallet.SendRequest req = Wallet.SendRequest.emptyWallet(destination);
        try {
            sendResult = Main.bitcoin.wallet().sendCoins(req);
        } catch (InsufficientMoneyException e) {
            // We couldn't empty the wallet for some reason.
            informationalAlert("Could not empty the wallet",
                    "You may have too little money left in the wallet to make a transaction.");
            overlayUi.done();
            return;
        }

        Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(Transaction result) {
                Platform.runLater(overlayUi::done);
            }

            @Override
            public void onFailure(Throwable t) {
                // We died trying to empty the wallet.
                crashAlert(t);
            }
        });
        sendResult.tx.getConfidence().addEventListener((tx, reason) -> {
            if (reason == TransactionConfidence.Listener.ChangeReason.SEEN_PEERS)
                updateTitleForBroadcast();
        });
        sendBtn.setDisable(true);
        address.setDisable(true);
        updateTitleForBroadcast();
    }

    private void updateTitleForBroadcast() {
        final int peers = sendResult.tx.getConfidence().numBroadcastPeers();
        titleLabel.setText(String.format("Broadcasting ... seen by %d peers", peers));
    }
}

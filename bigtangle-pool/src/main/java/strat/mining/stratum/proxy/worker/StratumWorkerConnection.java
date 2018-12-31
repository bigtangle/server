/**
 * stratum-proxy is a proxy supporting the crypto-currency stratum pool mining
 * protocol.
 * Copyright (C) 2014-2015  Stratehm (stratehm@hotmail.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with multipool-stats-backend. If not, see <http://www.gnu.org/licenses/>.
 */
package strat.mining.stratum.proxy.worker;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import net.bigtangle.pool.PoolServer;
import strat.mining.stratum.proxy.constant.Constants;
import strat.mining.stratum.proxy.exception.ChangeExtranonceNotSupportedException;
import strat.mining.stratum.proxy.exception.TooManyWorkersException;
import strat.mining.stratum.proxy.json.ClientGetVersionRequest;
import strat.mining.stratum.proxy.json.ClientGetVersionResponse;
import strat.mining.stratum.proxy.json.ClientReconnectNotification;
import strat.mining.stratum.proxy.json.ClientShowMessageNotification;
import strat.mining.stratum.proxy.json.JsonRpcError;
import strat.mining.stratum.proxy.json.JsonRpcResponse;
import strat.mining.stratum.proxy.json.MiningAuthorizeRequest;
import strat.mining.stratum.proxy.json.MiningAuthorizeResponse;
import strat.mining.stratum.proxy.json.MiningExtranonceSubscribeRequest;
import strat.mining.stratum.proxy.json.MiningExtranonceSubscribeResponse;
import strat.mining.stratum.proxy.json.MiningGetTransactionsRequest;
import strat.mining.stratum.proxy.json.MiningNotifyNotification;
import strat.mining.stratum.proxy.json.MiningSetDifficultyNotification;
import strat.mining.stratum.proxy.json.MiningSetExtranonceNotification;
import strat.mining.stratum.proxy.json.MiningSubmitRequest;
import strat.mining.stratum.proxy.json.MiningSubmitResponse;
import strat.mining.stratum.proxy.json.MiningSubscribeRequest;
import strat.mining.stratum.proxy.json.MiningSubscribeResponse;
import strat.mining.stratum.proxy.network.StratumConnection;
import strat.mining.stratum.proxy.utils.Timer;
import strat.mining.stratum.proxy.utils.Timer.Task;

public class StratumWorkerConnection extends StratumConnection implements WorkerConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerConnection.class);

    private volatile GetworkJobTemplate currentJob;

    private PoolServer manager;

    private Task subscribeTimeoutTask;
    private Integer subscribeReceiveTimeout = Constants.DEFAULT_SUBSCRIBE_RECEIVE_TIMEOUT;

    private Date isActiveSince;

    // The tail is the salt which is added to extranonce1 and which is unique by
    // connection.
    private String extranonce1Tail;
    private Integer extranonce2Size;

    private Map<String, String> authorizedWorkers;

    private boolean isSetExtranonceNotificationSupported = false;

    private String workerVersion;

    public StratumWorkerConnection(PoolServer manager, Socket socket) {
        super(socket);
        this.manager = manager;
        this.authorizedWorkers = Collections.synchronizedMap(new HashMap<String, String>());

    }

    @Override
    public void startReading() {
        super.startReading();
        subscribeTimeoutTask = new Task() {
            public void run() {
                LOGGER.warn("No subscribe request received from {} in {} ms. Closing connection.", getConnectionName(),
                        subscribeReceiveTimeout);
                // Close the connection if subscribe request is not received at
                // time.
                close();
            }
        };
        subscribeTimeoutTask.setName("SubscribeTimeoutTask-" + getConnectionName());
        Timer.getInstance().schedule(subscribeTimeoutTask, subscribeReceiveTimeout);
    }

    @Override
    protected void onParsingError(String line, Throwable throwable) {
        LOGGER.error("Parsing error on worker connection {}. Failed to parse line {}.", getConnectionName(), line,
                throwable);
    }

    @Override
    protected void onDisconnectWithError(Throwable cause) {
        // manager.onWorkerDisconnection(this, cause);
    }

    @Override
    protected void onNotify(MiningNotifyNotification notify) {
        // Do nothing, should never happen
    }

    @Override
    protected void onShowMessage(ClientShowMessageNotification showMessage) {
        // Do nothing, should never happen
    }

    @Override
    protected void onSetDifficulty(MiningSetDifficultyNotification setDifficulty) {
        // Do nothing, should never happen
    }

    @Override
    protected void onSetExtranonce(MiningSetExtranonceNotification setExtranonce) {
        // Do nothing, should never happen
    }

    @Override
    protected void onClientReconnect(ClientReconnectNotification clientReconnect) {
        // Do nothing, should never happen
    }

    @Override
    protected void onAuthorizeRequest(MiningAuthorizeRequest request) {
        MiningAuthorizeResponse response = new MiningAuthorizeResponse();
        response.setId(request.getId());

        // Throws an exception if the worker is not authorized
        // manager.onAuthorizeRequest(this, request);
        response.setIsAuthorized(true);
        authorizedWorkers.put(request.getUsername(), request.getPassword());

        sendResponse(response);
    }

    @Override
    protected void onSubscribeRequest(MiningSubscribeRequest request) {
        // Once the subscribe request is received, cancel the timeout timer.
        if (subscribeTimeoutTask != null) {
            subscribeTimeoutTask.cancel();
        }

        JsonRpcError error = null;

        if (error == null) {

            try {
                extranonce1Tail = manager.getFreeTail();
            } catch (TooManyWorkersException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            extranonce2Size = manager.getWorkerExtranonce2Size();

        }

        // Send the subscribe response
        MiningSubscribeResponse response = new MiningSubscribeResponse();
        response.setId(request.getId());

        response.setExtranonce1(manager.getExtranonce1() + extranonce1Tail);
        response.setExtranonce2Size(extranonce2Size);
        response.setSubscriptionDetails(getSubscibtionDetails());
        isActiveSince = new Date();

        sendResponse(response);

        // If the subscribe succeed, send the initial notifications (difficulty
        // and notify).
        if (error == null) {
            sendInitialNotifications();
            sendGetVersion();
        }
    }

    @Override
    protected void onSubmitRequest(MiningSubmitRequest request) {
        MiningSubmitResponse response = new MiningSubmitResponse();
        response.setId(request.getId());
        JsonRpcError error = null;

        if (authorizedWorkers.get(request.getWorkerName()) != null) {
            // Modify the request to add the tail of extranonce1 to the
            // submitted extranonce2
            request.setExtranonce2(extranonce1Tail + request.getExtranonce2());

            boolean isShareValid = true;

            if (isShareValid) {
                // onSubmitRequest(this, request);
            } else {
                error = new JsonRpcError();
                error.setCode(JsonRpcError.ErrorCode.LOW_DIFFICULTY_SHARE.getCode());
                error.setMessage("Share is above the target (proxy check)");
                response.setErrorRpc(error);
                LOGGER.debug("Share submitted by {}@{} is above the target. The share is not submitted to the pool.",
                        (String) request.getWorkerName(), getConnectionName());
                sendResponse(response);
            }
        } else {
            error = new JsonRpcError();
            error.setCode(JsonRpcError.ErrorCode.UNAUTHORIZED_WORKER.getCode());
            error.setMessage("Submit failed. Worker not authorized on this connection.");
            response.setErrorRpc(error);
            sendResponse(response);
        }
    }

    @Override
    protected void onExtranonceSubscribeRequest(MiningExtranonceSubscribeRequest request) {
        this.isSetExtranonceNotificationSupported = true;

        MiningExtranonceSubscribeResponse response = new MiningExtranonceSubscribeResponse();
        response.setId(request.getId());
        response.setResult(Boolean.TRUE);
        sendResponse(response);
    }

    @Override
    protected void onGetVersionRequest(ClientGetVersionRequest request) {
        LOGGER.warn("Worker {} send a GetVersion request. This should not happen.", getConnectionName());
    }

    @Override
    protected void onGetTransactionsRequest(MiningGetTransactionsRequest request) {
        JsonRpcResponse response = new JsonRpcResponse();
        request.setId(request.getId());
        response.setResult(Lists.newArrayList());
        JsonRpcError errorObject = new JsonRpcError();
        errorObject.setCode(20);
        errorObject.setMessage("mining.get_transactions is not supported by proxy.");
        response.setErrorRpc(errorObject);
        sendResponse(response);
    }

    /**
     * Called when the pool has answered to a submit request.
     * 
     * @param workerRequest
     * @param poolResponse
     */
    public void onPoolSubmitResponse(MiningSubmitRequest workerRequest, MiningSubmitResponse poolResponse) {

        MiningSubmitResponse workerResponse = new MiningSubmitResponse();
        workerResponse.setId(workerRequest.getId());
        workerResponse.setIsAccepted(poolResponse.getIsAccepted());
        workerResponse.setError(poolResponse.getError());

        sendResponse(workerResponse);
    }

    /**
     * Called when the pool change its extranonce. Send the extranonce change to
     * the worker. Throw an exception if the extranonce change is not supported
     * on the fly.
     */
    public void onPoolExtranonceChange() throws ChangeExtranonceNotSupportedException {
    }

    @Override
    protected void onAuthorizeResponse(MiningAuthorizeRequest request, MiningAuthorizeResponse response) {
        LOGGER.warn("Worker {} send an Authorize response. This should not happen.", getConnectionName());
    }

    @Override
    protected void onSubscribeResponse(MiningSubscribeRequest request, MiningSubscribeResponse response) {
        LOGGER.warn("Worker {} send a Subscribe response. This should not happen.", getConnectionName());
    }

    @Override
    protected void onSubmitResponse(MiningSubmitRequest request, MiningSubmitResponse response) {
        LOGGER.warn("Worker {} send a Submit response. This should not happen.", getConnectionName());
    }

    @Override
    protected void onExtranonceSubscribeResponse(MiningExtranonceSubscribeRequest request,
            MiningExtranonceSubscribeResponse response) {
        LOGGER.warn("Worker {} send an Extranonce subscribe response. This should not happen.", getConnectionName());
    }

    @Override
    protected void onGetVersionResponse(ClientGetVersionRequest request, ClientGetVersionResponse response) {
        workerVersion = response.getVersion();
    }

    /**
     * Build a list of subscription details.
     * 
     * @return
     */
    private List<Object> getSubscibtionDetails() {
        List<Object> details = new ArrayList<Object>();
        List<Object> setDifficultySubscribe = new ArrayList<>();
        setDifficultySubscribe.add(MiningSetDifficultyNotification.METHOD_NAME);
        setDifficultySubscribe.add("b4b6693b72a50c7116db18d6497cac52");
        details.add(setDifficultySubscribe);
        List<Object> notifySubscribe = new ArrayList<Object>();
        notifySubscribe.add(MiningNotifyNotification.METHOD_NAME);
        notifySubscribe.add("ae6812eb4cd7735a302a8a9dd95cf71f");
        details.add(notifySubscribe);
        return details;
    }

    /**
     * Send the first notifications to the worker. The setDifficulty and the
     * current job.
     */
    private void sendInitialNotifications() {
        // Send the setExtranonce notif
        if (isSetExtranonceNotificationSupported) {
            MiningSetExtranonceNotification extranonceNotif = new MiningSetExtranonceNotification();
            extranonceNotif.setExtranonce1(manager.getExtranonce1() + extranonce1Tail);
            extranonceNotif.setExtranonce2Size(extranonce2Size);
            sendNotification(extranonceNotif);
            updateBlockExtranonce();
            LOGGER.debug("Initial extranonce sent to {}.", getConnectionName());
        }

        // Send the difficulty if available
        Double difficulty = manager.getDifficulty();
        if (difficulty != null) {
            MiningSetDifficultyNotification setDifficulty = new MiningSetDifficultyNotification();
            setDifficulty.setDifficulty(difficulty);
            sendNotification(setDifficulty);
            LOGGER.debug("Initial difficulty sent to {}.", getConnectionName());
        }

        // Then send the first job if available.
        MiningNotifyNotification notify = manager.getCurrentStratumJob();
        if (notify != null) {
            sendNotification(notify);
            LOGGER.debug("Initial job sent to {}.", getConnectionName());
        }

    }

    /**
     * Send a GetVersion request to the worker.
     */
    private void sendGetVersion() {
        ClientGetVersionRequest request = new ClientGetVersionRequest();
        sendRequest(request);
    }

    @Override
    public void close() {
        super.close();

    }

    @Override
    public Map<String, String> getAuthorizedWorkers() {
        Map<String, String> result = null;
        synchronized (authorizedWorkers) {
            result = new HashMap<>(authorizedWorkers);
        }
        return result;
    }

    @Override
    public double getAcceptedHashrate() {
        return 0;
    }

    @Override
    public double getRejectedHashrate() {
        return 0;
    }

    @Override
    public Date getActiveSince() {
        return isActiveSince;
    }

    public boolean isSetExtranonceNotificationSupported() {
        return isSetExtranonceNotificationSupported;
    }

    @Override
    public void onPoolShowMessage(ClientShowMessageNotification showMessage) {
        ClientShowMessageNotification notification = new ClientShowMessageNotification();
        notification.setMessage(showMessage.getMessage());
        sendNotification(notification);
    }

    /**
     * Update the block header based on the notification
     * 
     * @param notification
     */
    private void updateBlockHeader(MiningNotifyNotification notification) {
    }

    /**
     * Update the difficulty to solve for the current block
     */
    private void updateBlockDifficulty() {
    }

    /**
     * Update the extranonce1 value of the current block.
     */
    private void updateBlockExtranonce() {
    }

    @Override
    public String getWorkerVersion() {
        return workerVersion;
    }

    @Override
    public void onPoolDifficultyChanged(MiningSetDifficultyNotification notification) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onPoolNotify(MiningNotifyNotification notification) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setSamplingHashesPeriod(Integer samplingHashesPeriod) {
        // TODO Auto-generated method stub

    }

    /**
     * Update the current job template from the stratum notification.
     */
    private void updateCurrentJobTemplateFromStratumJob(MiningNotifyNotification notification) {
        LOGGER.debug("Update getwork job for connection {}.", getConnectionName());

        currentJob = new GetworkJobTemplate(notification.getJobId(), notification.getBitcoinVersion(),
                notification.getPreviousHash(), notification.getCurrentNTime(), notification.getNetworkDifficultyBits(),
                notification.getMerkleBranches(), notification.getCoinbase1(), notification.getCoinbase2(),
                extranonce1Tail);

    }
}

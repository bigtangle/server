package net.bigtangle.pool;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AtomicDouble;

import strat.mining.stratum.proxy.callback.ResponseReceivedCallback;
import strat.mining.stratum.proxy.constant.Constants;
import strat.mining.stratum.proxy.exception.AuthorizationException;
import strat.mining.stratum.proxy.exception.TooManyWorkersException;
import strat.mining.stratum.proxy.json.ClientShowMessageNotification;
import strat.mining.stratum.proxy.json.JsonRpcRequest;
import strat.mining.stratum.proxy.json.MiningAuthorizeRequest;
import strat.mining.stratum.proxy.json.MiningAuthorizeResponse;
import strat.mining.stratum.proxy.json.MiningExtranonceSubscribeRequest;
import strat.mining.stratum.proxy.json.MiningExtranonceSubscribeResponse;
import strat.mining.stratum.proxy.json.MiningNotifyNotification;
import strat.mining.stratum.proxy.json.MiningSubmitRequest;
import strat.mining.stratum.proxy.json.MiningSubmitResponse;
import strat.mining.stratum.proxy.json.MiningSubscribeRequest;
import strat.mining.stratum.proxy.json.MiningSubscribeResponse;
import strat.mining.stratum.proxy.utils.Timer;
import strat.mining.stratum.proxy.utils.Timer.Task;
import strat.mining.stratum.proxy.worker.StratumWorkerConnection;

public class PoolServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PoolServer.class);

    private boolean closeRequested = false;
    private ServerSocket serverSocket;
    private Thread listeningThread;

    private String name;
    private String host;
    private URI uri;
    private String username;
    private String password;

    private Double difficulty;
    private String extranonce1;
    private Integer extranonce2Size;

    private Date readySince;
    private boolean isReady;
    private boolean isEnabled;
    private boolean isStable;

    private boolean isAppendWorkerNames;
    private boolean isUseWorkerPassword;
    private boolean isActive;
    private Date activeSince;

    private String workerSeparator;

    // Contains all available tails in Hexa format.
    private Deque<String> tails;

    private List<StratumWorkerConnection> workerConnections=new CopyOnWriteArrayList<StratumWorkerConnection>();;

    private MiningNotifyNotification currentJob;

    private Task reconnectTask;
    private Task notifyTimeoutTask;
    private Task stabilityTestTask;
    private Task subscribeResponseTimeoutTask;

    private Boolean isExtranonceSubscribeEnabled = false;

    private Integer numberOfSubmit = 1;

    private Integer priority;
    private Integer weight;

    private AtomicDouble acceptedDifficulty;
    private AtomicDouble rejectedDifficulty;

    private Integer noNotifyTimeout = Constants.DEFAULT_NOTIFY_NOTIFICATION_TIMEOUT;

    // Store the callbacks to call when the pool responds to a submit request.
    private Map<Object, ResponseReceivedCallback<MiningSubmitRequest, MiningSubmitResponse>> submitCallbacks;

    // Store the callbacks to call when the pool responds to worker authorize
    // request.
    private Map<Object, ResponseReceivedCallback<MiningAuthorizeRequest, MiningAuthorizeResponse>> authorizeCallbacks;

    private Set<String> authorizedWorkers;
    private Map<String, CountDownLatch> pendingAuthorizeRequests;

    private String lastStopCause;
    private Date lastStopDate;

    private Integer numberOfDisconnections;

    private String lastPoolMessage;

    /**
     * Send the subscribe request.
     */
    public void sendSubscribeRequest() {
        sendSuggestedDifficultyRequest();
        MiningSubscribeRequest request = new MiningSubscribeRequest();
        startSubscribeTimeoutTimer();
       sendRequest(request);
    }

    /**
     * Start the timer which check the subscribe response timeout
     */
    private void startSubscribeTimeoutTimer() {
        subscribeResponseTimeoutTask = new Timer.Task() {
            public void run() {
                LOGGER.warn("Subscribe response timeout. Stopping the pool");
              //  stopPool("Pool subscribe response timed out.");

            }
        };
        subscribeResponseTimeoutTask.setName("SubscribeTimeoutTask-" + getName());
        Timer.getInstance().schedule(subscribeResponseTimeoutTask, 5000);
    }

    /**
     * Stop the timer which check the subscribe response timeout
     */
    private void stopSubscribeTimeoutTimer() {
        if (subscribeResponseTimeoutTask != null) {
            subscribeResponseTimeoutTask.cancel();
            subscribeResponseTimeoutTask = null;
        }
    }


    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public URI getUri() {
        return uri;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public String getExtranonce1() {
        return extranonce1;
    }

    public Integer getExtranonce2Size() {
        return extranonce2Size;
    }

    public boolean isReady() {
        return isReady;
    }

    public boolean isStable() {
        return isStable;
    }

    public Double getDifficulty() {
        return difficulty;
    }

    public void processSubscribeResponse(MiningSubscribeRequest request, MiningSubscribeResponse response) {
        stopSubscribeTimeoutTimer();
        extranonce1 = response.getExtranonce1();
        extranonce2Size = response.getExtranonce2Size();

        sendSubscribeExtranonceRequest();

        // Start the notify timeout timer
        resetNotifyTimeoutTimer();

        // If appendWorkerNames is true, do not try to authorize the pool
        // username. Workers will be authorized on connection. So, just
        // declare the pool as ready.

        // Send the authorize request if worker names are not appended.
        MiningAuthorizeRequest authorizeRequest = new MiningAuthorizeRequest();
        authorizeRequest.setUsername(username);
        authorizeRequest.setPassword(password);
        sendRequest(authorizeRequest);

    }

     public void sendRequest(JsonRpcRequest request) {
     for(  StratumWorkerConnection c: workerConnections)    {
         if(c.isConnected() ) {
             c.sendRequest(request);
         }
     }
    }
    /**
     * Send an extranonce subscribe request to the pool.
     */
    private void sendSubscribeExtranonceRequest() {
        if (isExtranonceSubscribeEnabled) {
            // Else try to subscribe to extranonce change notification
            MiningExtranonceSubscribeRequest extranonceRequest = new MiningExtranonceSubscribeRequest();
            sendRequest(extranonceRequest);
        }
    }

    public void processSubscribeExtranonceResponse(MiningExtranonceSubscribeRequest request,
            MiningExtranonceSubscribeResponse response) {
        if (response.getIsSubscribed()) {
            LOGGER.info("Extranonce change subscribed on pool {}.", getName());
        } else {
            LOGGER.info("Failed to subscribe to extranonce change on pool {}. Error: {}", getName(),
                    response.getJsonError());
        }
    }

    /**
     * Send the suggested difficulty to the pool.
     */
    private void sendSuggestedDifficultyRequest() {

    }

    /**
     * Return true if the authorize response is positive. Else, return false.
     * 
     * @param request
     * @param response
     * @return
     */
    private boolean isAuthorized(MiningAuthorizeRequest request, MiningAuthorizeResponse response) {
        // Check the P2Pool authorization. Authorized if the the result is null
        // and there is no error.
        boolean isP2PoolAuthorized = (response.getIsAuthorized() == null && response.getError() == null);

        // Check if the user is authorized in the response.
        boolean isAuthorized = isP2PoolAuthorized || (response.getIsAuthorized() != null && response.getIsAuthorized());

        return isAuthorized;
    }

    public void processSubmitResponse(MiningSubmitRequest request, MiningSubmitResponse response) {
        ResponseReceivedCallback<MiningSubmitRequest, MiningSubmitResponse> callback = submitCallbacks
                .remove(response.getId());
        callback.onResponseReceived(request, response);
    }

    public void processShowMessage(ClientShowMessageNotification showMessage) {
        lastPoolMessage = showMessage.getMessage();

    }

    /**
     * Send a submit request and return the submit response.
     * 
     * @param workerRequest
     * @return
     */
    public void submitShare(MiningSubmitRequest workerRequest,
            ResponseReceivedCallback<MiningSubmitRequest, MiningSubmitResponse> callback) {
        MiningSubmitRequest poolRequest = new MiningSubmitRequest();
        poolRequest.setExtranonce2(workerRequest.getExtranonce2());
        poolRequest.setJobId(workerRequest.getJobId());
        poolRequest.setNonce(workerRequest.getNonce());
        poolRequest.setNtime(workerRequest.getNtime());

        if (isAppendWorkerNames) {
            poolRequest.setWorkerName((username == null ? "" : username)
                    + (workerSeparator == null ? "" : workerSeparator) + workerRequest.getWorkerName());
        } else {
            poolRequest.setWorkerName(username);
        }

        submitCallbacks.put(poolRequest.getId(), callback);
       sendRequest(poolRequest);
    }

    public void onDisconnectWithError(Throwable cause) {
        LOGGER.error("Disconnection of pool {}.", this, cause);

        String causeMessage = null;
        // If it is an EOFException, do not log any messages since an error has
        // surely occured on a request.
        if (!(cause instanceof EOFException)) {
            causeMessage = cause.getMessage();
        } else {
            // If it is an EOFException, log it only if a previous cause has not
            // been defined. If a cause is already defined before this one, it
            // is the real cause and this one is just the pool disconnection due
            // to the previous cause.
            // So set the EOFException message if the exception has happened
            // more than 1 second after the previous cause.
            if (lastStopDate == null || System.currentTimeMillis() > lastStopDate.getTime() + 1000) {
                causeMessage = cause.getMessage();
            }
        }

  

    }

    /**
     * Return a free tail for this pool.
     * 
     * @return
     * @throws TooManyWorkersException
     */
    public String getFreeTail() throws TooManyWorkersException {
        if(tails == null) return "";
        if (tails.size() > 0) {
            return tails.poll();
        } else {
            throw new TooManyWorkersException("No more tails available on pool " + getName());
        }
    }

    /**
     * Release the given tail.
     * 
     * @param tail
     */
    public void releaseTail(String tail) {
        if (tail != null && !tails.contains(tail)) {
            tails.add(tail);
        }
    }

    /**
     * Return the extranonce2 size
     * 
     * @return
     */
    public Integer getWorkerExtranonce2Size() {
        return extranonce2Size;
    }

    public MiningNotifyNotification getCurrentStratumJob() {
        return currentJob;
    }

    public Boolean isExtranonceSubscribeEnabled() {
        return isExtranonceSubscribeEnabled;
    }

    public void setExtranonceSubscribeEnabled(Boolean isExtranonceSubscribeEnabled) {
        this.isExtranonceSubscribeEnabled = isExtranonceSubscribeEnabled;
    }

    public Integer getNumberOfSubmit() {
        return numberOfSubmit;
    }

    public void setNumberOfSubmit(Integer numberOfSubmit) {
        this.numberOfSubmit = numberOfSubmit;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Pool [name=");
        builder.append(name);
        builder.append(", host=");
        builder.append(host);
        builder.append(", username=");
        builder.append(username);
        builder.append(", password=");
        builder.append(password);
        builder.append(", readySince=");
        builder.append(readySince);
        builder.append(", isReady=");
        builder.append(isReady);
        builder.append(", isEnabled=");
        builder.append(isEnabled);
        builder.append(", isStable=");
        builder.append(isStable);
        builder.append(", priority=");
        builder.append(priority);
        builder.append(", weight=");
        builder.append(weight);
        builder.append("]");
        return builder.toString();
    }

    public Double getAcceptedDifficulty() {
        return acceptedDifficulty.get();
    }

    public Double getRejectedDifficulty() {
        return rejectedDifficulty.get();
    }

    public Date getReadySince() {
        return readySince;
    }

    public Boolean getIsExtranonceSubscribeEnabled() {
        return isExtranonceSubscribeEnabled;
    }

    /**
     * Reset the notify timeoutTimer
     */
    private void resetNotifyTimeoutTimer() {
        if (noNotifyTimeout > 0) {
            if (notifyTimeoutTask != null) {
                notifyTimeoutTask.cancel();
                notifyTimeoutTask = null;
            }

            notifyTimeoutTask = new Task() {
                public void run() {
                    LOGGER.warn("No mining.notify received from pool {} for {} ms. Stopping the pool...", getName(),
                            noNotifyTimeout);
                    // If we have not received notify notification since DEALY,
                    // stop the pool and try to reconnect.
          
                }
            };
            notifyTimeoutTask.setName("NotifyTimeoutTask-" + getName());
            Timer.getInstance().schedule(notifyTimeoutTask, noNotifyTimeout * 1000);
        }
    }

    /**
     * Cancel all active timers
     */
    private synchronized void cancelTimers() {
        LOGGER.debug("Cancel all timers of pool {}.", getName());

        if (stabilityTestTask != null) {
            stabilityTestTask.cancel();
            stabilityTestTask = null;
        }

        if (reconnectTask != null) {
            reconnectTask.cancel();
            reconnectTask = null;
        }

        if (notifyTimeoutTask != null) {
            notifyTimeoutTask.cancel();
            notifyTimeoutTask = null;
        }

        if (subscribeResponseTimeoutTask != null) {
            subscribeResponseTimeoutTask.cancel();
            subscribeResponseTimeoutTask = null;
        }
    }

    /**
     * Authorize the given worker on the pool. Throws an exception if the worker
     * is not authorized on the pool. This method blocs until the response is
     * received from the pool.
     * 
     * @param workerRequest
     * @param callback
     */
    public void authorizeWorker(MiningAuthorizeRequest workerRequest) throws AuthorizationException {
        // Authorize the worker only if isAppendWorkerNames is true. If true, it
        // means that each worker has to be authorized. If false, the
        // authorization has already been done with the configured username.
        if (isAppendWorkerNames) {
            String finalUserName = (username == null ? "" : username) + (workerSeparator == null ? "" : workerSeparator)
                    + workerRequest.getUsername();

            // If the worker is already authorized, do nothing
            if (authorizedWorkers.contains(finalUserName)) {
                LOGGER.debug("Worker {} already authorized on the pool {}.", finalUserName, getName());
            } else {
                LOGGER.debug("Authorize worker {} on pool {}.", finalUserName, getName());

                // Create a latch to wait the authorization response.
                CountDownLatch responseLatch = null;
                boolean sendRequest = false;

                // Synchronized to be sure that if too requests are processed at
                // the same time, only one will be performed.
                synchronized (pendingAuthorizeRequests) {
                    if (!pendingAuthorizeRequests.containsKey(finalUserName)) {
                        responseLatch = new CountDownLatch(1);
                        pendingAuthorizeRequests.put(finalUserName, responseLatch);
                        sendRequest = true;
                    } else {
                        responseLatch = pendingAuthorizeRequests.get(finalUserName);
                    }
                }

                if (sendRequest) {
                    try {
                        // Response wrapper used to store the pool response
                        // values
                        final MiningAuthorizeResponse poolResponseWrapper = new MiningAuthorizeResponse();

                        MiningAuthorizeRequest poolRequest = new MiningAuthorizeRequest();
                        poolRequest.setUsername(finalUserName);
                        poolRequest.setPassword(isUseWorkerPassword ? workerRequest.getPassword() : this.password);
                        // Prepare the callback to call when response is
                        // received.
                        final CountDownLatch closureLatch = responseLatch;
                        authorizeCallbacks.put(poolRequest.getId(),
                                new ResponseReceivedCallback<MiningAuthorizeRequest, MiningAuthorizeResponse>() {
                                    public void onResponseReceived(MiningAuthorizeRequest request,
                                            MiningAuthorizeResponse response) {
                                        // Recopy values to allow blocked thread
                                        // to access the
                                        // response values.
                                        poolResponseWrapper.setId(response.getId());
                                        poolResponseWrapper.setResult(response.getResult());

                                        // Unblock the blocked thread.
                                        closureLatch.countDown();
                                    }
                                });

                        // Send the request.
                        sendRequest(poolRequest);

                        // Wait for the response
                        waitForAuthorizeResponse(finalUserName, responseLatch);

                        // Check the response values
                        if (poolResponseWrapper.getIsAuthorized() == null || !poolResponseWrapper.getIsAuthorized()) {
                            // If the worker is not authorized, throw an
                            // exception.
                            throw new AuthorizationException(
                                    "Worker " + finalUserName + " is not authorized on pool " + getName() + ". Cause: "
                                            + (poolResponseWrapper.getJsonError() != null
                                                    ? poolResponseWrapper.getJsonError()
                                                    : "none."));
                        }
                    } finally {
                        // Once the request is over, remove the latch for this
                        // username.
                        synchronized (pendingAuthorizeRequests) {
                            pendingAuthorizeRequests.remove(finalUserName);
                        }
                    }
                } else {
                    // Wait for the response
                    waitForAuthorizeResponse(finalUserName, responseLatch);

                    if (!authorizedWorkers.contains(finalUserName)) {
                        throw new AuthorizationException("Worker " + finalUserName + " not authorized on pool "
                                + getName()
                                + " after a delegated request. See the delegated request response in the logs for more details.");
                    }
                }
            }
        }
    }

    /**
     * 
     * @param userName
     * @param latch
     * @throws AuthorizationException
     */
    private void waitForAuthorizeResponse(String userName, CountDownLatch latch) throws AuthorizationException {
        // Wait for the response for 5 seconds max.
        boolean isTimeout = false;
        try {
            isTimeout = !latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.info("Interruption on pool {} during authorization for user {}.", getName(), userName);
            throw new AuthorizationException(
                    "Interruption of authorization of user " + userName + " on pool " + getName(), e);
        }

        if (isTimeout) {
            LOGGER.warn("Timeout of worker {} authorization on pool {}.", userName, getName());
            throw new AuthorizationException("Timeout of worker " + userName + " authorization on pool " + getName());
        }
    }

    public void setAppendWorkerNames(boolean isAppendWorkerNames) {
        if (isReady) {
            throw new IllegalStateException("The pool is ready. Stop the pool before updating the appendWorkerNames.");
        }
        this.isAppendWorkerNames = isAppendWorkerNames;
    }

    public void setUseWorkerPassword(boolean isUseWorkerPassword) {
        this.isUseWorkerPassword = isUseWorkerPassword;
    }

    public void setWorkerSeparator(String workerSeparator) {
        this.workerSeparator = workerSeparator;
    }

    public void setIsActive(boolean isActive) {
        if (isActive != this.isActive) {
            this.activeSince = isActive ? new Date() : null;
        }
        this.isActive = isActive;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public Date getActiveSince() {
        return this.activeSince;
    }

    public String getLastStopCause() {
        return lastStopCause;
    }

    public Date getLastStopDate() {
        return lastStopDate;
    }

    public void setHost(String host) {
        if (isReady) {
            throw new IllegalStateException("The pool is ready. Stop the pool before updating the host.");
        }
        this.host = host;
    }

    public void setUsername(String username) {
        if (isReady) {
            throw new IllegalStateException("The pool is ready. Stop the pool before updating the username.");
        }
        this.username = username;
    }

    public void setPassword(String password) {
        if (isReady) {
            throw new IllegalStateException("The pool is ready. Stop the pool before updating the password.");
        }
        this.password = password;
    }

    public void setIsExtranonceSubscribeEnabled(Boolean isExtranonceSubscribeEnabled) {
        if (isReady) {
            throw new IllegalStateException(
                    "The pool is ready. Stop the pool before updating the extranonceSubscribeEnabled.");
        }
        this.isExtranonceSubscribeEnabled = isExtranonceSubscribeEnabled;

    }

    public boolean isAppendWorkerNames() {
        return isAppendWorkerNames;
    }

    public boolean isUseWorkerPassword() {
        return isUseWorkerPassword;
    }

    public String getWorkerSeparator() {
        return workerSeparator;
    }

    public Integer getNumberOfDisconnections() {
        return this.numberOfDisconnections;
    }

    /**
     * Return the pool uptime in seconds
     * 
     * @return
     */
    public Long getUptime() {
        Long uptime = 0L;
        if (isReady && readySince != null) {
            uptime = (System.currentTimeMillis() - readySince.getTime());
        }
        return uptime;
    }

    public String getLastPoolMessage() {
        return this.lastPoolMessage;
    }

    @PostConstruct
    public void startRun() {
        try {
            startListeningIncomingConnections(null, 3334);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    /**
     * Start listening incoming connections on the given interface and port. If
     * bindInterface is null, bind to 0.0.0.0
     * 
     * @param bindInterface
     * @param port
     * @throws IOException
     */
    public void startListeningIncomingConnections(String bindInterface, Integer port) throws IOException {
        if (bindInterface == null) {
            serverSocket = new ServerSocket(port, 0);
        } else {
            serverSocket = new ServerSocket(port, 0, InetAddress.getByName(bindInterface));
        }
        LOGGER.info("ServerSocket opened on {}.", serverSocket.getLocalSocketAddress());

        listeningThread = new Thread() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
                    Socket incomingConnectionSocket = null;
                    try {
                        LOGGER.debug("Waiting for incoming connection on {}...", serverSocket.getLocalSocketAddress());
                        incomingConnectionSocket = serverSocket.accept();
                        incomingConnectionSocket.setTcpNoDelay(true);
                        incomingConnectionSocket.setKeepAlive(true);
                        LOGGER.info("New connection on {} from {}.", serverSocket.getLocalSocketAddress(),
                                incomingConnectionSocket.getRemoteSocketAddress());
                        StratumWorkerConnection    connection = new StratumWorkerConnection(PoolServer.this, incomingConnectionSocket);

                        connection.startReading();
                        workerConnections.add(connection);
                    } catch (Exception e) {
                        // Do not log the error if a close has been requested
                        // (as the error is expected ans is part of the shutdown
                        // process)
                        if (!closeRequested) {
                            LOGGER.error("Error on the server socket {}.", serverSocket.getLocalSocketAddress(), e);
                        }
                    }
                }

                LOGGER.info("Stop to listen incoming connection on {}.", serverSocket.getLocalSocketAddress());
            }
        };
        listeningThread.setName("StratumProxyManagerSeverSocketListener");
        listeningThread.setDaemon(true);
        listeningThread.start();
    }

}

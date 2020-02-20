/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.comms.exceptions.UnknownChannelIdException;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * The Protocol class is the base class for all protocols. It defines a series
 * of method needed to run any protocol, for example, access to storage and
 * communication layers.
 * 
 * The basic structure is that a Protocol consists of a number of ProtocolSteps.
 * Each Step is run sequentially. If a Step reports an exception the entire
 * protocol stops. The protocol waits for each Step to complete prior to moving
 * to the next one. The decision on when a Step is complete is determined by the
 * Step itself.
 * 
 * ProtocolSteps are all instantiated and can receive messages from other peers
 * prior to being run. This removes the need for individual peers to be strictly
 * lock stepped.
 * 
 * A protocol runs within a ProtocolServer, which can run multiple protocols
 * concurrently. Protocols must provide a unique ID so that they can be
 * distinguished in terms of messages and storage.
 * 
 * Protocols runs as callable instances which return a boolean value, true if
 * they successfully completed, false if not.
 * 
 * @author Chris Culnane
 *
 */
public abstract class Protocol<E extends CommunicationLayerMessage<?> & StorageObject<E>> implements Callable<Boolean> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(Protocol.class);

	/**
	 * ProtocolServer - the server this Protocol is running in
	 */
	private ProtocolServer<E> server = null;
	/**
	 * ArrayList of ProtocolSteps in the order they are to be run
	 */
	protected ArrayList<ProtocolStep<Boolean, E>> steps = new ArrayList<ProtocolStep<Boolean, E>>();
	/**
	 * HashMap of ProtocolSteps indexed by the ProtocolStep id
	 */
	protected HashMap<String, ProtocolStep<Boolean, E>> stepIndex = new HashMap<String, ProtocolStep<Boolean, E>>();

	/**
	 * Executor service for running the current ProtocolStep
	 */
	private ExecutorService runner = Executors.newSingleThreadExecutor();

	/**
	 * String holding the protocol id
	 */
	private String protocolId;

	/**
	 * Inner class define the JSON keys used in communication messages
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static class MESSAGES {
		/**
		 * Key that refers to the target protocol step
		 */
		public static final String STEP = "_protocolStep";
		/**
		 * Key that refers to the sender of the message
		 */
		public static final String SENDER = "_protocolSender";

		/**
		 * Key that refers to the target of the message
		 */
		public static final String TARGET = "_protocolTarget";

		/**
		 * Key that refers to the target of the message
		 */
		public static final String PROTOCOL_ID = "_protocolId";

		/**
		 * Key that refers to a flush message
		 */
		public static final String FLUSH_MSG = "_flush";

		/**
		 * Value that refers to target being a broadcast
		 */
		public static final String BROADCAST = "broadcast";

	}

	/**
	 * Create a new Protocol with the specified ID
	 * 
	 * @param protocolId
	 *            String containing protocol id
	 */
	public Protocol(String protocolId) {
		this.protocolId = protocolId;
	}

	/**
	 * Adds a StorageObject to the underlying storage system, indexed by the
	 * specified key
	 * 
	 * @param id
	 *            String key to index the StorageObject with
	 * @param msg
	 *            StorageObject to be stored
	 */
	public void addToStorage(String id, StorageObject<E> msg) {
		this.server.addToStorage(getStoragePrefix() + "." + id, msg);
	}

	/**
	 * Gets the ProtocolServer id
	 * 
	 * @return String containing the ProtocolServer Id
	 */
	public String getServerId() {
		return this.server.getId();
	}

	/**
	 * Gets a Set of String of the PeerIds associated with the ProtocolServer -
	 * this will be the channels created by the ProtocolServer
	 * 
	 * @return Set of Strings containing peer channels Ids
	 */
	public Set<String> getPeerIds() {
		return this.server.getPeerIds();
	}

	/**
	 * Adds a message to the underlying transcript. The transcript is just a log
	 * of all appropriate incoming and outgoing messages.
	 * 
	 * @param msg
	 *            message to add to transcript
	 */
	public void addToTranscript(E msg) {
		this.server.addToTranscript(msg);
	}

	/**
	 * Gets the storage prefix for this protocol - this is used to distinguish
	 * shared ProtocolSteps that might be used in multiple protocols
	 * 
	 * @return String of storage prefix for this Protocol
	 */
	public abstract String getStoragePrefix();

	/**
	 * Sets the ProtocolServer that this Protocol will be run on
	 * 
	 * @param server
	 *            ProtocolServer that will run this Protocol
	 */
	public void setProtocolServer(ProtocolServer<E> server) {
		this.server = server;
	}

	/**
	 * Gets the Protocol Id
	 * 
	 * @return String containing ProtocolId
	 */
	public String getId() {
		return this.protocolId;
	}

	/**
	 * Adds a ProtocolStep to this Protocol. This will be added to the end of
	 * the current list and also added to the map indexing ProtocolSteps by id
	 * 
	 * @param step
	 *            ProtocolStep to be added
	 */
	public void addProtocolStep(ProtocolStep<Boolean, E> step) {
		step.setProtocol(this);
		steps.add(step);
		stepIndex.put(step.getStepId(), step);
	}

	/**
	 * Broadcasts the specified message to all peer channels
	 * 
	 * @param msg
	 *            message to broadcast
	 * @throws IOException
	 */
	public void sendBroadcast(E msg) throws IOException {
		msg.set(MESSAGES.PROTOCOL_ID, this.getId());
		msg.set(MESSAGES.TARGET, MESSAGES.BROADCAST);
		this.server.sendBroadcast(msg);
		this.server.addToTranscript(msg);
	}

	/**
	 * Sends a message to a specific peer, as specified by sentTo
	 * 
	 * @param msg
	 *            msg to send
	 * @param sendTo
	 *            String of id to send message to
	 * @throws UnknownChannelIdException
	 * @throws IOException
	 */
	public void sendMessage(E msg, String sendTo) throws UnknownChannelIdException, IOException {
		msg.set(MESSAGES.PROTOCOL_ID, this.getId());
		msg.set(MESSAGES.TARGET, sendTo);
		this.server.sendMessage(msg, sendTo);
		this.server.addToTranscript(msg);
	}

	/**
	 * Causes any queued messages to be flushed to receiving protocols. This is
	 * necessary because the ProtocolServer could be running or start before a
	 * Protocol has been added. The ProtocolServer queues messages for which the
	 * target Protocol doesn't yet exist. This causes that queue to be flushed
	 * through, by simulating the arrival of a new message. This method should
	 * be called after a Protocol has been added to a ProtocolServer or before
	 * it runs.
	 */
	public void flushMessageQueue() {
		E flush = this.server.getMessageConstructor().createEmptyMessage();
		flush.set(MESSAGES.PROTOCOL_ID, this.getId());
		flush.set(MESSAGES.FLUSH_MSG, MESSAGES.FLUSH_MSG);
		this.server.messageReceived(flush);
	}

	/**
	 * Runs the actual Protocol by stepping through each ProtocolStep and
	 * waiting for each to complete before moving on. If the ProtocolStep
	 * returns false an exception is thrown. Likewise, if an exception is thrown
	 * whilst processing the ProtocolStep a further exception will be thrown by
	 * the Protocol, stopping further processing.
	 * 
	 * @throws ProtocolExecutionException
	 */
	private void runProtocol() throws ProtocolExecutionException {
		if (server == null) {
			throw new ProtocolExecutionException("Cannot run protocol without server having been set");
		}
		try{
		flushMessageQueue();
		for (ProtocolStep<Boolean, E> step : steps) {
			logger.info("Running protocol step:{}", step.getStepId());
			Future<Boolean> result = this.runner.submit(step);

			try {
				if (!result.get()) {
					throw new ProtocolExecutionException("Protocol execution was stopped");
				}
				logger.info("Finished protocol step:{}", step.getStepId());
			} catch (InterruptedException e) {
				throw new ProtocolExecutionException("Protocol was interrupted", e);
			} catch (ExecutionException e) {
				throw new ProtocolExecutionException("Protocol threw an exception whilst running", e);
			}
		}
		}finally{
			server.protocolFinished(this);
		}
	}

	/**
	 * The actual call method used by the executor. If all ProtocolSteps
	 * complete without an exception this Protocol has completed and we can
	 * return true.
	 */
	public Boolean call() throws ProtocolExecutionException {
		runProtocol();
		return true;
	}

	/**
	 * Processes an incoming message by looking at the _protocolStep and passing
	 * it on to the relevant step. If the received message is a flush message we
	 * ignore it, since it is just being used the flush the message queue.
	 * 
	 * TODO this needs to validate the incoming message - probably with a JSON
	 * schema
	 * 
	 * @param msg
	 *            message received
	 */
	public void processMessage(E msg) throws StorageException {
		if (msg.has(MESSAGES.FLUSH_MSG)) {
			logger.info("Flush message received, will ignore contents");
		} else {
			logger.info("Protocol Received: {}", msg);
			this.stepIndex.get(msg.getString(MESSAGES.STEP)).processMessage(msg);
		}

	}

	/**
	 * Gets a StorageObject from the underlying storage using the specified
	 * index. The storage prefix for this Protocol will be prepended before
	 * looking up the value.
	 * 
	 * @param objId
	 *            String id of the object to be retrieved
	 * @return StorageObject that is returned
	 */
	public StorageObject<E> getFromStorage(String objId) {
		return this.server.getFromStorage(this.getStoragePrefix() + "." + objId);
	}

	/**
	 * Returns a new empty StorageObject of the appropriate type
	 * 
	 * @return StorageObject of the appropriate type
	 */
	public StorageObject<E> getNewStorageObject() {
		return this.server.getNewStorageObject();
	}

}

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
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayer;
import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessageConstructor;
import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessageListener;
import au.edu.unimelb.cs.culnane.comms.exceptions.UnknownChannelIdException;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * The ProtocolServer runs protocols and provides access to the underlying
 * communication and storage layers. A protocol server can concurrently run
 * different protocols.
 * 
 * @author Chris Culnane
 *
 */
public class ProtocolServer<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements CommunicationLayerMessageListener<E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(ProtocolServer.class);
	/**
	 * ConcurrentHashMap, with a ConcurrentList for queuing incoming protocol
	 * messages
	 */
	private ConcurrentHashMap<String, LinkedBlockingDeque<E>> protocolMsgQueue = new ConcurrentHashMap<String, LinkedBlockingDeque<E>>();

	/**
	 * ConcurrentHashMap of protocols, indexed by protocol id
	 */
	private ConcurrentHashMap<String, Protocol<E>> protocols = new ConcurrentHashMap<String, Protocol<E>>();
	/**
	 * ConcurrentHashMap of currently running protocols and their respective
	 * future objects
	 */
	private ConcurrentHashMap<String, Future<Boolean>> runningProtocols = new ConcurrentHashMap<String, Future<Boolean>>();

	/**
	 * Communication layer to use for communication between peers
	 */
	private CommunicationLayer<E> comms;
	/**
	 * Protocol executor service to run the actual protocols on
	 */
	private ExecutorService protocolExecutor = Executors.newCachedThreadPool();

	/**
	 * Server id - normally Peer id
	 */
	private String id;

	/**
	 * Protocols storage to use for underlying storage
	 */
	private ProtocolStore<E> storage;

	/**
	 * Creats a new ProtocolServer with the specified id, comms and storage.
	 * 
	 * @param id
	 *            String id of peer and server
	 * @param comms
	 *            CommunicationLayer to use for intra-peer communication
	 * @param storage
	 *            ProtocolStorage to use for all storage requirements
	 */
	public ProtocolServer(String id, CommunicationLayer<E> comms, ProtocolStore<E> storage) {
		this.id = id;
		this.comms = comms;
		this.comms.addMessageListener(this);
		this.storage = storage;

	}

	/**
	 * Gets the message constructor for the underlying channel
	 * 
	 * @return CommunicationLayerMessageConstructor
	 */
	public CommunicationLayerMessageConstructor<E> getMessageConstructor() {
		return comms.getMsgConstructor();
	}

	/**
	 * Gets the number of defined peers
	 * 
	 * @return integer of the number of peers
	 */
	public int getPeerCount() {
		return this.comms.getChannelCount();
	}

	/**
	 * Gets a Set of strings containing the other peer ids
	 * 
	 * @return Set of Strings containing peer ids
	 */
	public Set<String> getPeerIds() {
		return this.comms.getChannelIds();
	}

	/**
	 * Gets the id of this server
	 * 
	 * @return String containing server id
	 */
	public String getId() {
		return this.id;

	}

	/**
	 * Submit a protocol to this server. This will not start the protocol, it
	 * will just register it with this server and set this server in the
	 * Protocol.
	 * 
	 * @param protocol
	 *            Protocol to be submitted
	 */
	public void submitProtocol(Protocol<E> protocol) {
		logger.info("Protocol: {} added to the server", protocol.getId());
		protocol.setProtocolServer(this);
		protocols.put(protocol.getId(), protocol);
	}

	/**
	 * Starts a protocol based on the protocol id. The protocol must have
	 * already been submitted prior to calling startProtocol. If the protocol is
	 * not found no action is taken.
	 * 
	 * @param protocolId
	 * @throws ProtocolExecutionException
	 */
	public void startProtocol(String protocolId) throws ProtocolExecutionException {
		Protocol<E> protocol = protocols.get(protocolId);

		logger.info("Start protocol:{} requested", protocolId);
		if (protocol != null) {
			FutureTask<Boolean> placeholder = new FutureTask<Boolean>(protocol);
			if (runningProtocols.putIfAbsent(protocol.getId(), placeholder) == null) {
				logger.info("Protocol:{} started", protocolId);
				Future<Boolean> result = protocolExecutor.submit(protocol);
				runningProtocols.replace(protocol.getId(), result);
			} else {
				throw new ProtocolExecutionException("Cannot execute the same protocol concurrently");
			}

		} else {
			logger.warn("Protocol:{} not found", protocolId);
		}
	}

	/**
	 * Gets a Protocol Future object by the protocolid. returns null if not
	 * found
	 * 
	 * @param protocolId
	 *            String protocol id
	 * @return Future<Boolean> if running protocol found, or null
	 */
	public Future<Boolean> getProtocolFuture(String protocolId) {
		return this.runningProtocols.get(protocolId);
	}

	/**
	 * Broadcasts the message to all peers 
	 * 
	 * @param msg
	 *            message to send
	 * @throws IOException
	 *             if an error occurs during sending
	 */
	public void sendBroadcast(E msg) throws IOException {
		msg.set(Protocol.MESSAGES.SENDER, this.id);
		comms.broadcast(msg);
	}

	/**
	 * Sends the message to a specified peer.
	 * 
	 * @param msg
	 *            message to be sent
	 * @param sendTo
	 *            String id of recipient
	 * @throws UnknownChannelIdException
	 *             if no corresponding channel for the sendTo is found
	 * @throws IOException
	 *             if an error occurs during sending
	 */
	public void sendMessage(E msg, String sendTo) throws UnknownChannelIdException, IOException {
		msg.set(Protocol.MESSAGES.SENDER, this.id);
		comms.send(msg, sendTo);
	}

	@Override
	public synchronized void messageReceived(E msg) {
		try {
			// TODO would like to remove synchronized from the method
			String protocolId = msg.getString(Protocol.MESSAGES.PROTOCOL_ID);
			Protocol<E> prot = protocols.get(protocolId);
			if (prot == null && !msg.has(Protocol.MESSAGES.FLUSH_MSG)) {
				LinkedBlockingDeque<E> pMsgQueue = new LinkedBlockingDeque<E>();
				pMsgQueue.add(msg);
				LinkedBlockingDeque<E> currentQueue = this.protocolMsgQueue.putIfAbsent(protocolId, pMsgQueue);
				if (currentQueue != null) {
					currentQueue.add(msg);
				}
			} else {
				LinkedBlockingDeque<E> currentQueue = this.protocolMsgQueue.remove(protocolId);
				if (currentQueue == null) {
					protocols.get(protocolId).processMessage(msg);
				} else {
					Iterator<E> itr = currentQueue.iterator();
					while (itr.hasNext()) {

						protocols.get(protocolId).processMessage(itr.next());
					}
					protocols.get(protocolId).processMessage(msg);
				}
			}
		} catch (StorageException e) {
			logger.error("Exception processing message: {}", msg);
		}

	}

	/**
	 * Adds the to the transcript
	 * 
	 * @param msg
	 *            to be added to the transcript
	 */
	public void addToTranscript(E msg) {
		this.storage.addToTranscript(msg);
	}

	/**
	 * Stores a StorageObject in the underlying storage
	 * 
	 * @param id
	 *            String key id of the object
	 * @param msg
	 *            StorageObject to be stored
	 */
	public void addToStorage(String id, StorageObject<E> msg) {
		this.storage.store(id, msg);
	}

	/**
	 * Gets a StorageObject from the underlying storage
	 * 
	 * @param id
	 *            String key id of object to retrieve
	 * @return StorageObject retrieved
	 */
	public StorageObject<E> getFromStorage(String id) {

		return this.storage.get(id);
	}

	/**
	 * Returns a new StorageObject of the appropriate type
	 * 
	 * @return StorageObject of the appropriate type
	 */
	public StorageObject<E> getNewStorageObject() {
		return this.storage.getNewStorageObject();
	}

	public void protocolFinished(Protocol<E> protocol) {
		this.runningProtocols.remove(protocol.getId());

		
	}
	public void removeProtocol(Protocol<E> protocol){
		protocols.remove(protocol.getId());
	}

}

/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.pedersencommitment;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.ZpStarSafePrimeGroupRFC3526;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * Protocol Step to create a Pedersen Commitment and share it with other peers
 * 
 * @author Chris Culnane
 *
 */
public class PdCCreateCommit<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(PdCCreateCommit.class);

	/**
	 * Incoming message queue
	 */
	private LinkedBlockingDeque<E> incomingMessages = new LinkedBlockingDeque<E>();

	/**
	 * Static string with STEP_ID
	 */
	public static final String STEP_ID = "PdC_CreateCommit";

	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * Static class with message keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class MESSAGES {
		public static final String COMMIT = "commit";
		public static final String COMMITID = "commitID";
	}

	/**
	 * Static class with storage keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class STORAGE {
		public static final String COMMITS = "commits";
		public static final String COMMIT = "commit";
	}

	/**
	 * String containing id for this commit
	 */
	private String commitId;

	/**
	 * BigInteger containing the message, m, to commit to
	 */
	private BigInteger m;

	/**
	 * boolean that determins whether to retrieve data from storage or used a
	 * passed in value
	 */
	private boolean useDataId = false;

	/**
	 * Construct a new protocol step to create a Perdersen Commitment on the
	 * specified value
	 * 
	 * @param commitID
	 *            String commit id
	 * @param m
	 *            BigInteger value to commit to
	 */
	public PdCCreateCommit(String commitID, BigInteger m) {
		this.commitId = commitID;
		this.m = m;
	}

	/**
	 * String dataId containing the storage object to retrieve data from
	 */
	private String dataId;

	/**
	 * String fieldId containing the field from the dataId storage object to
	 * commit to
	 */
	private String fieldId;

	/**
	 * Construct a new protocol step to create a pedersen commit on the data in
	 * the specified storage object
	 * 
	 * @param commitID
	 *            String commit it
	 * @param dataId
	 *            String id of StorageObject to retrieve
	 * @param fieldId
	 *            String id of field in dataId storage object, whose value we
	 *            will commit to
	 */
	public PdCCreateCommit(String commitID, String dataId, String fieldId) {
		this.useDataId = true;
		this.commitId = commitID;
		this.dataId = dataId;
		this.fieldId = fieldId;
	}

	/**
	 * Gets the commit data, either from the data passed in, of if appropriate,
	 * from a StorageObject
	 * 
	 * @return BigInteger of data to commit to
	 * @throws StorageException
	 */
	private BigInteger getCommitData() throws StorageException {
		if (!useDataId) {
			return this.m;
		} else {
			return this.protocol.getFromStorage(dataId).get().getBigInteger(fieldId);
		}
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", getStepId());
		String storageId = PdCInit.STEP_ID;
		if (this.protocol.getFromStorage(storageId) == null) {
			throw new ProtocolExecutionException("This step should not be run before initialisation of the group");
		}
		StorageObject<E> storage = this.protocol.getFromStorage(storageId);
		logger.info("PedersenCommitment {} is initialised. Will reload values", storageId);

		E initdata = storage.get();
		ZpStarSafePrimeGroupRFC3526 group = new ZpStarSafePrimeGroupRFC3526(
				initdata.getStorageObject(PdCInit.STORAGE.GROUP));
		BigInteger v = new BigInteger(initdata.getString(PdCInit.STORAGE.V), 16);

		PedersenCommitment pedCommitment = new PedersenCommitment(group, v);
		PedersenCommit commit = pedCommitment.commit(getCommitData());

		StorageObject<E> mydata = this.protocol.getNewStorageObject();

		mydata.set(STORAGE.COMMIT, commit);
		logger.info("Mydata:{}", commit);
		StorageObject<E> broadcastCommit = this.protocol.getNewStorageObject();
		broadcastCommit.set(MESSAGES.COMMITID, this.commitId);
		broadcastCommit.set(MESSAGES.COMMIT, commit.getCommitString());
		broadcastCommit.set(Protocol.MESSAGES.STEP, getStepId());

		logger.info("Generated my data, about to broadcast: {}", broadcastCommit);
		// Broadcast message
		protocol.sendBroadcast(broadcastCommit.get());
		boolean receivedAll = false;
		Map<String, String> commitsReceived = new HashMap<String, String>();
		TreeSet<String> peerIds = new TreeSet<String>();
		peerIds.addAll(this.protocol.getPeerIds());
		while (!receivedAll) {
			try {
				E msg = this.incomingMessages.take();
				logger.info("Processing incoming message: {}", msg);
				String sender = msg.getString(Protocol.MESSAGES.SENDER);
				String receivedCommit = msg.getString(MESSAGES.COMMIT);
				if (!commitsReceived.containsKey(sender)) {
					commitsReceived.put(sender, receivedCommit);
					if (!peerIds.remove(sender)) {
						throw new ProtocolExecutionException("Received message from unknown peer");
					}
					if (peerIds.isEmpty()) {
						receivedAll = true;
						break;
					}
				} else {
					throw new ProtocolExecutionException("Received duplicate message from peer:" + sender);
				}
			} catch (StorageException e) {
				throw new ProtocolExecutionException("Received message was not correctly formatted", e);
			}

		}
		logger.info("Finished processing messages");
		mydata.setStringMap(STORAGE.COMMITS, commitsReceived);
		// Store my data
		protocol.addToStorage(getStepId(), mydata);
		return true;
	}

	@Override
	public String getStepId() {
		return STEP_ID + ":" + this.commitId;// We do this to distinguish
												// between concurrent runs of
												// this protocol step
	}

	@Override
	public void processMessage(E msg) {
		this.protocol.addToTranscript(msg);
		incomingMessages.add(msg);

	}

	@Override
	public void setProtocol(Protocol<E> protocol) {
		this.protocol = protocol;

	}

}

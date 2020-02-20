/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.cointoss;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * Coin Toss Open Commit sends an opening to our commit and then waits for the
 * openings from all other peers to check their commits.
 * 
 * @author Chris Culnane
 *
 */
public class CTOpenCommit<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(CTOpenCommit.class);
	/**
	 * Concurrent Queue to buffer incoming messages
	 */
	private LinkedBlockingDeque<E> incomingMessages = new LinkedBlockingDeque<E>();

	/**
	 * String containing STEP_ID
	 */
	public static final String STEP_ID = "CT_OpenAndCheckCommits";

	/**
	 * Protocol this step is running in
	 */
	private Protocol<E> protocol;

	/**
	 * Static class holding String keys used for storage
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class STORAGE {
		public static final String JOINT_RANDOM_VALUE_STATUS_NEW = "new";
		public static final String JOINT_RANDOM_VALUE_STATUS_USED = "used";
		public static final String JOINT_RANDOM_VALUE_STATUS = "jointRandomnessStatus";
		public static final String JOINT_RANDOM_VALUE = "jointRandomness";
		public static final String RECEIVED_OPENINGS = "receivedOpenings";
	}

	/**
	 * Static class holding String keys used for messages
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class MESSAGES {
		public static final String RANDOM_VALUE = "randomValue";

	}

	/**
	 * Constructs a new Coin Toss Open Commit step
	 */
	public CTOpenCommit() {
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", STEP_ID);

		StorageObject<E> commitData = this.protocol.getFromStorage(CTGenRandAndCommit.STEP_ID).get();

		StorageObject<E> broadcastOpen = this.protocol.getNewStorageObject();
		broadcastOpen.set(MESSAGES.RANDOM_VALUE, commitData.getString(CTGenRandAndCommit.STORAGE.MY_RANDOM_VALUE_KEY));
		broadcastOpen.set(Protocol.MESSAGES.STEP, STEP_ID);
		this.protocol.sendBroadcast(broadcastOpen.get());
		logger.info("Broadcasting opening: {}", broadcastOpen);
		boolean receivedAll = false;
		Map<String, String> openReceived = new HashMap<String, String>();
		TreeSet<String> peerIds = new TreeSet<String>();
		peerIds.addAll(this.protocol.getPeerIds());
		MessageDigest hash = MessageDigest.getInstance("SHA-256");
		Map<String, String> commitsReceived = commitData.getStringMap(CTGenRandAndCommit.STORAGE.RECEIVED_COMMITS_KEY);

		while (!receivedAll) {
			try {
				E msg = this.incomingMessages.take();
				logger.info("Processing incoming message: {}", msg);
				String sender = msg.getString(Protocol.MESSAGES.SENDER);
				String randVal = msg.getString(MESSAGES.RANDOM_VALUE);
				hash.update((new BigInteger(randVal, 16)).toByteArray());
				hash.update(sender.getBytes());
				String checkHash = Base64.encodeBase64String(hash.digest());
				hash.reset();
				logger.debug("ReceivedCommits:" + commitsReceived);
				if (commitsReceived.containsKey(sender)) {
					String receivedCommit = commitsReceived.get(sender);
					if (!checkHash.equals(receivedCommit)) {
						throw new ProtocolExecutionException("Commits did not open successfully");
					}
					if (!peerIds.remove(sender)) {
						throw new ProtocolExecutionException("Received message from unknown peer");
					}
					openReceived.put(sender, randVal);
					if (peerIds.isEmpty()) {
						receivedAll = true;
						break;
					}

				} else {
					throw new ProtocolExecutionException("Received opening without commit:" + sender);
				}
			} catch (StorageException e) {
				throw new ProtocolExecutionException("Received message was not correctly formatted", e);
			}

		}
		logger.info("Finished processing openings");
		StorageObject<E> mydata = this.protocol.getNewStorageObject();
		mydata.setStringMap(STORAGE.RECEIVED_OPENINGS, openReceived);
		logger.info("Received:{}", openReceived.keySet());
		TreeSet<String> peerOpeningIds = new TreeSet<String>(openReceived.keySet());
		peerOpeningIds.add(this.protocol.getServerId());
		logger.debug("ProtocolServerId:{}", this.protocol.getServerId());
		Iterator<String> itr = peerOpeningIds.iterator();
		hash.reset();
		while (itr.hasNext()) {
			String currentId = itr.next();
			logger.info("Adding value: {}", currentId);
			if (currentId.equals(this.protocol.getServerId())) {
				logger.info("randomness value: {}", commitData.getString(CTGenRandAndCommit.STORAGE.MY_RANDOM_VALUE_KEY));
				hash.update(new BigInteger(commitData.getString(CTGenRandAndCommit.STORAGE.MY_RANDOM_VALUE_KEY), 16)
						.toByteArray());
			} else {
				hash.update(new BigInteger(openReceived.get(currentId), 16).toByteArray());
				logger.info("randomness value: {}", openReceived.get(currentId), 16);
			}
		}
		mydata.set(STORAGE.JOINT_RANDOM_VALUE, Base64.encodeBase64String(hash.digest()));
		mydata.set(STORAGE.JOINT_RANDOM_VALUE_STATUS, STORAGE.JOINT_RANDOM_VALUE_STATUS_NEW);
		// Store my data
		protocol.addToStorage(STEP_ID, mydata);
		logger.info("Combined randomness: {}", mydata.getString(STORAGE.JOINT_RANDOM_VALUE));

		return true;
	}

	@Override
	public String getStepId() {
		return STEP_ID;
	}

	@Override
	public void processMessage(E msg) {
		logger.info("Received opening message: {}", msg);
		this.protocol.addToTranscript(msg);
		incomingMessages.add(msg);

	}

	@Override
	public void setProtocol(Protocol<E> protocol) {
		this.protocol = protocol;

	}

}

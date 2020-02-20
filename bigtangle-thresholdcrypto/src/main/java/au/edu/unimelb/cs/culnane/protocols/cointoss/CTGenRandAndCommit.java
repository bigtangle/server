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
import java.security.SecureRandom;
import java.util.HashMap;
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
 * Coin Toss Random Commit protocol Step. This generates our random values and
 * commits to it. It waits for equivalent commits to come from all other peers
 * before completing.
 * 
 * @author Chris Culnane
 *
 */
public class CTGenRandAndCommit<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(CTGenRandAndCommit.class);
	/**
	 * Concurrent queue to buffer incoming messages
	 */
	private LinkedBlockingDeque<E> incomingMessages = new LinkedBlockingDeque<E>();

	/**
	 * String containing STEP_ID
	 */
	public static final String STEP_ID = "CT_GenRandCommit";

	/**
	 * Protocol this step is being run within
	 */
	private Protocol<E> protocol;

	/**
	 * Static class to hold keys used for storage objects
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static class STORAGE {
		public static final String MY_RANDOM_VALUE_KEY = "myRandomValue";
		public static final String MY_COMMIT_KEY = "myCommit";
		public static final String RECEIVED_COMMITS_KEY = "receivedCommits";
	}

	/**
	 * Static class to hold keys used for message objects
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static class MESSAGES {
		public static final String COMMIT = "commit";

	}

	/**
	 * Integer holding the size of the randomness to generate
	 */
	private int randSize;

	/**
	 * Construct a new CoinToss Generate Randomness and Commit step
	 * 
	 * @param randSize
	 *            int containing size of randomness to generate
	 */
	public CTGenRandAndCommit(int randSize) {
		this.randSize = randSize;
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", STEP_ID);

		// Generate Randomness
		SecureRandom rand = new SecureRandom();
		BigInteger randVal = new BigInteger(randSize, rand);
		StorageObject<E> mydata = protocol.getNewStorageObject();
		mydata.set(STORAGE.MY_RANDOM_VALUE_KEY, randVal.toString(16));

		// Prepare commit
		MessageDigest hash = MessageDigest.getInstance("SHA-256");
		hash.update(randVal.toByteArray());
		hash.update(this.protocol.getServerId().getBytes());
		String commitString = Base64.encodeBase64String(hash.digest());
		mydata.set(STORAGE.MY_COMMIT_KEY, commitString);

		// Prepare outgoing broadcast
		StorageObject<E> broadcastCommit = protocol.getNewStorageObject();
		broadcastCommit.set(MESSAGES.COMMIT, commitString);
		broadcastCommit.get().set(Protocol.MESSAGES.STEP, STEP_ID);
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
				String commit = msg.getString(MESSAGES.COMMIT);
				if (!commitsReceived.containsKey(sender)) {
					commitsReceived.put(sender, commit);
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
		mydata.setStringMap(STORAGE.RECEIVED_COMMITS_KEY, commitsReceived);
		// Store my data
		protocol.addToStorage(STEP_ID, mydata);
		return true;
	}

	@Override
	public String getStepId() {
		return STEP_ID;
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

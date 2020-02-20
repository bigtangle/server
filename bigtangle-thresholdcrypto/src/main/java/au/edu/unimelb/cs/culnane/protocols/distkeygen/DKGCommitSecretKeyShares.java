/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.distkeygen;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.Group;
import au.edu.unimelb.cs.culnane.crypto.secretsharing.SecretShare;
import au.edu.unimelb.cs.culnane.crypto.secretsharing.ShamirSecretSharing;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * This should either initialise the pedersen commitment, or if it has been
 * pre-constructed, reload the values
 * 
 * @author cculnane
 *
 */
public class DKGCommitSecretKeyShares<E extends CommunicationLayerMessage<?> & StorageObject<E>> implements ProtocolStep<Boolean, E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(DKGCommitSecretKeyShares.class);

	/**
	 * Concurrent queue to buffer incoming messages
	 */
	private LinkedBlockingDeque<E> incomingMessages = new LinkedBlockingDeque<E>();

	/**
	 * String STEP_ID
	 */
	public static final String STEP_ID = "DKG_CommitSecretKeyShares";

	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * String key id for the keys generated in this run
	 */
	private String keyId;

	/**
	 * Static class containing String keys for messages
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class MESSAGES {
		public static final String G_TO_COEFS = "g_to_coefs";
	}

	/**
	 * Static class containing String keys for storage
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class STORAGE {
		public static final String G_TO_COEFS = "g_to_coefs";
		public static final String SHARES = "shares";
		public static final String COMMITS_TO_G_TO_COEFS = "commits";
		// public static final String PUBLIC_KEY = "h";
	}

	/**
	 * Underlying Group used during key generation
	 */
	private Group group;
	/**
	 * Integer holding the threshold
	 */
	private int threshold;

	/**
	 * Constructs a new Distributed Key Generation Commit Secret Key Share step.
	 * This will generate the secret shares and then commit to them.
	 * 
	 * @param group
	 *            Group that the keys were generated in
	 * @param keyId
	 *            String id for the generated key
	 * @param threshold
	 *            integer threshold of sharing
	 */
	public DKGCommitSecretKeyShares(Group group, String keyId, int threshold) {
		this.group = group;
		this.keyId = keyId;
		this.threshold = threshold;
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", getStepId());

		StorageObject<E> gendata = protocol.getFromStorage(DKGGenerateKeys.STEP_ID + ":" + keyId).get();
		BigInteger x = gendata.getBigInteger(DKGGenerateKeys.STORAGE.SECRET_KEY);

		int peerCount = this.protocol.getPeerIds().size() + 1;
		ShamirSecretSharing secretSharing = new ShamirSecretSharing(peerCount, threshold, group, new SecureRandom());
		BigInteger[] coefs = secretSharing.generateCoefficients(x);
		SecretShare[] shares = secretSharing.generateShares(coefs);

		BigInteger[] available = new BigInteger[shares.length];
		for (int i = 0; i < shares.length; i++) {
			available[i] = shares[i].getShare();
		}
		if (x.equals(secretSharing.interpolate(available))) {
			logger.info("Verified shares are working");
		} else {
			logger.error("SHARES NOT WORKING");
			throw new ProtocolExecutionException("Attempt to verify secret sharing failed");
		}
		BigInteger[] g_to_coefs = new BigInteger[coefs.length];

		List<BigInteger> g_to_coefs_List = new ArrayList<BigInteger>();
		//JSONArray g_to_coefs_JSON = new JSONArray();
		for (int i = 0; i < g_to_coefs.length; i++) {
			g_to_coefs[i] = group.get_g().modPow(coefs[i], group.get_p());
			g_to_coefs_List.add(g_to_coefs[i]);
		}
		StorageObject<E> mydata = this.protocol.getNewStorageObject();
		mydata.setBigIntList(STORAGE.G_TO_COEFS, g_to_coefs_List);
		List<StorageObject<E>> sharesList = new ArrayList<StorageObject<E>>();
		for (SecretShare share : shares) {
			StorageObject<E> shareStore = this.protocol.getNewStorageObject();
			share.storeInStorageObject(shareStore);
			sharesList.add(shareStore);
		}
		mydata.set(STORAGE.SHARES, sharesList);

		StorageObject<E> broadcast = this.protocol.getNewStorageObject();
		broadcast.setBigIntList(MESSAGES.G_TO_COEFS, g_to_coefs_List);
		broadcast.get().set(Protocol.MESSAGES.STEP, getStepId());

		this.protocol.sendBroadcast(broadcast.get());
		boolean receivedAll = false;

		TreeSet<String> peerIds = new TreeSet<String>();
		peerIds.addAll(this.protocol.getPeerIds());
		
		StorageObject<E> commitsReceived = this.protocol.getNewStorageObject();
		//Map<String, JSONArray> commitsReceived = new HashMap<String, JSONArray>();
		BigInteger checkDegree = BigInteger.ONE;
		while (!receivedAll) {
			try {
				E msg = this.incomingMessages.take();
				logger.info("Processing incoming message: {}", msg);
				String sender = msg.getString(Protocol.MESSAGES.SENDER);
				List<BigInteger> receivedCommit = msg.getBigIntList(MESSAGES.G_TO_COEFS);
				
				if (!commitsReceived.has(sender)) {
					commitsReceived.setBigIntList(sender, receivedCommit);
					checkDegree = checkDegree
							.multiply(receivedCommit.get(receivedCommit.size() - 1))
							.mod(group.get_p());
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
		if (checkDegree.equals(BigInteger.ONE)) {
			throw new ProtocolExecutionException("Polynomial degree test failed");
		}
		logger.info("DegreeCheck:{}", checkDegree);
		
		mydata.set(STORAGE.COMMITS_TO_G_TO_COEFS, commitsReceived);
		// Store my data
		protocol.addToStorage(getStepId(), mydata);
		return true;
	}

	@Override
	public String getStepId() {
		return STEP_ID + ":" + this.keyId;
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

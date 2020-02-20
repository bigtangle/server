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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.Group;
import au.edu.unimelb.cs.culnane.crypto.secretsharing.SecretShare;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * Shares the constructed secret key and receives shares from other servers.
 * 
 * @author Chris Culnane
 *
 */
public class DKGShareSecretKey<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(DKGShareSecretKey.class);

	/**
	 * Incoming Message Queue
	 */
	private LinkedBlockingDeque<E> incomingMessages = new LinkedBlockingDeque<E>();

	/**
	 * Static String STEP_ID
	 */
	public static final String STEP_ID = "DKG_ShareSecretKey";

	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * String id of the key
	 */
	private String keyId;

	/**
	 * Static class containing message keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class MESSAGES {
		public static final String SHARE = "share";
	}

	/**
	 * Static class containing storage keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class STORAGE {
		public static final String SHARES_RECEIVED = "sharesReceived";
		public static final String COMBINED_SECRET_KEY = "combinedKey";
		public static final String PUBLIC_VERIFICATION = "publicVerification";

		// public static final String PUBLIC_KEY = "h";
	}

	/**
	 * Group to use for key operations
	 */
	private Group group;

	/**
	 * int threshold of shares
	 */
	private int threshold;

	/**
	 * StorageObject to save keydata to
	 */
	private StorageObject<?> keyData;

	/**
	 * Construct a new DKGShareSecretKey protocol step, to share and receive
	 * secret key shares
	 * 
	 * @param group
	 *            Group the key is generated from
	 * @param keyId
	 *            String id of the key to share
	 * @param threshold
	 *            int threshold of shares
	 * @param exportKey
	 *            StorageObject to store key data in
	 */
	public DKGShareSecretKey(Group group, String keyId, int threshold, StorageObject<?> exportKey) {
		this.group = group;
		this.keyId = keyId;
		this.threshold = threshold;
		this.keyData = exportKey;
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", getStepId());

		StorageObject<E> commitdata = protocol.getFromStorage(DKGCommitSecretKeyShares.STEP_ID + ":" + keyId).get();

		List<StorageObject<E>> shares = commitdata.getList(DKGCommitSecretKeyShares.STORAGE.SHARES);
		StorageObject<E> commits = commitdata.getStorageObject(DKGCommitSecretKeyShares.STORAGE.COMMITS_TO_G_TO_COEFS);

		StorageObject<E> mydata = this.protocol.getNewStorageObject();
		Map<String, StorageObject<E>> sharesReceived = new HashMap<String, StorageObject<E>>();

		// Construct PeerIds, including my own
		TreeSet<String> peers = new TreeSet<String>();
		peers.addAll(this.protocol.getPeerIds());
		peers.add(this.protocol.getServerId());
		SecretShare myShare = null;
		logger.info("shares:{}", shares);
		Iterator<String> itr = peers.iterator();
		int peerCount = 0;
		while (itr.hasNext()) {
			SecretShare share = new SecretShare(shares.get(peerCount));
			String peerId = itr.next();
			if (peerId.equals(this.protocol.getServerId())) {
				myShare = share;
				// sharesReceived.put(peerId, share);
				logger.info("myshare:{},{}", peerCount, share.getShare().toString(16));
			} else {
				StorageObject<E> shareMessage = this.protocol.getNewStorageObject();
				shareMessage.set(MESSAGES.SHARE, shares.get(peerCount));
				shareMessage.set(Protocol.MESSAGES.STEP, this.getStepId());
				// logger.info("receivedshare:{}",
				// share.getShare().toString(16));
				protocol.sendMessage(shareMessage.get(), peerId);
			}
			peerCount++;

		}
		boolean receivedAll = false;

		TreeSet<String> peerIds = new TreeSet<String>();
		peerIds.addAll(this.protocol.getPeerIds());

		List<String> peerIndex = new ArrayList<String>();
		peerIndex.addAll(this.protocol.getPeerIds());
		peerIndex.add(this.protocol.getServerId());
		Collections.sort(peerIndex);

		BigInteger[] combinedCheckValues = new BigInteger[peerIndex.size()];
		for (int i = 0; i < combinedCheckValues.length; i++) {
			combinedCheckValues[i] = BigInteger.ONE;
		}

		BigInteger combinedSecretKey = BigInteger.ZERO;
		while (!receivedAll) {
			try {
				E msg = this.incomingMessages.take();
				logger.info("Processing incoming message: {}", msg);
				String sender = msg.getString(Protocol.MESSAGES.SENDER);
				SecretShare receivedShare = new SecretShare(msg.getStorageObject(MESSAGES.SHARE));
				if (!sharesReceived.containsKey(sender)) {
					sharesReceived.put(sender, msg.getStorageObject(MESSAGES.SHARE));
					List<BigInteger> senderCommit = commits.getBigIntList(sender);
					BigInteger shareCheck = BigInteger.ZERO;
					for (int i = 0; i < peerIndex.size(); i++) {
						BigInteger index = BigInteger.valueOf(i + 1);
						BigInteger checkValue = BigInteger.ONE;
						for (int j = 0; j < threshold; j++) {
							BigInteger commitValue = senderCommit.get(j);
							checkValue = checkValue.multiply(commitValue.modPow(index.pow(j), group.get_p()))
									.mod(group.get_p());
						}
						combinedCheckValues[i] = combinedCheckValues[i].multiply(checkValue).mod(group.get_p());
						if (i == (peerIndex.indexOf(protocol.getServerId()))) {
							shareCheck = checkValue;
						}
					}
					combinedSecretKey = combinedSecretKey.add(receivedShare.getShare()).mod(group.get_q());
					if (!shareCheck.equals(group.get_g().modPow(receivedShare.getShare(), group.get_p()))) {
						throw new ProtocolExecutionException("Share commit validation failed");
					}
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

		// Get my commits
		List<BigInteger> mycommits = commitdata.getBigIntList(DKGCommitSecretKeyShares.STORAGE.G_TO_COEFS);

		for (int i = 0; i < peerIndex.size(); i++) {
			BigInteger index = BigInteger.valueOf(i + 1);
			BigInteger checkValue = BigInteger.ONE;
			for (int j = 0; j < threshold; j++) {
				BigInteger commitValue = mycommits.get(j);
				checkValue = checkValue.multiply(commitValue.modPow(index.pow(j), group.get_p())).mod(group.get_p());
			}
			combinedCheckValues[i] = combinedCheckValues[i].multiply(checkValue).mod(group.get_p());
		}

		List<BigInteger> publicVerification = new ArrayList<BigInteger>();
		for (BigInteger v : combinedCheckValues) {
			publicVerification.add(v);
		}

		combinedSecretKey = combinedSecretKey.add(myShare.getShare()).mod(group.get_q());
		mydata.set(STORAGE.SHARES_RECEIVED, sharesReceived);
		mydata.set(STORAGE.COMBINED_SECRET_KEY, combinedSecretKey);
		mydata.setBigIntList(STORAGE.PUBLIC_VERIFICATION, publicVerification);
		// Export KeyData
		keyData.set("x", combinedSecretKey);
		keyData.setBigIntList("verify", publicVerification);
		
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

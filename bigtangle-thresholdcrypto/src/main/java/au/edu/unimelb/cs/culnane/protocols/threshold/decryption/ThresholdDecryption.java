/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.threshold.decryption;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.Group;
import au.edu.unimelb.cs.culnane.crypto.elgamal.ElGamalCipher;
import au.edu.unimelb.cs.culnane.crypto.elgamal.ThresholdElGamalKeyPair;
import au.edu.unimelb.cs.culnane.crypto.secretsharing.ShamirSecretSharing;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * ThresholdDecryption protocol that prepares a threshold decryption share and
 * proof of partial decryption before sharing it with other peers. Waits for a
 * threshold of valid shares to be received before combining the final
 * decryption.
 * 
 * @author Chris Culnane
 *
 */
public class ThresholdDecryption<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {

	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(ThresholdDecryption.class);

	/**
	 * Queue of incoming messages
	 */
	private LinkedBlockingDeque<E> incomingMessages = new LinkedBlockingDeque<E>();

	/**
	 * Static string containing STEP_ID
	 */
	public static final String STEP_ID = "ThresholdDecryption";

	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * Unique id of this decryption run
	 */
	private String uuid;

	/**
	 * Static class containing message keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class MESSAGES {
		public static final String PARTIAL_DECRYPTION = "partial_dec";
	}

	/**
	 * Static class containing storage keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class STORAGE {
		// public static final String PUBLIC_KEY = "h";
	}

	/**
	 * ThresholdElGamalKeyPair containing key data
	 */
	private ThresholdElGamalKeyPair keyPair;

	/**
	 * Group to perform operations on
	 */
	private Group group;
	/**
	 * Int threshold of key
	 */
	private int threshold;

	/**
	 * ElGamalCipher containing the cipher to be decrypted
	 */
	private ElGamalCipher cipher;

	/**
	 * Storage Object for cipher
	 */
	private StorageObject<?> cipherStore;

	/**
	 * Constructs a new ThresholdDecryption step
	 * 
	 * @param group
	 *            Group to perform operations on
	 * @param cipher
	 *            StorageObject containing cipher to decrypt
	 * @param keyData
	 *            StorageObject containing key data
	 * @param uuid
	 *            String uuid of this decryption run
	 * @param threshold
	 *            int threshold of the key
	 * @throws StorageException
	 *             - if cipher does not contain appropriate fields
	 */
	public ThresholdDecryption(Group group, StorageObject<?> cipherStore, StorageObject<?> keyData, String uuid,
			int threshold) throws StorageException {
		this.group = group;
		this.uuid = uuid;
		this.threshold = threshold;
		this.cipherStore = cipherStore;
		this.keyPair = new ThresholdElGamalKeyPair(keyData);
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", getStepId());
		this.cipher = new ElGamalCipher(this.cipherStore);
		int peerCount = this.protocol.getPeerIds().size() + 1;
		ShamirSecretSharing secretSharing = new ShamirSecretSharing(peerCount, threshold, group, new SecureRandom());

		BigInteger s = cipher.getC1().modPow(keyPair.getPrivateKey(), group.get_p());

		List<BigInteger> verify = keyPair.getVerifyData();
		EqualityDiscreteLogProof proof = EqualityDiscreteLogProof.constructProof(group, cipher.getC1(), group.get_g(),
				s, group.get_g().modPow(keyPair.getPrivateKey(), group.get_p()), keyPair.getPrivateKey());

		StorageObject<E> partialDec = this.protocol.getNewStorageObject();
		partialDec.set("s", s);
		partialDec.set("proof", proof);

		StorageObject<E> broadcast = this.protocol.getNewStorageObject();
		broadcast.set(MESSAGES.PARTIAL_DECRYPTION, partialDec);
		broadcast.set(Protocol.MESSAGES.STEP, getStepId());
		this.protocol.sendBroadcast(broadcast.get());

		boolean receivedThreshold = false;

		TreeSet<String> peerIds = new TreeSet<String>();
		peerIds.addAll(this.protocol.getPeerIds());
		Map<String, BigInteger> partialsReceived = new HashMap<String, BigInteger>();

		ArrayList<String> peerIndex = new ArrayList<String>();
		peerIndex.addAll(this.protocol.getPeerIds());
		peerIndex.add(this.protocol.getServerId());
		Collections.sort(peerIndex);
		BigInteger[] available = new BigInteger[peerIndex.size()];
		boolean[] haveShare = new boolean[peerIndex.size()];
		haveShare[peerIndex.indexOf(this.protocol.getServerId())] = true;
		available[peerIndex.indexOf(this.protocol.getServerId())] = s;
		int partialCount = 1;
		while (!receivedThreshold) {
			try {
				E msg = this.incomingMessages.take();
				logger.info("Processing incoming message: {}", msg);
				String sender = msg.getString(Protocol.MESSAGES.SENDER);
				StorageObject<E> receivedPartialObj = msg.getStorageObject(MESSAGES.PARTIAL_DECRYPTION);
				BigInteger receivedPartial = receivedPartialObj.getBigInteger("s");
				EqualityDiscreteLogProof receivedProof = new EqualityDiscreteLogProof(
						receivedPartialObj.getStorageObject("proof"), cipher.getC1(), group.get_g(), receivedPartial,
						verify.get(peerIndex.indexOf(sender)));
				if (!partialsReceived.containsKey(sender)) {
					if (!EqualityDiscreteLogProof.verify(group, receivedProof)) {
						logger.error("Proof of partial decryption failed for: {}", sender);
						continue;
					} else {
						logger.info("Proof of partial decryption Verified");
					}
					partialsReceived.put(sender, receivedPartial);
					partialCount++;
					available[peerIndex.indexOf(sender)] = receivedPartial;
					haveShare[peerIndex.indexOf(sender)] = true;
					if (partialCount >= threshold) {
						logger.info("Shares:{}", Arrays.toString(haveShare));
						BigInteger[] weights = secretSharing.computeLagrangeWeights(haveShare);
						logger.info("weights:{}", Arrays.toString(weights));
						BigInteger combined = BigInteger.ONE;
						for (int i = 0; i < peerIndex.size(); i++) {
							if (haveShare[i]) {
								BigInteger temp = available[i].modPow(weights[i], group.get_p());
								combined = combined.multiply(temp).mod(group.get_p());
							}
						}
						BigInteger decrypted = cipher.getC2().multiply(combined.modInverse(group.get_p()))
								.mod(group.get_p());
						logger.info("Decryption:{}", decrypted);
						StorageObject<E> decStore = this.protocol.getNewStorageObject();
						decStore.set("plaintext", decrypted.toString());
						this.protocol.addToStorage("decryption", decStore);
						receivedThreshold = true;
						break;
					}
					if (!peerIds.remove(sender)) {
						throw new ProtocolExecutionException("Received message from unknown peer");
					}
					if (peerIds.isEmpty()) {
						break;
					}
				} else {
					throw new ProtocolExecutionException("Received duplicate message from peer:" + sender);
				}
			} catch (StorageException e) {
				throw new ProtocolExecutionException("Received message was not correctly formatted", e);
			}
		}
		if (!receivedThreshold) {
			logger.warn("Did not receive a threshold of valid partial decryptions");
		}
		logger.info("Finished processing messages");
		return true;
	}

	@Override
	public String getStepId() {
		return STEP_ID + ":" + this.uuid;
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

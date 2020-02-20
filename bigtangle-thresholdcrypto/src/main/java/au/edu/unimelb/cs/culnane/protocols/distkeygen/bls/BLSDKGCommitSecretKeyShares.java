/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.distkeygen.bls;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.bls.BLSPrivateKey;
import au.edu.unimelb.cs.culnane.crypto.bls.BLSPublicKey;
import au.edu.unimelb.cs.culnane.crypto.bls.BLSSystemParameters;
import au.edu.unimelb.cs.culnane.crypto.secretsharing.SecretShare;
import au.edu.unimelb.cs.culnane.crypto.secretsharing.ShamirSecretSharing;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;
import au.edu.unimelb.cs.culnane.utils.IOUtils;
import au.edu.unimelb.cs.culnane.utils.IOUtils.EncodingType;
import it.unisa.dia.gas.jpbc.Element;

/**
 * Commits to the secret key share and receives commitments from other peers
 * 
 * @author Chris Culnane
 *
 */
public class BLSDKGCommitSecretKeyShares<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(BLSDKGCommitSecretKeyShares.class);

	/**
	 * Incoming message queue
	 */
	private LinkedBlockingDeque<E> incomingMessages = new LinkedBlockingDeque<E>();

	/**
	 * Static string STEP_ID
	 */
	public static final String STEP_ID = "BLS_DKG_CommitSecretKeyShares";

	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * String id of the key
	 */
	private String keyId;

	/**
	 * Static class with message keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class MESSAGES {
		public static final String G_TO_COEFS = "g_to_coefs";
	}

	/**
	 * Static class with storage keys
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
	 * BLSSystemParameters, including initialised pairing
	 */
	private BLSSystemParameters sysParams;

	/**
	 * Int threshold of key
	 */
	private int threshold;

	/**
	 * Constructs a new class to commit to the secret key and receive commits
	 * from other peers
	 * 
	 * @param sysParams
	 *            BLSSystemParameters with initialised pairing
	 * @param keyId
	 *            String id of key
	 * @param threshold
	 *            int threshold of key
	 */
	public BLSDKGCommitSecretKeyShares(BLSSystemParameters sysParams, String keyId, int threshold) {
		this.sysParams = sysParams;
		this.keyId = keyId;
		this.threshold = threshold;
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", getStepId());

		StorageObject<E> gendata = protocol.getFromStorage(BLSDKGGenerateKeys.STEP_ID + ":" + keyId);
		BLSPrivateKey privateKey = new BLSPrivateKey(gendata.getStorageObject(BLSDKGGenerateKeys.STORAGE.SECRET_KEY),
				sysParams.getPairing());
		BLSPublicKey publicKey = new BLSPublicKey(gendata.getStorageObject(BLSDKGGenerateKeys.STORAGE.PUBLIC_KEY),
				sysParams.getPairing());
		int peerCount = this.protocol.getPeerIds().size() + 1;
		// int threshold = peerCount - (peerCount / 3);
		ShamirSecretSharing secretSharing = new ShamirSecretSharing(peerCount, threshold,
				sysParams.getPairing().getZr().getOrder(), new SecureRandom());
		BigInteger[] coefs = secretSharing.generateCoefficients(privateKey.getKey().toBigInteger());

		SecretShare[] shares = secretSharing.generateShares(coefs);

		BigInteger[] available = new BigInteger[shares.length];
		for (int i = 0; i < shares.length; i++) {
			available[i] = shares[i].getShare();
		}
		if (privateKey.getKey().toBigInteger().equals(secretSharing.interpolate(available))) {
			logger.info("Shares are working");
		} else {
			logger.error("SHARES NOT WORKING");
		}
		Element[] g_to_coefs = new Element[coefs.length];

		List<String> g_to_coefs_List = new ArrayList<String>();
		for (int i = 0; i < g_to_coefs.length; i++) {
			if (i == 0) {
				if (!privateKey.getKey().equals(sysParams.getPairing().getZr().newElement(coefs[i]))) {
					throw new Exception("Coef zero is not secret");
				}
			}
			g_to_coefs[i] = sysParams.getGenerator()
					.powZn(sysParams.getPairing().getZr().newElement(coefs[i]).getImmutable());
			g_to_coefs_List.add(IOUtils.encodeData(EncodingType.BASE64, g_to_coefs[i].toBytes()));
		}
		if (!g_to_coefs[0].equals(publicKey.getPublicKeyElement())) {
			throw new Exception("Coefs don't equal public key");
		}

		StorageObject<E> mydata = this.protocol.getNewStorageObject();
		mydata.setStringList(STORAGE.G_TO_COEFS, g_to_coefs_List);
		List<StorageObject<E>> sharesList = new ArrayList<StorageObject<E>>();
		for (SecretShare share : shares) {
			StorageObject<E> shareStore = this.protocol.getNewStorageObject();
			share.storeInStorageObject(shareStore);
			sharesList.add(shareStore);
		}
		mydata.set(STORAGE.SHARES, sharesList);

		StorageObject<E> broadcast = this.protocol.getNewStorageObject();
		broadcast.setStringList(MESSAGES.G_TO_COEFS, g_to_coefs_List);
		broadcast.set(Protocol.MESSAGES.STEP, getStepId());

		this.protocol.sendBroadcast(broadcast.get());
		boolean receivedAll = false;

		TreeSet<String> peerIds = new TreeSet<String>();
		peerIds.addAll(this.protocol.getPeerIds());
		StorageObject<E> commitsReceived = this.protocol.getNewStorageObject();
		BigInteger checkDegree = BigInteger.ONE;
		while (!receivedAll) {
			try {
				E msg = this.incomingMessages.take();
				logger.info("Processing incoming message: {}", msg);
				String sender = msg.getString(Protocol.MESSAGES.SENDER);
				List<String> receivedCommit = msg.getStringList(MESSAGES.G_TO_COEFS);

				if (!commitsReceived.has(sender)) {
					commitsReceived.setStringList(sender, receivedCommit);
					/**
					 * checkDegree = checkDegree .multiply(new
					 * BigInteger(receivedCommit.getString(receivedCommit.length
					 * () - 1), 16)) .mod(group.get_p());
					 */
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
		// if (checkDegree.equals(BigInteger.ONE)) {
		// throw new ProtocolExecutionException("Polynomial degree test
		// failed");
		// }//TODO confirm if we need the check degree
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

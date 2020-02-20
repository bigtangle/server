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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.bls.BLSPrivateKey;
import au.edu.unimelb.cs.culnane.crypto.bls.BLSSystemParameters;
import au.edu.unimelb.cs.culnane.crypto.secretsharing.SecretShare;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;
import au.edu.unimelb.cs.culnane.utils.IOUtils;
import au.edu.unimelb.cs.culnane.utils.IOUtils.EncodingType;
import it.unisa.dia.gas.jpbc.Element;

/**
 * Protocol step to share the generated secret key
 * 
 * @author Chris Culnane
 *
 */
public class BLSDKGShareSecretKey<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(BLSDKGShareSecretKey.class);
	/**
	 * Queue for incoming messages
	 */
	private LinkedBlockingDeque<E> incomingMessages = new LinkedBlockingDeque<E>();

	/**
	 * Static string containing STEP_ID
	 */
	public static final String STEP_ID = "DKG_ShareSecretKey";

	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * String key id
	 */
	private String keyId;

	/**
	 * Static class with message keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class MESSAGES {
		public static final String SHARE = "share";
	}

	/**
	 * Static class with storage keys
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
	 * BLSSystemParameters, including initialised pairing
	 */
	private BLSSystemParameters sysParams;

	/**
	 * int threshold of key
	 */
	private int threshold;

	/**
	 * StorageObject to store key in
	 */
	private StorageObject<?> keyData;

	/**
	 * Construct a new protocol step to share the previously generated secret
	 * key
	 * 
	 * @param sysParams
	 *            BLSSystemParamters to use
	 * @param keyId
	 *            String id of key
	 * @param threshold
	 *            int threshold of key
	 * @param exportKey
	 *            StorageObject to store key in
	 */
	public BLSDKGShareSecretKey(BLSSystemParameters sysParams, String keyId, int threshold,
			StorageObject<?> exportKey) {
		this.sysParams = sysParams;
		this.keyId = keyId;
		this.threshold = threshold;
		this.keyData = exportKey;
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", getStepId());

		StorageObject<E> commitdata = protocol.getFromStorage(BLSDKGCommitSecretKeyShares.STEP_ID + ":" + keyId);
		List<StorageObject<E>> shares = commitdata.getList(BLSDKGCommitSecretKeyShares.STORAGE.SHARES);
		StorageObject<E> commits = commitdata
				.getStorageObject(BLSDKGCommitSecretKeyShares.STORAGE.COMMITS_TO_G_TO_COEFS);

		StorageObject<E> mydata = this.protocol.getNewStorageObject();
		// Map<String, SecretShare> sharesReceived = new HashMap<String,
		// SecretShare>();
		StorageObject<E> sharesReceived = this.protocol.getNewStorageObject();

		// Construct PeerIds, including my own
		TreeSet<String> peers = new TreeSet<String>();
		peers.addAll(this.protocol.getPeerIds());
		peers.add(this.protocol.getServerId());
		SecretShare myShare = null;
		Iterator<String> itr = peers.iterator();
		int peerCount = 0;
		while (itr.hasNext()) {
			SecretShare share = new SecretShare(shares.get(peerCount));
			String peerId = itr.next();
			if (peerId.equals(this.protocol.getServerId())) {
				myShare = share;

			} else {
				StorageObject<E> shareMessage = this.protocol.getNewStorageObject();
				shareMessage.set(MESSAGES.SHARE, shares.get(peerCount));
				shareMessage.set(Protocol.MESSAGES.STEP, this.getStepId());
				protocol.sendMessage(shareMessage.get(), peerId);
			}
			peerCount++;

		}
		boolean receivedAll = false;

		TreeSet<String> peerIds = new TreeSet<String>();
		peerIds.addAll(this.protocol.getPeerIds());

		ArrayList<String> peerIndex = new ArrayList<String>();
		peerIndex.addAll(this.protocol.getPeerIds());
		peerIndex.add(this.protocol.getServerId());
		Collections.sort(peerIndex);

		Element[] combinedCheckValues = new Element[peerIndex.size()];

		Element combinedSecretKey = null;// pairing.getZr().newElement(share.getValue())
		// BigInteger combinedSecretKey = BigInteger.ZERO;
		while (!receivedAll) {
			try {
				E msg = this.incomingMessages.take();
				logger.info("Processing incoming message: {}", msg);
				String sender = msg.getString(Protocol.MESSAGES.SENDER);
				SecretShare receivedShare = new SecretShare(msg.getStorageObject(MESSAGES.SHARE));

				if (!sharesReceived.has(sender)) {
					sharesReceived.set(sender, msg.getStorageObject(MESSAGES.SHARE));
					List<String> senderCommit = commits.getStringList(sender);
					Element shareCheck = null;
					for (int i = 0; i < peerIndex.size(); i++) {
						BigInteger index = BigInteger.valueOf(i + 1);
						Element checkValue = null;
						for (int j = 0; j < threshold; j++) {
							Element commitValue = sysParams.getPairing().getG2().newElement();
							commitValue.setFromBytes(IOUtils.decodeData(EncodingType.BASE64, senderCommit.get(j)));
							Element tempCheckValue = commitValue.getImmutable().mul(index.pow(j));

							if (checkValue == null) {
								checkValue = tempCheckValue;
							} else {
								checkValue = checkValue.getImmutable().add(tempCheckValue);
							}

						}
						if (combinedCheckValues[i] == null) {
							combinedCheckValues[i] = checkValue;
						} else {
							combinedCheckValues[i] = combinedCheckValues[i].getImmutable().add(checkValue);
						}

						if (i == (peerIndex.indexOf(protocol.getServerId()))) {
							shareCheck = checkValue;
						}
					}
					Element receivedElement = sysParams.getPairing().getZr().newElement(receivedShare.getShare());
					if (combinedSecretKey == null) {
						combinedSecretKey = receivedElement;
					} else {
						combinedSecretKey = combinedSecretKey.getImmutable().add(receivedElement);
					}

					if (!shareCheck.equals(sysParams.getGenerator().powZn(receivedElement))) {
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
		List<String> mycommits = commitdata.getStringList(BLSDKGCommitSecretKeyShares.STORAGE.G_TO_COEFS);

		for (int i = 0; i < peerIndex.size(); i++) {
			BigInteger index = BigInteger.valueOf(i + 1);
			Element checkValue = null;
			for (int j = 0; j < threshold; j++) {
				System.out.println(mycommits.get(j));
				BigInteger commitValue = new BigInteger(IOUtils.decodeData(EncodingType.BASE64, mycommits.get(j)));
				Element commitElement = sysParams.getPairing().getG2().newElement();
				commitElement.setFromBytes(commitValue.toByteArray());
				if (checkValue == null) {
					checkValue = commitElement.getImmutable();
				} else {
					checkValue = checkValue.mul(commitElement.mul(index.pow(j)));
				}

			}
			combinedCheckValues[i] = combinedCheckValues[i].getImmutable().add(checkValue);
		}
		List<String> publicVerification = new ArrayList<String>();

		for (Element v : combinedCheckValues) {
			publicVerification.add(IOUtils.encodeData(EncodingType.BASE64, v.toBytes()));
		}

		
		// add to combinedcheck
		logger.info("Finished processing messages");
		logger.info("Received all shares");
		logger.info("Starting share verification");
		combinedSecretKey = combinedSecretKey.getImmutable()
				.add(sysParams.getPairing().getZr().newElement(myShare.getShare()));
		BLSPrivateKey privKey = new BLSPrivateKey(combinedSecretKey);

		StorageObject<E> privKeyStore = this.protocol.getNewStorageObject();
		privKey.storeInStorageObject(privKeyStore);
		mydata.set(STORAGE.SHARES_RECEIVED, sharesReceived);
		mydata.set(STORAGE.COMBINED_SECRET_KEY, privKeyStore);
		mydata.setStringList(STORAGE.PUBLIC_VERIFICATION, publicVerification);
		// Export KeyData
		privKey.storeInStorageObject(keyData);
		keyData.setStringList("verify", publicVerification);
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

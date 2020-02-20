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
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.Group;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PdCCreateCommit;
import au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PdCOpenCommit;
import au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenCommit;
import au.edu.unimelb.cs.culnane.storage.StorageObject;

/**
 * This should either initialise the pedersen commitment, or if it has been
 * pre-constructed, reload the values
 * 
 * @author cculnane
 *
 */
public class DKGCombinePublicKey<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(DKGCombinePublicKey.class);

	/**
	 * String STEP_ID
	 */
	public static final String STEP_ID = "DKG_CombinePublicKey";
	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * String key id for generated keys
	 */
	private String keyId;

	/**
	 * Public static class with String keys used for storage
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class STORAGE {
		public static final String JOINT_PUBLIC_KEY = "h";
	}

	/**
	 * The underlying group to use for key generation
	 */
	private Group group;

	/**
	 * StorageObject<?> to store generated key data in
	 */
	private StorageObject<?> keyData;

	/**
	 * Creates a new Distributed Key Generation Combine Public Key Step which
	 * will combine the public key from the different peers into a single joint
	 * public key
	 * 
	 * @param group
	 *            Group used for key generation
	 * @param keyId
	 *            String key id to differentiate the keys generated
	 * @param keyData
	 *            StorageObject<?> to store key data
	 */
	public DKGCombinePublicKey(Group group, String keyId, StorageObject<?> keyData) {
		this.group = group;
		this.keyId = keyId;
		this.keyData = keyData;
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", getStepId());

		StorageObject<E> gendata = protocol.getFromStorage(PdCCreateCommit.STEP_ID + ":Commit:" + keyId).get();
		PedersenCommit mycommit = new PedersenCommit(gendata.getStorageObject(PdCCreateCommit.STORAGE.COMMIT));
		StorageObject<E> commitdata = protocol.getFromStorage(PdCOpenCommit.STEP_ID + ":Commit:" + keyId).get();
		Map<String, StorageObject<E>> openings = commitdata.getMap(PdCOpenCommit.STORAGE.RECEIVED_OPENINGS);
		TreeSet<String> peerIds = new TreeSet<String>();
		peerIds.addAll(this.protocol.getPeerIds());
		peerIds.add(this.protocol.getServerId());
		BigInteger combinedPublicKey = BigInteger.ONE;
		Iterator<String> itr = peerIds.iterator();
		while (itr.hasNext()) {
			BigInteger publicKeyShare;
			String currentId = itr.next();
			logger.info("Adding value: {}", currentId);
			if (currentId.equals(this.protocol.getServerId())) {
				publicKeyShare = mycommit.getM();
			} else {
				logger.info("open:{}", openings);
				publicKeyShare = openings.get(currentId).getBigInteger(PdCOpenCommit.MESSAGES.M);
				logger.info("publicKeyShare from{},{}", currentId, publicKeyShare.toString(16));
			}
			logger.info("public key share: {}", publicKeyShare);
			combinedPublicKey = combinedPublicKey.multiply(publicKeyShare).mod(group.get_p());
		}

		StorageObject<E> mydata = this.protocol.getNewStorageObject();
		mydata.set(STORAGE.JOINT_PUBLIC_KEY, combinedPublicKey);
		this.keyData.set("h", combinedPublicKey);
		logger.info("Generated Joint Public key");
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
	}

	@Override
	public void setProtocol(Protocol<E> protocol) {
		this.protocol = protocol;

	}

}

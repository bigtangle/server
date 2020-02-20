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

import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.bls.BLSSystemParameters;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PdCCreateCommit;
import au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PdCOpenCommit;
import au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PedersenCommit;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import it.unisa.dia.gas.jpbc.Element;

/**
 * Combines the received public shares into a single public key
 * 
 * @author Chris Culnane
 *
 */
public class BLSDKGCombinePublicKey<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(BLSDKGCombinePublicKey.class);

	/**
	 * Static string STEP_ID
	 */
	public static final String STEP_ID = "DKG_CombinePublicKey";

	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * String id for the key
	 */
	private String keyId;

	/**
	 * Static class with storage keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class STORAGE {
		public static final String JOINT_PUBLIC_KEY = "h";
	}

	/**
	 * BLSSystemParameters holds group and pairing information
	 */
	private BLSSystemParameters sysParams;

	/**
	 * StorageObject to store key data in
	 */
	private StorageObject<?> keyData;

	/**
	 * Constructs a new BLSDKGCombinePublicKey to combine the previously
	 * received public keys
	 * 
	 * @param sysParams
	 *            BLSSystemParameters with initialised pairing
	 * @param keyId
	 *            String key id
	 * @param keyData
	 *            StorageObject to store key in
	 */
	public BLSDKGCombinePublicKey(BLSSystemParameters sysParams, String keyId, StorageObject<?> keyData) {
		this.sysParams = sysParams;
		this.keyId = keyId;
		this.keyData = keyData;
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", getStepId());

		StorageObject<E> gendata = protocol.getFromStorage(PdCCreateCommit.STEP_ID + ":Commit:" + keyId).get();
		PedersenCommit mycommit = new PedersenCommit(gendata.getStorageObject(PdCCreateCommit.STORAGE.COMMIT));
		StorageObject<E> commitdata = protocol.getFromStorage(PdCOpenCommit.STEP_ID + ":Commit:" + keyId).get();

		Element jointPublicKey = null;

		Map<String, StorageObject<E>> openings = commitdata.getMap(PdCOpenCommit.STORAGE.RECEIVED_OPENINGS);
		TreeSet<String> peerIds = new TreeSet<String>();
		peerIds.addAll(this.protocol.getPeerIds());
		peerIds.add(this.protocol.getServerId());
		Iterator<String> itr = peerIds.iterator();
		while (itr.hasNext()) {
			Element publicKeyShare;
			String currentId = itr.next();
			if (currentId.equals(this.protocol.getServerId())) {
				publicKeyShare = this.sysParams.getPairing().getG2().newElement();
				// Set the element value from the bytes decoded from the JSON
				publicKeyShare.setFromBytes(mycommit.getM().toByteArray());

				// publicKeyShare = mycommit.getM();
			} else {
				
				// Construct a new element
				publicKeyShare = this.sysParams.getPairing().getG2().newElement();
				// Set the element value from the bytes decoded from the JSON
				publicKeyShare
						.setFromBytes(openings.get(currentId).getBigInteger(PdCOpenCommit.MESSAGES.M).toByteArray());

				logger.info("publicKeyShare from{},{}", currentId, publicKeyShare);

			}
			if (jointPublicKey == null) {
				jointPublicKey = publicKeyShare.getImmutable();
			} else {
				jointPublicKey = jointPublicKey.getImmutable().mul(publicKeyShare);
			}
		}

		StorageObject<E> mydata = this.protocol.getNewStorageObject();
		mydata.set(STORAGE.JOINT_PUBLIC_KEY, jointPublicKey.toBytes());
		this.keyData.set("h", jointPublicKey.toBytes());
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

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.bls.BLSKeyPair;
import au.edu.unimelb.cs.culnane.crypto.bls.BLSSystemParameters;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.storage.StorageObject;

/**
 * Protocol step to generates a new private and public BLS key
 * 
 * @author Chris Culnane
 *
 */
public class BLSDKGGenerateKeys<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(BLSDKGGenerateKeys.class);

	/**
	 * Static string containing STEP_ID
	 */
	public static final String STEP_ID = "BLS_DKG_GenPublicKey";

	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * String id of the key
	 */
	private String keyId;

	/**
	 * Static class of storage keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class STORAGE {
		public static final String SECRET_KEY = "x";
		public static final String SECRET_KEY_COMMIT = "x_commit";
		public static final String PUBLIC_KEY = "h";
		public static final String PUBLIC_KEY_COMMIT = "h_commit";
	}

	/**
	 * BLSSystemParameters, including an initialised pairing
	 */
	private BLSSystemParameters sysParams;

	/**
	 * Construct a new Protocol step to generate a BLS key pair
	 * 
	 * @param sysParams
	 *            BLSSystemParameters to use for key generation
	 * @param keyId
	 *            String id of key
	 */
	public BLSDKGGenerateKeys(BLSSystemParameters sysParams, String keyId) {
		this.sysParams = sysParams;
		this.keyId = keyId;
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", getStepId());
		BLSKeyPair keyPair = BLSKeyPair.generateKeyPair(this.sysParams);

		StorageObject<E> mydata = this.protocol.getNewStorageObject();
		StorageObject<E> privateKey = mydata.getNewStorageObject();
		keyPair.getPrivateKey().storeInStorageObject(privateKey);
		StorageObject<E> publicKey = mydata.getNewStorageObject();
		keyPair.getPublicKey().storeInStorageObject(publicKey);
		mydata.set(STORAGE.SECRET_KEY, privateKey);
		mydata.set(STORAGE.PUBLIC_KEY, publicKey);

		mydata.set(STORAGE.SECRET_KEY_COMMIT, keyPair.getPrivateKey().getKey().toBigInteger());
		mydata.set(STORAGE.PUBLIC_KEY_COMMIT,
				new BigInteger(1, keyPair.getPublicKey().getPublicKeyElement().toBytes()));

		logger.info("Generated Secret and Public key");

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

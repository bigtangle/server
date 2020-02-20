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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.Group;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.storage.StorageObject;

/**
 * Distributed Key Generation Generate Key Step - will generate a public and
 * private key for this peer. These will later be shared with out peers.
 * 
 * @author Chris Culnane
 *
 */
public class DKGGenerateKeys<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(DKGGenerateKeys.class);

	/**
	 * String STEP_ID
	 */
	public static final String STEP_ID = "DKG_GenPublicKey";

	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * String keyId to distinguish keys generated in this run
	 */
	private String keyId;

	/**
	 * static class with storage keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class STORAGE {
		public static final String SECRET_KEY = "x";
		public static final String PUBLIC_KEY = "h";
	}

	/**
	 * Underlying group to use for key generation
	 */
	private Group group;

	/**
	 * Constructs a new Distributed Key Generation Generate Keys Step with group
	 * and id as specified
	 * 
	 * @param group
	 *            Group to generate keys from
	 * @param keyId
	 *            String id to identify these keys
	 */
	public DKGGenerateKeys(Group group, String keyId) {
		this.group = group;
		this.keyId = keyId;
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", getStepId());

		BigInteger x = group.getRandomValueInQ(new SecureRandom());
		BigInteger h = group.get_g().modPow(x, group.get_p());

		StorageObject<E> mydata = this.protocol.getNewStorageObject();

		mydata.set(STORAGE.SECRET_KEY, x);
		mydata.set(STORAGE.PUBLIC_KEY, h);

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

/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.pedersencommitment;

import java.math.BigInteger;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.prng.FixedSecureRandom;
import org.bouncycastle.crypto.prng.SP800SecureRandom;
import org.bouncycastle.crypto.prng.SP800SecureRandomBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.P_Length;
import au.edu.unimelb.cs.culnane.crypto.ZpStarSafePrimeGroupRFC3526;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.protocols.cointoss.CTOpenCommit;
import au.edu.unimelb.cs.culnane.storage.StorageObject;

/**
 * Protocol step to initialises the Pedersen Commitment by taking the value the
 * random value generated from the distributed coin toss to generate a joint
 * value to use for commitments
 * 
 * @author Chris Culnane
 *
 */
public class PdCInit<E extends CommunicationLayerMessage<?> & StorageObject<E>> implements ProtocolStep<Boolean, E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(PdCInit.class);

	/**
	 * static string with STEP_ID
	 */
	public static final String STEP_ID = "PdC_Init";

	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * Static class containing storage fields
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class STORAGE {
		public static final String GROUP = "group";
		public static final String V = "v";

	}

	/**
	 * Static class containing message fields
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class MESSAGES {

	}

	/**
	 * Constructs a new Pedersen Commitment Initialiser
	 */
	public PdCInit() {

	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", STEP_ID);
		StorageObject<E> storageObj = this.protocol.getFromStorage(STEP_ID);

		if (storageObj == null) {
			logger.info("PedersenCommitment {} is unitialised. Will start initialisation", STEP_ID);
			logger.info("Initalising group");
			ZpStarSafePrimeGroupRFC3526 group = new ZpStarSafePrimeGroupRFC3526();
			group.initialise(P_Length.P3072);

			StorageObject<E> commitData = this.protocol.getFromStorage(CTOpenCommit.STEP_ID);
			if (commitData.getString(CTOpenCommit.STORAGE.JOINT_RANDOM_VALUE_STATUS)
					.equals(CTOpenCommit.STORAGE.JOINT_RANDOM_VALUE_STATUS_USED)) {
				throw new ProtocolExecutionException("Joint random value has already been used");
			}

			byte[] inputSeed = Base64.decodeBase64(commitData.getString(CTOpenCommit.STORAGE.JOINT_RANDOM_VALUE));
			commitData.set(CTOpenCommit.STORAGE.JOINT_RANDOM_VALUE_STATUS,
					CTOpenCommit.STORAGE.JOINT_RANDOM_VALUE_STATUS_USED);
			this.protocol.addToStorage(CTOpenCommit.STEP_ID, commitData);
			FixedSecureRandom fsr = new FixedSecureRandom(inputSeed);
			// We disable predictionResistant since this would require
			// re-seeding
			// and we only have a finite amount of seed randomness
			SP800SecureRandomBuilder rBuild = new SP800SecureRandomBuilder(fsr, false);
			rBuild.setPersonalizationString(STEP_ID.getBytes());
			SP800SecureRandom drbg = rBuild.buildHash(new SHA256Digest(), null, false);
			BigInteger v = group.createGenerator(drbg);
			StorageObject<E> mydata = this.protocol.getNewStorageObject();
			mydata.set(STORAGE.GROUP, group);
			mydata.set(STORAGE.V, v);
			protocol.addToStorage(STEP_ID, mydata);
			logger.info("Finished initalising group");
			return true;
		} else {
			logger.info("PedersenCommitment {} is already initialised. ", STEP_ID);
			return true;
		}
	}

	@Override
	public String getStepId() {
		return STEP_ID;
	}

	@Override
	public void processMessage(E msg) {
		logger.error("Incoming message during init, will ignore");
	}

	@Override
	public void setProtocol(Protocol<E> protocol) {
		this.protocol = protocol;

	}

}

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

import java.io.IOException;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.P_Length;
import au.edu.unimelb.cs.culnane.crypto.ZpStarSafePrimeGroupRFC3526;
import au.edu.unimelb.cs.culnane.crypto.bls.BLSSystemParameters;
import au.edu.unimelb.cs.culnane.crypto.exceptions.GroupException;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.cointoss.CTGenRandAndCommit;
import au.edu.unimelb.cs.culnane.protocols.cointoss.CTOpenCommit;
import au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PdCCreateCommit;
import au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PdCInit;
import au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PdCOpenCommit;
import au.edu.unimelb.cs.culnane.storage.StorageObject;

/**
 * Protocol to handle distributed generation of a threshold BLS key
 * 
 * @author Chris Culnane
 *
 * @param <E>
 */
public class BLSDistKeyGenProtocol<E extends CommunicationLayerMessage<?> & StorageObject<E>> extends Protocol<E> {

	/**
	 * String prefix for storage data
	 */
	private static final String STORAGE_PREFIX = "BLSDistKeyGen";

	/**
	 * StorageObject to store KeyData
	 */
	private StorageObject<?> keyData;

	/**
	 * Construct a new BLSDistKeyGenProtocol to generate a new distributed
	 * threshold BLS key
	 * 
	 * @param keyId
	 *            String id for the generated key
	 * @param keyStorage
	 *            StorageObject to store key data in
	 * @throws GroupException
	 * @throws IOException
	 */
	public BLSDistKeyGenProtocol(String keyId, StorageObject<?> keyStorage) throws GroupException, IOException {
		super("BLSDistKeyGen");
		this.keyData = keyStorage;
		BLSSystemParameters sysParams = new BLSSystemParameters("./params.json");

		this.addProtocolStep(new CTGenRandAndCommit<E>(2048));
		this.addProtocolStep(new CTOpenCommit<E>());
		this.addProtocolStep(new PdCInit<E>());
		ZpStarSafePrimeGroupRFC3526 group = new ZpStarSafePrimeGroupRFC3526();
		group.initialise(P_Length.P2046);
		BLSDKGGenerateKeys<E> keyGen = new BLSDKGGenerateKeys<E>(sysParams, keyId);
		this.addProtocolStep(keyGen);
		this.addProtocolStep(new PdCCreateCommit<E>("Commit:" + keyId, keyGen.getStepId(),
				BLSDKGGenerateKeys.STORAGE.PUBLIC_KEY_COMMIT));
		this.addProtocolStep(new PdCOpenCommit<E>("Commit:" + keyId));
		this.addProtocolStep(new BLSDKGCombinePublicKey<E>(sysParams, keyId, keyData));
		this.addProtocolStep(new BLSDKGCommitSecretKeyShares<E>(sysParams, keyId, 2));
		this.addProtocolStep(new BLSDKGShareSecretKey<E>(sysParams, keyId, 2, keyData));
		this.addProtocolStep(new BLSDKGSignPublicKey<E>(keyId));
	}

	@Override
	public String getStoragePrefix() {
		return STORAGE_PREFIX;
	}

}

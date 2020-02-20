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

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.P_Length;
import au.edu.unimelb.cs.culnane.crypto.ZpStarSafePrimeGroupRFC3526;
import au.edu.unimelb.cs.culnane.crypto.exceptions.GroupException;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.cointoss.CTGenRandAndCommit;
import au.edu.unimelb.cs.culnane.protocols.cointoss.CTOpenCommit;
import au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PdCCreateCommit;
import au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PdCInit;
import au.edu.unimelb.cs.culnane.protocols.pedersencommitment.PdCOpenCommit;
import au.edu.unimelb.cs.culnane.storage.StorageObject;

/**
 * Distributed Key Generation Protocol based on Feldman Secret Sharing. This
 * requires all peers to take part honestly, otherwise it will throw an
 * exception. If an exception occurs it is essential that the transcripts are
 * inspected and the misbehaving peer is excluded. A more secure distribute key
 * generation would use Gennaro, but that is considerably more complicated to
 * implement.
 * 
 * @author Chris Culnane
 *
 */
public class DistKeyGenProtocol<E extends CommunicationLayerMessage<?> & StorageObject<E>> extends Protocol<E> {

	/**
	 * Protocol ID
	 */
	private static final String PROTOCOL_ID = "DistKeyGen";

	/**
	 * StorageObject to hold generated key data
	 */
	private StorageObject<?> keyData;

	/**
	 * Constructs a new Distributed Key Generation Protocol and adds the
	 * necessary protocol steps.
	 * 
	 * @param keyId
	 *            String keyid - to distinguish different runs of the protocol
	 * @param keyJSONData
	 *            StorageObject to store key data in
	 * @param threshold
	 *            int threshold for the key generation
	 * @throws GroupException
	 */
	public DistKeyGenProtocol(String keyId, StorageObject<?> keyJSONData, int threshold) throws GroupException {
		super(PROTOCOL_ID);
		this.keyData = keyJSONData;
		this.addProtocolStep(new CTGenRandAndCommit<E>(2048));
		this.addProtocolStep(new CTOpenCommit<E>());
		this.addProtocolStep(new PdCInit<E>());
		ZpStarSafePrimeGroupRFC3526 group = new ZpStarSafePrimeGroupRFC3526();
		group.initialise(P_Length.P2046);
		DKGGenerateKeys<E> keyGen = new DKGGenerateKeys<E>(group, keyId);
		this.addProtocolStep(keyGen);
		this.addProtocolStep(
				new PdCCreateCommit<E>("Commit:" + keyId, keyGen.getStepId(), DKGGenerateKeys.STORAGE.PUBLIC_KEY));
		this.addProtocolStep(new PdCOpenCommit<E>("Commit:" + keyId));
		this.addProtocolStep(new DKGCombinePublicKey<E>(group, keyId, keyData));
		this.addProtocolStep(new DKGCommitSecretKeyShares<E>(group, keyId, threshold));
		this.addProtocolStep(new DKGShareSecretKey<E>(group, keyId, threshold, keyData));
		this.addProtocolStep(new DKGSignPublicKey<E>(keyId));

	}

	@Override
	public String getStoragePrefix() {
		return PROTOCOL_ID;
	}

}

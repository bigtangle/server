/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.threshold.pet;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.Group;
import au.edu.unimelb.cs.culnane.crypto.elgamal.ElGamalCipher;
import au.edu.unimelb.cs.culnane.crypto.exceptions.GroupException;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.threshold.decryption.ThresholdDecryption;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;
import au.edu.unimelb.cs.culnane.storage.json.JSONStorageObject;

/**
 * Protocol for performing a plaintext equivalence test. This is currently not
 * strictly speaking a threshold protocol. The final decryption step is
 * thresholded, the calculation of the PET is currently distributed and requires
 * all parties to participate
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            underlying type for communication and storage, should implement
 *            CommunicationLayerMessage and StorageObject with appropriate type
 */
public class ThresholdPETProtocol<E extends CommunicationLayerMessage<?> & StorageObject<E>> extends Protocol<E> {

	/**
	 * Static string with storage prefix
	 */
	private static final String STORAGE_PREFIX = "ThresholdPETProtocol";

	/**
	 * Constructs a new Threshold PET protocol
	 * 
	 * @param uuid
	 *            String with UUID of this run
	 * @param group
	 *            Crypto group to perform operations on
	 * @param cipherOneStore
	 *            StorageObject containing cipher one
	 * @param cipherTwoStore
	 *            StorageOBject containing cipher two - to check if equals
	 *            cipherOne
	 * @param keyData
	 *            StorageObject with key data
	 * @param threshold
	 *            int threshold of key
	 * @throws GroupException
	 * 
	 * @throws StorageException
	 */
	public ThresholdPETProtocol(String uuid, Group group, StorageObject<?> cipherOneStore,
			StorageObject<?> cipherTwoStore, StorageObject<?> keyData, int threshold)
			throws GroupException, StorageException {
		super("ThresholdDecryptionProtocol");
		ElGamalCipher cipherOne = new ElGamalCipher(cipherOneStore);
		ElGamalCipher cipherTwo = new ElGamalCipher(cipherTwoStore);
		this.addProtocolStep(new ThresholdPETCommit<E>(group, uuid, threshold));
		JSONStorageObject decryptCipher = new JSONStorageObject();
		this.addProtocolStep(new ThresholdPETPrepare<E>(group, cipherOne, cipherTwo, decryptCipher, uuid, threshold));
		this.addProtocolStep(new ThresholdDecryption<E>(group, decryptCipher, keyData, uuid, threshold));
	}

	@Override
	public String getStoragePrefix() {
		return STORAGE_PREFIX;
	}

}

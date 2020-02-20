/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.threshold.decryption;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.Group;
import au.edu.unimelb.cs.culnane.crypto.exceptions.GroupException;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * ThresholDecryptionProtocol to perform threshold decryption and combining
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            underlying type for communication and storage, should implement
 *            CommunicationLayerMessage and StorageObject with appropriate type
 */
public class ThresholdDecryptionProtocol<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		extends Protocol<E> {

	private ThresholdDecryption<E> threshDec;
	/**
	 * Static string with storage prefix
	 */
	private static final String STORAGE_PREFIX = "ThresholdDecryptionProtocol";

	/**
	 * Constructs a new ThresholdDecryptionProtocol with the specified uuid,
	 * cipher and key data
	 * 
	 * @param uuid
	 *            String containing UUID for this decryption run
	 * @param group
	 *            Group that operations will be performed on
	 * @param cipher
	 *            StorageObject containing the cipher
	 * @param keyData
	 *            StorageObject containing the key data
	 * @param threshold
	 *            int threshold of key
	 * @throws GroupException
	 * @throws StorageException
	 */
	public ThresholdDecryptionProtocol(String uuid, Group group, StorageObject<?> cipher, StorageObject<?> keyData,
			int threshold) throws GroupException, StorageException {
		super("ThresholdDecryptionProtocol");
		threshDec = new ThresholdDecryption<E>(group, cipher, keyData, uuid, threshold);
		this.addProtocolStep(threshDec);
	}

	@Override
	public String getStoragePrefix() {
		return STORAGE_PREFIX;
	}

}

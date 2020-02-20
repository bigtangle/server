/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols;

import au.edu.unimelb.cs.culnane.storage.StorageObject;

/**
 * Generic ProtocolStorage interface provides basic methods for storing and
 * retrieving StorageObject by a key. The actual underlying storage medium is
 * not fixed, it could be in-memory, JSON, flat file, or a database.
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            The underlying StorageObject type
 */
public interface ProtocolStore<E> {

	/**
	 * Adds the StorageObject to the transcript. The transcript is just a log of
	 * incoming messages and often used to audit peers in the event of dishonest
	 * behavior. addToTranscript to write to a persistent store, so if
	 * misbehaviour takes place the transcript can be retrieved and analyzed.
	 * 
	 * @param message
	 *            StorageObject to add to the transcript
	 */
	public void addToTranscript(StorageObject<E> message);

	/**
	 * Stores a StorageObject, indexing it by the specified key
	 * 
	 * @param id
	 *            String id to index this object by
	 * @param obj
	 *            StorageObject to be stored
	 */
	public void store(String id, StorageObject<E> obj);

	/**
	 * Gets the StorageObject indexed by the specified id.
	 * 
	 * @param id
	 *            String id of StorageObject to retrieve
	 * @return StorageObject at that key or null if not found
	 */
	public StorageObject<E> get(String id);

	/**
	 * Returns a new StorageObject of the appropriate type
	 * 
	 * @return StorageObject of the appropriate type
	 */
	public StorageObject<E> getNewStorageObject();

}

/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.storage;

import java.io.BufferedWriter;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * Simple interface to provides access to the underlying data, and methods for
 * storing and retrieving data. This is byte based to ensure compatibility with
 * a maximum number of different storage platforms.
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            The underlying storage object
 */
public interface StorageObject<E> {

	/**
	 * Gets the underlying storage object
	 * 
	 * @return underlying data object
	 */
	public E get();

	/**
	 * Gets a BigInteger value from the StorageObject
	 * 
	 * @param id
	 *            String field id
	 * @return BigInteger of value
	 * @throws StorageException
	 *             - if the field does not exist
	 */
	public BigInteger getBigInteger(String id) throws StorageException;

	/**
	 * Gets a List of BigIntegers from the StorageObject
	 * 
	 * @param id
	 *            String field id
	 * @return List of BigIntegers
	 * @throws StorageException
	 *             - if the field does not exist
	 */
	public List<BigInteger> getBigIntList(String id) throws StorageException;

	/**
	 * Gets this StorageObject represented in a byte array
	 * 
	 * @return byte array containing StorageObject
	 * @throws StorageException
	 */
	public byte[] getBytes() throws StorageException;

	/**
	 * Gets the byte array value of a field
	 * 
	 * @param id
	 *            String field id
	 * @return byte array of value
	 * @throws StorageException
	 *             - if the field does not exist
	 */
	public byte[] getBytes(String id) throws StorageException;

	/**
	 * Gets an interator of the field names in this StorageObject
	 * 
	 * @return String Iterator of field names
	 */
	public Iterator<String> getFields();

	/**
	 * Gets an int value
	 * 
	 * @param id
	 *            String field id
	 * @return int of the value
	 * @throws StorageException
	 *             - if the field does not exist
	 */
	public int getInt(String id) throws StorageException;

	/**
	 * Gets a list of StorageObjects from a field
	 * 
	 * @param id
	 *            String field id
	 * @return List of StorageObjects
	 * @throws StorageException
	 *             - if the field does not exist
	 */
	public List<StorageObject<E>> getList(String id) throws StorageException;

	/**
	 * Gets a Map of StorageObjects, indexed by Strings
	 * 
	 * @param id
	 *            String field id
	 * @return Map of StorageObjects indexed by Strings
	 * @throws StorageException
	 *             - if the field does not exist
	 */
	public Map<String, StorageObject<E>> getMap(String id) throws StorageException;

	/**
	 * Gets a new StorageObject - this method must return a new instance of a
	 * StorageObject with the appropriate type. It must not return a reference
	 * to itself. This method should be used for generating new storage objects
	 * when new data is to be stored. This reason for this is to allow a type
	 * agnostic user to handle different types. Without it, the type of the
	 * StorageObject would have to be hard-coded.
	 * 
	 * @return new empty StorageObject of the appropriate type
	 */
	public StorageObject<E> getNewStorageObject();

	/**
	 * Gets the underlying data object of StorageObject that is stored within
	 * this StorageObject. Note: this returns the underlying type, not a
	 * StorageObject. If that is self referential then it is both.
	 * 
	 * @param id
	 *            String id of object to retrieve
	 * @return underlying data object of a StorageObject stored in this
	 *         StorageObject
	 * @throws StorageException
	 *             - if the StorageObject doesn't contain the specified field
	 */
	public E getStorageObject(String id) throws StorageException;

	/**
	 * Gets a String with specified id
	 * 
	 * @param id
	 *            String id to retrieve
	 * @return String of the value stored in field id
	 * @throws StorageException
	 *             - if the StorageObject does not contain the specified field
	 */
	public String getString(String id) throws StorageException;

	/**
	 * Gets a List of Strings stored in the StorageObject under the specified
	 * key
	 * 
	 * @param id
	 *            String id of field to retrieve
	 * @return List of String in the specified field
	 * @throws StorageException
	 *             - if the StorageObject does not contain an appropriate field
	 */
	public List<String> getStringList(String id) throws StorageException;

	/**
	 * Gets a map of strings indexed by strings from the StorageObject
	 * 
	 * @param id
	 *            String id of field to retrieve
	 * @return Map of Strings, indexed by Strings
	 * @throws StorageException
	 *             - if the StorageObject does not contain an appropriate field
	 */
	public Map<String, String> getStringMap(String id) throws StorageException;

	/**
	 * Check if this StorageObject has an entry for the specified id
	 * 
	 * @param id
	 *            String id to check
	 * @return boolean, returns true if field exists, false if not
	 */
	public boolean has(String id);

	/**
	 * Creates a new StorageObject with the byte data specified. This should be
	 * used to create a StorageObject from data read from the underlying storage
	 * medium. Note: It is important that this creates a new instance of
	 * StorageObject, and not just populate the calling instance.
	 * 
	 * @param data
	 *            byte array of data that has been read and contains a
	 *            StorageObject
	 * @return StorageObject containing a representation of the byte data
	 */
	public StorageObject<E> load(byte[] data);

	/**
	 * Sets a String value against the specified id, overwriting any existing
	 * value
	 * 
	 * @param id
	 *            String id
	 * @param value
	 *            String value to store
	 */
	public void set(String id, String value);

	/**
	 * Sets a BigInteger value against the specified id, overwriting any
	 * existing value
	 * 
	 * @param id
	 *            String id
	 * @param value
	 *            BigInteger value to store
	 */
	public void set(String id, BigInteger value);

	/**
	 * Sets a List of BigIntegers against the specified id, overwriting any
	 * existing value
	 * 
	 * @param id
	 *            String id
	 * @param list
	 *            List of BigIntegers to store
	 * @throws StorageException
	 */
	public void setBigIntList(String id, List<BigInteger> list) throws StorageException;

	/**
	 * Sets a byte array value against the specified id, overwriting any
	 * existing value
	 * 
	 * @param id
	 *            String id
	 * @param value
	 *            byte array value to store
	 */
	public void set(String id, byte[] value);

	/**
	 * Sets an int value against the specified id, overwriting any existing
	 * value
	 * 
	 * @param id
	 *            String id
	 * @param value
	 *            int value to store
	 */
	public void set(String id, int value);

	/**
	 * Sets a List of StorageObjects against the specified id, overwriting any
	 * existing value
	 * 
	 * @param id
	 *            String id
	 * @param list
	 *            List of StorageObject to store
	 */
	public void set(String id, List<StorageObject<E>> list);

	/**
	 * Sets a Map of StorageObjects, indexed by String, against the specified
	 * id, overwriting any existing value
	 * 
	 * @param id
	 *            String id
	 * @param map
	 *            Map of StorageObjects indexed by Strings
	 */
	public void set(String id, Map<String, StorageObject<E>> map);

	/**
	 * Sets a StorageOject against the specified id, overwriting any existing
	 * value
	 * 
	 * @param id
	 *            String id
	 * @param storageObj
	 *            StorageObject to store
	 */
	public void set(String id, StorageObject<E> storageObj);

	/**
	 * Sets a Storable Object against the specified id, overwriting any existing
	 * value
	 * 
	 * @param id
	 *            String id
	 * @param storable
	 *            Storable object to store
	 */
	public void set(String id, Storable storable);

	/**
	 * Sets a List of String against the specified id, overwriting any existing
	 * value
	 * 
	 * @param id
	 *            String id
	 * @param list
	 *            List of Strings to store
	 */
	public void setStringList(String id, List<String> list);

	/**
	 * Sets a Map of Strings, indexed by Strings, against the specified id,
	 * overwriting any existing value
	 * 
	 * @param id
	 *            String id
	 * @param map
	 *            Map of Strings, indexed by Strings, to store
	 */
	public void setStringMap(String id, Map<String, String> map);

	/**
	 * Reads a StorageObject from the file specified
	 * 
	 * @param path
	 *            String path to file containing a StorageObject
	 * @return StorageObject of appropriate type read from file
	 * @throws StorageException
	 *             - if there is an error reading the file
	 */
	public StorageObject<E> readFromFile(String path) throws StorageException;

	/**
	 * Writes this StorageObject to the file specified, overwriting if it
	 * already exists.
	 * 
	 * @param path
	 *            String path to file to write to
	 * @throws StorageException
	 *             - if there is an error during writing
	 */
	public void writeToFile(String path) throws StorageException;

	/**
	 * Writes this StorageObject to the specified stream. Use this to append a
	 * StorageObject to a file, by opening the writer with the appropriate
	 * append flag.
	 * 
	 * @param writer
	 *            BufferedWriter to write this StorageObject to
	 * @throws StorageException
	 *             - if there is an error during writing
	 */
	public void writeToStream(BufferedWriter writer) throws StorageException;
}

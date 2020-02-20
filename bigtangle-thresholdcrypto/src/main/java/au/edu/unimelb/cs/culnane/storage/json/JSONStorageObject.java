/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.storage.json;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import au.edu.unimelb.cs.culnane.comms.json.JSONCommLayerMessage;
import au.edu.unimelb.cs.culnane.storage.Storable;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;
import au.edu.unimelb.cs.culnane.utils.IOUtils;
import au.edu.unimelb.cs.culnane.utils.IOUtils.EncodingType;

/**
 * ProtocolJSON extends the CommunicationLayer JSONMessage and implements the
 * StorageObject interface. As such, it provides a universal data object that
 * can be used for sending, receiving and storing data within the protocol.
 * 
 * @author Chris Culnane
 *
 */
public class JSONStorageObject extends JSONCommLayerMessage implements StorageObject<JSONStorageObject> {

	/**
	 * Constructs an empty ProtocolJSON object
	 */
	public JSONStorageObject() {
	}

	/**
	 * Constructs a new ProtocolJSON object from a byte array of a String
	 * containing JSON
	 * 
	 * @param bytes
	 *            byte array of String containing JSON
	 */
	public JSONStorageObject(byte[] bytes) {
		super(bytes);
	}

	/**
	 * Construct a ProtocolJSON from a subset of another ProtocolJSON. An array
	 * of strings is used to identify the keys that should be copied. Missing
	 * keys are ignored.
	 * 
	 * @param jo
	 *            A JSONObject
	 * @param names
	 *            An array of strings
	 */
	public JSONStorageObject(JSONObject jo, String[] names) {
		super(jo, names);
	}

	/**
	 * Construct a ProtocolJSON from a JSONTokener
	 * 
	 * @param x
	 *            A JSONTokener object containing the source string.
	 * @throws JSONException
	 *             If there is a syntax error in the source string or a
	 *             duplicated key.
	 */
	public JSONStorageObject(JSONTokener x) throws JSONException {
		super(x);

	}

	/**
	 * Construct a ProtocolJSON from a Map.
	 * 
	 * @param map
	 *            A map object that can be used to initialize the contents of
	 *            the JSONObject.
	 */
	public JSONStorageObject(Map<?, ?> map) {
		super(map);
	}

	/**
	 * Construct a ProtocolJSON from an Object using bean getters. It reflects
	 * on all of the public methods of the object. For each of the methods with
	 * no parameters and a name starting with "get" or "is" followed by an
	 * uppercase letter, the method is invoked, and a key and the value returned
	 * from the getter method are put into the new ProtocolJSON. The key is
	 * formed by removing the "get" or "is" prefix. If the second remaining
	 * character is not upper case, then the first character is converted to
	 * lower case. For example, if an object has a method named "getName", and
	 * if the result of calling object.getName() is "Larry Fine", then the
	 * ProtocolJSON will contain "name": "Larry Fine".
	 * 
	 * @param bean
	 *            An object that has getter methods that should be used to make
	 *            a JSONObject.
	 */
	public JSONStorageObject(Object bean) {
		super(bean);
	}

	/**
	 * Construct a ProtocolJSON from an Object, using reflection to find the
	 * public members. The resulting ProtocolJSON's keys will be the strings
	 * from the names array, and the values will be the field values associated
	 * with those keys in the object. If a key is not found or not visible, then
	 * it will not be copied into the new ProtocolJSON.
	 * 
	 * @param object
	 *            An object that has fields that should be used to make a
	 *            ProtocolJSON.
	 * @param names
	 *            An array of strings, the names of the fields to be obtained
	 *            from the object.
	 */
	public JSONStorageObject(Object object, String[] names) {
		super(object, names);
	}

	/**
	 * Constructs a new ProtocolJSON object from a string containing JSON
	 * 
	 * @param msgString
	 *            String of JSON
	 */
	public JSONStorageObject(String msgString) {
		super(msgString);
	}

	/**
	 * Construct a ProtocolJSON from a ResourceBundle.
	 * 
	 * @param baseName
	 *            The ResourceBundle base name.
	 * @param locale
	 *            The Locale to load the ResourceBundle for.
	 * @throws JSONException
	 *             If any JSONExceptions are detected.
	 */
	public JSONStorageObject(String baseName, Locale locale) throws JSONException {
		super(baseName, locale);
	}

	@Override
	public JSONStorageObject get() {
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.json.JSONObject#getBigInteger(java.lang.String)
	 */
	@Override
	public BigInteger getBigInteger(String key) throws JSONException {
		return new BigInteger(super.getString(key), 16);
	}

	@Override
	public List<BigInteger> getBigIntList(String id) {
		List<BigInteger> list = new ArrayList<BigInteger>();
		JSONArray arr = super.getJSONArray(id);
		for (int i = 0; i < arr.length(); i++) {
			list.add(new BigInteger(arr.getString(i), 16));
		}
		return list;
	}

	@Override
	public byte[] getBytes() {
		return toString().getBytes();
	}

	@Override
	public byte[] getBytes(String id) throws StorageException {
		try {
			String value = getString(id);
			return IOUtils.decodeData(EncodingType.BASE64, value);
		} catch (JSONException e) {
			throw new StorageException("Cannot read bytes", e);
		}

	}

	@Override
	public Iterator<String> getFields() {
		return this.keys();
	}

	@Override
	public int getInt(String id) {
		return super.getInt(id);

	}

	@Override
	public List<StorageObject<JSONStorageObject>> getList(String id) {
		List<StorageObject<JSONStorageObject>> list = new ArrayList<StorageObject<JSONStorageObject>>();
		JSONArray arr = super.getJSONArray(id);
		for (int i = 0; i < arr.length(); i++) {
			list.add(new JSONStorageObject(arr.getJSONObject(i).toString()));
		}
		return list;
	}

	@Override
	public Map<String, StorageObject<JSONStorageObject>> getMap(String id) {
		Map<String, StorageObject<JSONStorageObject>> map = new HashMap<String, StorageObject<JSONStorageObject>>();
		JSONStorageObject storedObj = new JSONStorageObject(getJSONObject(id).toString());
		Iterator<String> itr = storedObj.keys();
		while (itr.hasNext()) {
			String key = itr.next();
			map.put(key, storedObj.getStorageObject(key));
		}

		return map;
	}

	@Override
	public JSONStorageObject getNewStorageObject() {
		return new JSONStorageObject();
	}


	@Override
	public JSONStorageObject getStorageObject(String id) {
		return new JSONStorageObject(getJSONObject(id).toString());

	}

	@Override
	public List<String> getStringList(String id) {
		List<String> list = new ArrayList<String>();
		JSONArray arr = super.getJSONArray(id);
		for (int i = 0; i < arr.length(); i++) {
			list.add(arr.getString(i));
		}
		return list;
	}

	@Override
	public Map<String, String> getStringMap(String id) {
		Map<String, String> map = new HashMap<String, String>();
		JSONObject obj = getJSONObject(id);
		Iterator<String> itr = obj.keys();
		while (itr.hasNext()) {
			String key = itr.next();
			map.put(key, obj.getString(key));
		}
		return map;
	}


	@Override
	public JSONStorageObject load(byte[] data) {
		return new JSONStorageObject(new String(data));
	}

	@Override
	public void set(String id, Storable storable) {
		StorageObject<JSONStorageObject> store = this.getNewStorageObject();
		storable.storeInStorageObject(store);
		this.set(id, store);
	}

	@Override
	public void set(String id, StorageObject<JSONStorageObject> storageObj) {
		super.put(id, storageObj.get());

	}

	@Override
	public void set(String id, String value) {
		super.put(id, value);

	}

	@Override
	public void set(String id, BigInteger value) {
		super.put(id, value.toString(16));
	}

	@Override
	public void setBigIntList(String id, List<BigInteger> list) {
		JSONArray arr = new JSONArray();
		for (BigInteger b : list) {
			arr.put(b.toString(16));
		}
		put(id, arr);

	}

	@Override
	public void set(String id, byte[] value) {
		super.put(id, IOUtils.encodeData(EncodingType.BASE64, value));

	}

	@Override
	public void set(String id, int value) {
		super.put(id, value);

	}

	public void set(String id, List<StorageObject<JSONStorageObject>> list) {
		super.put(id, list);

	}

	@Override
	public void set(String id, Map<String, StorageObject<JSONStorageObject>> storeMap) {
		JSONStorageObject protMap = new JSONStorageObject();
		Iterator<Entry<String, StorageObject<JSONStorageObject>>> itr = storeMap.entrySet().iterator();
		while (itr.hasNext()) {
			Entry<String, StorageObject<JSONStorageObject>> entry = itr.next();
			protMap.set(entry.getKey(), entry.getValue());
		}
		super.put(id, protMap);
	}

	@Override
	public void setStringList(String id, List<String> list) {
		super.put(id, list);

	}

	@Override
	public void setStringMap(String id, Map<String, String> map) {
		super.put(id, map);

	}

	@Override
	public StorageObject<JSONStorageObject> readFromFile(String path) throws StorageException {
		try {
			return new JSONStorageObject(IOUtils.readJSONObjectFromFile(path).toString());
		} catch (IOException e) {
			throw new StorageException("Exception reading from file", e);
		}
	}

	@Override
	public void writeToFile(String path) throws StorageException {
		try {
			IOUtils.writeToFile(path, this);
		} catch (IOException e) {
			throw new StorageException("Exception reading from file", e);
		}

	}

	@Override
	public void writeToStream(BufferedWriter writer) throws StorageException {
		try {
			writer.write(this.toString());
		} catch (IOException e) {
			throw new StorageException("Exception writing to stream", e);
		}

	}

}

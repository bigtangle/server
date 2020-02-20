/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.storage.stores;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.protocols.ProtocolStore;
import au.edu.unimelb.cs.culnane.storage.StorageObject;

/**
 * ProtocolHashStorage is a ProtocolStorage provider that is based on a
 * serializable HashMap. Data is initially held in memory and only serialized or
 * deserialized on request. The underlying storage is JSON based, but it can
 * handle any type of StorageObject, with the data being encoded and decoded
 * from bytes. The underlying byte data will be stored as Base64 encoded
 * strings.
 * 
 * @author Chris Culnane
 *
 * @param <E>
 */
public class HashStore<E extends StorageObject<E>> implements ProtocolStore<E> {
	class AutoStore implements Runnable {

		private HashStore<E> hashStore;

		public AutoStore(HashStore<E> hStore) {
			hashStore = hStore;
		}

		@Override
		public void run() {
			try {
				hashStore.clearTranscriptToFile();
				hashStore.writeStorageToFile(storePath);
				scheduleAutoStore();
			} catch (IOException e) {
				logger.error("Schedule clearTranscriptToFile threw and Exception, will not reschedule", e);
			}

		}

	}

	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(HashStore.class);
	/**
	 * ConcurrentHashMap that acts as an in-memory store of keys and objects
	 */
	private ConcurrentHashMap<String, StorageObject<E>> map = new ConcurrentHashMap<String, StorageObject<E>>();

	/**
	 * Transcript Queue, that records individual actions
	 */
	private Queue<StorageObject<E>> transcript = new ConcurrentLinkedQueue<StorageObject<E>>();

	/**
	 * Scheduler to schedule automatic writes of the data to disk
	 */
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	/**
	 * Base object for all StoreObjects, we will use this to generate new
	 * StoreObjects
	 */
	private StorageObject<E> baseObject;

	/**
	 * String path to where the transcript should be saved
	 */
	private String transcriptPath = null;

	/**
	 * String path to where the data should be stored
	 */
	private String storePath = null;

	/**
	 * long milliseconds between automatic store operations
	 */
	private long autoStoreInterval = 0;

	/**
	 * ScheduledFuture to hold reference to next scheduled write
	 */
	private ScheduledFuture<?> scheduleFuture = null;

	/**
	 * Constructs a new empty ProtocolHashStorage object
	 */
	public HashStore(StorageObject<E> baseObject) {
		this.baseObject = baseObject;
	}

	/**
	 * Constructs a new empty ProtocolHashStorage object
	 */
	public HashStore(StorageObject<E> baseObject, String transcriptPath, String storePath, long autoStoreInterval) {
		this.baseObject = baseObject;
		this.transcriptPath = transcriptPath;
		this.storePath = storePath;
		this.autoStoreInterval = autoStoreInterval;
		if (this.autoStoreInterval > 0) {
			scheduleAutoStore();
		}
	}

	@Override
	public void addToTranscript(StorageObject<E> message) {
		transcript.add(message);
	}

	/**
	 * Writes the contents of the transcript queue to a file, clearing the queue
	 * in the process. This will use the default transcriptPath name. If no
	 * transcriptPath has been set an exception will be thrown.
	 * 
	 * @throws IOException
	 *             - if an error occurs during writing
	 */
	private void clearTranscriptToFile() throws IOException {
		if (transcriptPath != null) {
			this.clearTranscriptToFile(transcriptPath, true);
			this.writeStorageToFile(storePath);
		} else {
			logger.error("Cannot clearTranscriptToFile, not path set");
			throw new IOException("clearTranscriptToFile called without path");
		}
	}

	/**
	 * Writes the contents of the transcript queue to a file, clearing the queue
	 * in the process. Will write to the specified filePath. If append is true
	 * it will append to the existing file.
	 * 
	 * @param filePath
	 *            String path to write transcript to
	 * @param append
	 *            boolean, set to true to append to a file, false will overwrite
	 *            any existing file
	 * @throws IOException
	 *             - if an error occurs during writing
	 */
	public void clearTranscriptToFile(String filePath, boolean append) throws IOException {

		File transcriptFile = new File(filePath);
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new FileWriter(transcriptFile, append));
			StorageObject<E> transcriptEntry;
			while ((transcriptEntry = this.transcript.poll()) != null) {
				transcriptEntry.writeToStream(bw);
				bw.newLine();
			}
		} finally {
			if (bw != null) {
				bw.close();
			}
		}

	}

	/**
	 * Writes the storage data to file and stops the automatic writing of data.
	 * This should be called during the shutdown process of a protocol to ensure
	 * all data is written out and that a write operation will not be running
	 * when the JVM exits, which could corrupt the file.
	 * 
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws IOException
	 */
	public void flushStorageAndStopAutoStore() throws InterruptedException, ExecutionException, IOException {
		if (scheduleFuture != null && !scheduleFuture.cancel(false)) {
			scheduleFuture.get();
		}
		if (this.storePath == null || this.transcriptPath == null) {
			throw new IOException("Cannot flush store because no paths were set - call write to file methods manually");
		} else {
			this.clearTranscriptToFile();
			writeStorageToFile(storePath);
		}
	}

	@Override
	public StorageObject<E> get(String id) {
		return map.get(id);
	}

	@Override
	public StorageObject<E> getNewStorageObject() {
		return baseObject.getNewStorageObject();
	}

	/**
	 * Loads ProtocolHashStorage data from a file containing a StorageObject.
	 * 
	 * @param filePath
	 *            String path to file to load data from
	 * @throws IOException
	 *             if an error occurs during reading
	 */
	public void loadStorageFromFile(String filePath) throws IOException {
		StorageObject<E> input = this.baseObject.getNewStorageObject();
		input = input.readFromFile(filePath);
		Iterator<String> itr = input.getFields();
		while (itr.hasNext()) {
			String key = itr.next();
			this.map.put(key, input.getStorageObject(key));
		}
	}

	/**
	 * Schedules the next auto store operationF
	 */
	private void scheduleAutoStore() {
		logger.info("Scheduled next AutoStore in {} second", (autoStoreInterval / 1000));
		scheduleFuture = this.scheduler.schedule(new AutoStore(this), this.autoStoreInterval, TimeUnit.MILLISECONDS);

	}

	@Override
	public void store(String id, StorageObject<E> obj) {
		map.put(id, obj);
	}

	/**
	 * Called to write the in-memory data to file. Enumerates through the list
	 * of keys in the HashMap, reads the associated byte data - converting it to
	 * Base64 - and stores as a JSONObject within a JSONArray. Writes the
	 * completed JSONArray to the file specified.
	 * 
	 * @param filePath
	 *            String path to write data to
	 * @throws IOException
	 *             if an exception occurs during writing data
	 */
	public void writeStorageToFile(String filePath) throws IOException {
		Enumeration<String> keys = map.keys();
		StorageObject<E> output = this.baseObject.getNewStorageObject();

		while (keys.hasMoreElements()) {
			String key = keys.nextElement();
			output.set(key, map.get(key));
		}
		output.writeToFile(filePath);
	}

}

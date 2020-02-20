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

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import au.edu.unimelb.cs.culnane.crypto.P_Length;
import au.edu.unimelb.cs.culnane.crypto.ZpStarSafePrimeGroupRFC3526;
import au.edu.unimelb.cs.culnane.crypto.exceptions.GroupException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStore;
import au.edu.unimelb.cs.culnane.protocols.runners.SocketProtocolRunner;
import au.edu.unimelb.cs.culnane.protocols.runners.SocketProtocolRunnerException;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;
import au.edu.unimelb.cs.culnane.storage.json.JSONStorageObject;
import au.edu.unimelb.cs.culnane.storage.stores.HashStore;
import au.edu.unimelb.cs.culnane.utils.IOUtils;

/**
 * ThresholdDecryptionRunner is a SocketProtocolRunner that is used to run a
 * protocol. The SocketProtocolRunner encapsulates a server to run the actual
 * protocol.
 * 
 * @author Chris Culnane
 *
 */
public class ThresholdDecryptionRunner extends SocketProtocolRunner {

	private ZpStarSafePrimeGroupRFC3526 group = new ZpStarSafePrimeGroupRFC3526();
	private JSONStorageObject keyData;

	/**
	 * Create a new ThresholdDecryptionRunners with the specified id and port.
	 * Connects to peers defined in peerFilePath (JSONArray).
	 * 
	 * @param id
	 *            String id of server/peer
	 * @param port
	 *            int port to listen on
	 * @param peerFilePath
	 *            String path to file containing JSONArray of peer definitions
	 * @param useSSL
	 *            boolean set to true to use SecureSockets, false uses standard
	 *            Sockets
	 * @param storage
	 *            ProtocolStorage object to store data in
	 * @throws SocketProtocolRunnerException
	 * @throws GroupException
	 * @throws IOException
	 */
	public ThresholdDecryptionRunner(String id, int port, String peerFilePath, boolean useSSL,
			ProtocolStore<JSONStorageObject> storage)
			throws SocketProtocolRunnerException, GroupException, IOException {
		super(id, port, peerFilePath, useSSL, storage);
		group.initialise(P_Length.P2046);
		keyData = new JSONStorageObject(IOUtils.readStringFromFile("./" + this.id.toLowerCase() + ".js"));

	}

	public static void main(String[] args) throws NumberFormatException, IOException, ProtocolExecutionException,
			GroupException, SocketProtocolRunnerException {
		HashStore<JSONStorageObject> storage = new HashStore<JSONStorageObject>(new JSONStorageObject());
		ThresholdDecryptionRunner thresholdDecrypt = new ThresholdDecryptionRunner(args[0], Integer.parseInt(args[1]),
				args[2], true, storage);
		JSONStorageObject cipher = new JSONStorageObject(IOUtils.readStringFromFile("./testcipher.json"));
		thresholdDecrypt.doDecryption("round1", 2, cipher);
		// thresholdDecrypt.runProtocol();

	}

	private ThresholdDecryptionProtocol<JSONStorageObject> tdp;

	public void removeProtocol() {
		if (tdp != null) {
			this.server.removeProtocol(tdp);
		}
	}

	public void doDecryption(String id, int threshold, JSONStorageObject cipher)
			throws StorageException, GroupException, ProtocolExecutionException {
		String decryptionUUID = id;
		tdp = new ThresholdDecryptionProtocol<JSONStorageObject>(decryptionUUID, group, cipher, keyData, threshold);
		runProtocol();
	}

	@Override
	public void runProtocol() throws ProtocolExecutionException {

		try {
			Future<Boolean> future;
			// String decryptionUUID = "round1";
			// JSONStorageObject cipher = new
			// JSONStorageObject(IOUtils.readStringFromFile("./testcipher.json"));
			// ThresholdDecryptionProtocol<JSONStorageObject> dkgp = new
			// ThresholdDecryptionProtocol<JSONStorageObject>(
			// decryptionUUID, group, cipher, keyData, 2);
			// server.submitProtocol(dkgp);
			// server.startProtocol(dkgp.getId());
			// future = server.getProtocolFuture(dkgp.getId());
			server.submitProtocol(tdp);

			server.startProtocol(tdp.getId());
			future = server.getProtocolFuture(tdp.getId());

			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ProtocolExecutionException("Exception running Threshold Decryption", e);
		}

	}

}

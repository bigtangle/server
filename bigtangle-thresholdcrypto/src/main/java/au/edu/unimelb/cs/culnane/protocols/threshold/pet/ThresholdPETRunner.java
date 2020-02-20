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

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import au.edu.unimelb.cs.culnane.crypto.Group;
import au.edu.unimelb.cs.culnane.crypto.P_Length;
import au.edu.unimelb.cs.culnane.crypto.ZpStarSafePrimeGroupRFC3526;
import au.edu.unimelb.cs.culnane.crypto.exceptions.GroupException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStore;
import au.edu.unimelb.cs.culnane.protocols.runners.SocketProtocolRunner;
import au.edu.unimelb.cs.culnane.protocols.runners.SocketProtocolRunnerException;
import au.edu.unimelb.cs.culnane.storage.json.JSONStorageObject;
import au.edu.unimelb.cs.culnane.storage.stores.HashStore;
import au.edu.unimelb.cs.culnane.utils.IOUtils;

/**
 * ThresholdPETRunner is a SocketProtocolRunner that is used to run a protocol.
 * The SocketProtocolRunner encapsulates a server to run the actual protocol.
 * 
 * @author Chris Culnane
 *
 */
public class ThresholdPETRunner extends SocketProtocolRunner {

	private Group group;

	/**
	 * Create a new ThresholdPETRunner with the specified id and port. Connects
	 * to peers defined in peerFilePath (JSONArray).
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
	 * @param group
	 *            Crypto group to perform operations on
	 * @throws SocketProtocolRunnerException
	 */
	public ThresholdPETRunner(String id, int port, String peerFilePath, boolean useSSL,
			ProtocolStore<JSONStorageObject> storage, Group group) throws SocketProtocolRunnerException {
		super(id, port, peerFilePath, useSSL, storage);
		this.group = group;
	}

	public static void main(String[] args) throws NumberFormatException, IOException, ProtocolExecutionException,
			GroupException, SocketProtocolRunnerException {
		HashStore<JSONStorageObject> storage = new HashStore<JSONStorageObject>(new JSONStorageObject());
		ZpStarSafePrimeGroupRFC3526 group = new ZpStarSafePrimeGroupRFC3526();
		group.initialise(P_Length.P2046);
		ThresholdPETRunner thresholdDecrypt = new ThresholdPETRunner(args[0], Integer.parseInt(args[1]), args[2], true,
				storage, group);
		thresholdDecrypt.runProtocol();

	}

	@Override
	public void runProtocol() throws ProtocolExecutionException {
		try {
			JSONStorageObject keyData = new JSONStorageObject(
					IOUtils.readStringFromFile("./" + this.id.toLowerCase() + ".js"));
			JSONStorageObject cipherOne = new JSONStorageObject(IOUtils.readStringFromFile("./testcipher.json"));
			JSONStorageObject cipherTwo = new JSONStorageObject(IOUtils.readStringFromFile("./testcipher2.json"));
			Future<Boolean> future;
			String decryptionUUID = "round1";
			ThresholdPETProtocol<JSONStorageObject> dkgp = new ThresholdPETProtocol<JSONStorageObject>(decryptionUUID,
					group, cipherOne, cipherTwo, keyData, 2);
			server.submitProtocol(dkgp);
			server.startProtocol(dkgp.getId());
			future = server.getProtocolFuture(dkgp.getId());

			future.get();
		} catch (InterruptedException | IOException | GroupException | ExecutionException e) {
			throw new ProtocolExecutionException("Exception running PET protocol", e);
		}

	}

}

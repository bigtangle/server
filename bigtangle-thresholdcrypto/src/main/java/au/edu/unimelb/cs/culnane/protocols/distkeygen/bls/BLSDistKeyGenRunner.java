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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import au.edu.unimelb.cs.culnane.crypto.exceptions.GroupException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStore;
import au.edu.unimelb.cs.culnane.protocols.runners.SocketProtocolRunner;
import au.edu.unimelb.cs.culnane.protocols.runners.SocketProtocolRunnerException;
import au.edu.unimelb.cs.culnane.storage.json.JSONStorageObject;
import au.edu.unimelb.cs.culnane.storage.stores.HashStore;

/**
 * BLSDistKeyGenRunner is a SocketProtocolRunner that is used to run a protocol.
 * The SocketProtocolRunner encapsulates a server to run the actual protocol.
 * 
 * @author Chris Culnane
 *
 */
public class BLSDistKeyGenRunner extends SocketProtocolRunner {
	/**
	 * Create a new BLSDistKeyGenRunner with the specified id and port. Connects
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
	 * @throws SocketProtocolRunnerException
	 */
	public BLSDistKeyGenRunner(String id, int port, String peerFilePath, boolean useSSL,
			ProtocolStore<JSONStorageObject> storage) throws SocketProtocolRunnerException {
		super(id, port, peerFilePath, useSSL, storage);
	}

	public static void main(String[] args)
			throws NumberFormatException, SocketProtocolRunnerException, ProtocolExecutionException, IOException {
		HashStore<JSONStorageObject> storage = new HashStore<JSONStorageObject>(new JSONStorageObject(),
				"./" + args[0] + "transcript.txt", "./" + args[0] + "storage.txt", 2000);
		BLSDistKeyGenRunner dkgr = new BLSDistKeyGenRunner(args[0], Integer.parseInt(args[1]), args[2], true, storage);
		dkgr.runProtocol();
		try {
			storage.flushStorageAndStopAutoStore();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void runProtocol() throws ProtocolExecutionException {
		try {
			JSONStorageObject keyData = new JSONStorageObject();
			Future<Boolean> future;
			BLSDistKeyGenProtocol<JSONStorageObject> dkgp = new BLSDistKeyGenProtocol<JSONStorageObject>("BLSKeyGen",
					keyData);
			server.submitProtocol(dkgp);
			server.startProtocol(dkgp.getId());
			future = server.getProtocolFuture(dkgp.getId());

			future.get();
			
			keyData.writeToFile("./" + this.id.toLowerCase() + "_bls.js");
			
			
			System.out.println("keydata:" + keyData);
		} catch (InterruptedException | ExecutionException | GroupException | IOException e) {
			throw new ProtocolExecutionException("Exception running Distributed Key Gen", e);
		}

	}

}

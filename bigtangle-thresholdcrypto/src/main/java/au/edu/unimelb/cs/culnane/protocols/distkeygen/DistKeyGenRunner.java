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

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import au.edu.unimelb.cs.culnane.crypto.exceptions.GroupException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStore;
import au.edu.unimelb.cs.culnane.protocols.runners.SocketProtocolRunner;
import au.edu.unimelb.cs.culnane.protocols.runners.SocketProtocolRunnerException;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;
import au.edu.unimelb.cs.culnane.storage.json.JSONStorageObject;
import au.edu.unimelb.cs.culnane.storage.stores.HashStore;

/**
 * Protocol Runner for the Distributed Key Generation Protocol. This starts a
 * ProtocolServer and runs the protocol in it.
 * 
 * @author Chris Culnane
 *
 */
public class DistKeyGenRunner extends SocketProtocolRunner {

	/**
	 * int threshold for protocol
	 */
	private int threshold;

	/**
	 * Creates a DistributedKeyGenRunner with the specified id and listening on
	 * the specified port. It will create channels for the Peers listed in the
	 * peerFilePath.
	 * 
	 * @param id
	 *            String with peer id
	 * @param port
	 *            Integer of port to listen on
	 * @param peerFilePath
	 *            String path to peer connection information file
	 * @param useSSL
	 *            boolean set to true to use Secure Sockets, false to use
	 *            Standard sockets
	 * @param storage
	 *            ProtocolStorage to use for data storage
	 * @param threshold
	 *            int threshold
	 * @throws SocketProtocolRunnerException
	 */
	public DistKeyGenRunner(String id, int port, String peerFilePath, boolean useSSL,
			ProtocolStore<JSONStorageObject> storage, int threshold) throws SocketProtocolRunnerException {
		super(id, port, peerFilePath, useSSL, storage);
		this.threshold = threshold;
	}

	public static void main(String[] args)
			throws NumberFormatException, SocketProtocolRunnerException, ProtocolExecutionException, IOException {
		HashStore<JSONStorageObject> storage = new HashStore<JSONStorageObject>(new JSONStorageObject());
		DistKeyGenRunner dkgr = new DistKeyGenRunner(args[0], Integer.parseInt(args[1]), args[2], true, storage,2);
		dkgr.runProtocol();
		storage.writeStorageToFile("./" + args[0] + "storage.txt");
	}

	@Override
	public void runProtocol() throws ProtocolExecutionException {
		try {
			StorageObject<?> keyData = new JSONStorageObject();
			Future<Boolean> future;
			DistKeyGenProtocol<JSONStorageObject> dkgp = new DistKeyGenProtocol<JSONStorageObject>("FirstKeyGen",
					keyData, threshold);
			server.submitProtocol(dkgp);
			server.startProtocol(dkgp.getId());
			future = server.getProtocolFuture(dkgp.getId());

			future.get();
			keyData.writeToFile("./" + this.id.toLowerCase() + ".js");
		} catch (InterruptedException | ExecutionException | GroupException | StorageException e) {
			throw new ProtocolExecutionException("Exception running Distributed Key Gen", e);
		}

	}

}

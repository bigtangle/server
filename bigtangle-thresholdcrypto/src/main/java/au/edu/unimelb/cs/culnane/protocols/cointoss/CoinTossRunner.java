/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.cointoss;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStore;
import au.edu.unimelb.cs.culnane.protocols.runners.SocketProtocolRunner;
import au.edu.unimelb.cs.culnane.protocols.runners.SocketProtocolRunnerException;
import au.edu.unimelb.cs.culnane.storage.json.JSONStorageObject;
import au.edu.unimelb.cs.culnane.storage.stores.HashStore;

/**
 * CoinTossRunner is a SocketProtocolRunner that is used to run a protocol. The
 * SocketProtocolRunner encapsulates a server to run the actual protocol.
 * 
 * @author Chris Culnane
 *
 */
public class CoinTossRunner extends SocketProtocolRunner {

	/**
	 * Constructs a new CoinTossRunner with the relevant id, and port number.
	 * 
	 * @param id
	 *            String id of this peer
	 * @param port
	 *            integer port to listen on
	 * @param peerFilePath
	 *            String path to JSONArray file containing other peers to
	 *            connect to
	 * @param useSSL
	 *            boolean true to use SecureSockets, false to use standard
	 *            sockets
	 * @param storage
	 *            ProtocolStorage object to use for storing data
	 * @throws SocketProtocolRunnerException
	 */
	public CoinTossRunner(String id, int port, String peerFilePath, boolean useSSL,
			ProtocolStore<JSONStorageObject> storage) throws SocketProtocolRunnerException {
		super(id, port, peerFilePath, useSSL, storage);
	}

	public static void main(String[] args)
			throws NumberFormatException, SocketProtocolRunnerException, ProtocolExecutionException, IOException {
		HashStore<JSONStorageObject> storage = new HashStore<JSONStorageObject>(new JSONStorageObject());
		CoinTossRunner ctr = new CoinTossRunner(args[0], Integer.parseInt(args[1]), args[2], true, storage);
		ctr.runProtocol();
		storage.writeStorageToFile("./" + args[0] + "storage.txt");
	}

	@Override
	public void runProtocol() throws ProtocolExecutionException {
		CoinTossProtocol<JSONStorageObject> ctp = new CoinTossProtocol<JSONStorageObject>();
		server.submitProtocol(ctp);
		server.startProtocol(ctp.getId());
		Future<Boolean> future = server.getProtocolFuture(ctp.getId());
		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ProtocolExecutionException("Exception whilst running protocol", e);
		}

	}
}

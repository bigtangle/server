/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.pedersencommitment;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStore;
import au.edu.unimelb.cs.culnane.protocols.runners.SocketProtocolRunner;
import au.edu.unimelb.cs.culnane.protocols.runners.SocketProtocolRunnerException;
import au.edu.unimelb.cs.culnane.storage.json.JSONStorageObject;
import au.edu.unimelb.cs.culnane.storage.stores.HashStore;

/**
 * PedersenRunner is a SocketProtocolRunner that is used to run a protocol. The
 * SocketProtocolRunner encapsulates a server to run the actual protocol.
 * 
 * @author Chris Culnane
 *
 */
public class PedersenRunner extends SocketProtocolRunner {
	private boolean createCommit=false;
	/**
	 * Create a new PedersenRunner with the specified id and port. Connects to
	 * peers defined in peerFilePath (JSONArray).
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
	 * @param commit
	 *            boolean that indicates whether to create or open a commit.
	 *            True means create
	 * @throws SocketProtocolRunnerException
	 */
	public PedersenRunner(String id, int port, String peerFilePath, boolean useSSL,
			ProtocolStore<JSONStorageObject> storage, boolean commit) throws SocketProtocolRunnerException {
		super(id, port, peerFilePath, useSSL, storage);
		createCommit=commit;
	}

	public static void main(String[] args)
			throws NumberFormatException, IOException, ProtocolExecutionException, SocketProtocolRunnerException {
		HashStore<JSONStorageObject> storage = new HashStore<JSONStorageObject>(new JSONStorageObject());
		File storageFile = new File("./" + args[0] + "storage.txt");
		if (storageFile.exists()) {
			storage.loadStorageFromFile("./" + args[0] + "storage.txt");
		}
		PedersenRunner pedRunner = new PedersenRunner(args[0], Integer.parseInt(args[1]), args[2], true, storage,Boolean.parseBoolean(args[3]));
		pedRunner.runProtocol();
		storage.writeStorageToFile("./" + args[0] + "storage.txt");
		storage.clearTranscriptToFile("./" + args[0] + "transcript.txt", false);
	}

	@Override
	public void runProtocol() throws ProtocolExecutionException {
		
		Future<Boolean> future;
		if (createCommit) {
			
			BigInteger randomValue = new BigInteger(256, new SecureRandom());
			

			PedersenCommitmentProtocol<JSONStorageObject> pcp = new PedersenCommitmentProtocol<JSONStorageObject>(
					"keyGenOne", randomValue);
			server.submitProtocol(pcp);
			server.startProtocol(pcp.getId());
			future = server.getProtocolFuture(pcp.getId());
		} else {
			PedersenCommitmentOpeningProtocol<JSONStorageObject> pop = new PedersenCommitmentOpeningProtocol<JSONStorageObject>(
					"keyGenOne");
			server.submitProtocol(pop);
			server.startProtocol(pop.getId());
			future = server.getProtocolFuture(pop.getId());

		}
		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ProtocolExecutionException("Exception running Pedersen Protocol", e);

		}
	}

}

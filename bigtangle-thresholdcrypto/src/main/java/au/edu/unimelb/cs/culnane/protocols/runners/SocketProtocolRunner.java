/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.runners;

import java.io.IOException;

import javax.net.ssl.SSLContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.comms.layers.socket.SocketLayer;
import au.edu.unimelb.cs.culnane.comms.layers.socket.SocketLayerChannel;
import au.edu.unimelb.cs.culnane.crypto.catools.CertTools;
import au.edu.unimelb.cs.culnane.crypto.exceptions.SSLInitException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolServer;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStore;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.json.JSONStorageObject;
import au.edu.unimelb.cs.culnane.storage.json.JSONStorageObjectConstructor;
import au.edu.unimelb.cs.culnane.storage.stores.HashStore;
import au.edu.unimelb.cs.culnane.utils.IOUtils;

/**
 * Abstract Socket Protocol runner used as a base for running protocols over
 * Socket based communication channels. Uses ProtocolJSON storage objects for
 * communication.
 * 
 * @author Chris Culnane
 *
 */
public abstract class SocketProtocolRunner {

	/**
	 * Protocol Server
	 */
	protected ProtocolServer<JSONStorageObject> server;
	/**
	 * Protocol Storage
	 */
	protected ProtocolStore<JSONStorageObject> storage;

	/**
	 * String id of server
	 */
	protected String id;

	/**
	 * Create a new SocketProtocolRunner with the specified id and port.
	 * Connects to peers defined in peerFilePath (JSONArray).
	 * 
	 * @param id
	 *            String id of server/peer
	 * @param port
	 *            int port to listen on
	 * @param peerFilePath
	 *            String path to file containin JSONArray of peer definitions
	 * @param useSSL
	 *            boolean set to true to use SecureSockets, false uses standard
	 *            Sockets
	 * @param storage
	 *            ProtocolStorage object to store data in
	 * @throws SocketProtocolRunnerException
	 */
	public SocketProtocolRunner(String id, int port, String peerFilePath, boolean useSSL,
			ProtocolStore<JSONStorageObject> storage) throws SocketProtocolRunnerException {
		SocketLayer<JSONStorageObject> comms;
		try {
			this.id = id;
			JSONArray connections = IOUtils.readJSONArrayFromFile(peerFilePath);
			if (useSSL) {
				SSLContext ctx = CertTools.initSSLKeys(id);
				comms = new SocketLayer<JSONStorageObject>(new JSONStorageObjectConstructor(), port, ctx.getServerSocketFactory(),
						true);
				comms.start();
				connectToAll(id, connections, comms, ctx, true);
			} else {
				comms = new SocketLayer<JSONStorageObject>(new JSONStorageObjectConstructor(), port);
				comms.start();
				connectToAll(id, connections, comms);
			}
			if (storage == null) {
				this.storage = new HashStore<JSONStorageObject>(new JSONStorageObject());
			}else{
				this.storage = storage;
			}
			
			server = new ProtocolServer<JSONStorageObject>(id, comms, this.storage);

		} catch (SSLInitException | IOException e) {
			throw new SocketProtocolRunnerException("Exception starting SocketProtocol Runner", e);
		}
	}

	/**
	 * Runs the protocol, stepping through each protocol step
	 * 
	 * @throws ProtocolExecutionException
	 *             if an error occurs running any of the protocol steps
	 */
	public abstract void runProtocol() throws ProtocolExecutionException;

	/**
	 * Creates a Socket based Channel with the specified peer connection info
	 * 
	 * @param conninfo
	 *            JSONObject containing peer connection information
	 * @return SocketLayerChannel to use for communication
	 * @throws IOException
	 */
	public static <I extends CommunicationLayerMessage<?> & StorageObject<I>> SocketLayerChannel<I> createChannel(
			JSONObject conninfo) throws IOException {
		SocketLayerChannel<I> channel = new SocketLayerChannel<I>(conninfo.getString("id"), conninfo.getString("host"),
				conninfo.getInt("port"));
		channel.open();
		return channel;
	}

	/**
	 * Connects to all peers listed in the JSONArray, adding each channel to the
	 * underlying communication layer
	 * 
	 * @param myId
	 *            String of my id - to prevent us connection to ourselves
	 * @param connectionInfo
	 *            JSONArray containing a JSONObject for each peer with
	 *            connection information
	 * @param comms
	 *            Socket based Communication Layer
	 * @throws JSONException
	 * @throws IOException
	 */
	public static <I extends CommunicationLayerMessage<?> & StorageObject<I>> void connectToAll(String myId,
			JSONArray connectionInfo, SocketLayer<I> comms) throws JSONException, IOException {
		for (int i = 0; i < connectionInfo.length(); i++) {
			if (!connectionInfo.getJSONObject(i).getString("id").equals(myId)) {
				comms.addChannel(createChannel(connectionInfo.getJSONObject(i)));
			}
		}
	}

	/**
	 * Creates a Socket based secure Channel with the specified peer connection
	 * info
	 * 
	 * @param conninfo
	 *            JSONObject containing peer connection information
	 * @param ctx
	 *            SSLContext to use for secure socket creation
	 * @param requireClientAuth
	 *            boolean true if mutual authentication, false if not
	 * @return SocketLayerChannel to use for communication
	 * @throws IOException
	 */
	public static SocketLayerChannel<JSONStorageObject> createChannel(JSONObject conninfo, SSLContext ctx,
			boolean requireClientAuth) throws IOException {
		SocketLayerChannel<JSONStorageObject> channel = new SocketLayerChannel<JSONStorageObject>(conninfo.getString("id"),
				conninfo.getString("host"), conninfo.getInt("port"), ctx.getSocketFactory(), requireClientAuth);
		channel.open();
		return channel;
	}

	/**
	 * Connects to all peers listed in the JSONArray using Secure channels,
	 * adding each channel to the underlying communication layer
	 * 
	 * @param myId
	 *            String of my id - to prevent us connection to ourselves
	 * @param connectionInfo
	 *            JSONArray containing a JSONObject for each peer with
	 *            connection information
	 * @param comms
	 *            Socket based Communication Layer
	 * @param ctx
	 *            SSLContext to use for secure socket creation
	 * @param requireClientAuth
	 *            boolean true if mutual authentication, false if not
	 * @throws JSONException
	 * @throws IOException
	 */
	public static void connectToAll(String myId, JSONArray connectionInfo, SocketLayer<JSONStorageObject> comms,
			SSLContext ctx, boolean requireClientAuth) throws JSONException, IOException {
		for (int i = 0; i < connectionInfo.length(); i++) {
			if (!connectionInfo.getJSONObject(i).getString("id").equals(myId)) {
				comms.addChannel(createChannel(connectionInfo.getJSONObject(i), ctx, requireClientAuth));
			}
		}
	}

}
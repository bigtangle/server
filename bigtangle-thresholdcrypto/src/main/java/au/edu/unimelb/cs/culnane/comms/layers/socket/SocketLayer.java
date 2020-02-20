/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.comms.layers.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayer;
import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessageConstructor;
import au.edu.unimelb.cs.culnane.comms.exceptions.CommunicationLayerMessageException;

/**
 * SocketLayer that provides socket based communications. This layer can handle
 * both SecureSockets and standard Sockets, depending on whether a SocketFactory
 * is passed in
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            underlying CommunicationLayerMessage type
 */
public class SocketLayer<E extends CommunicationLayerMessage<?>> extends CommunicationLayer<E> {

	/**
	 * ServerSocket that will listen and receive connections
	 */
	private ServerSocket serverSocket;
	/**
	 * SocketListener that runs on a separate thread to monitor and process the
	 * ServerSocket connections
	 */
	private SocketListener listener;

	/**
	 * ExecutorService to run the listener on
	 */
	private ExecutorService listenerExec = Executors.newSingleThreadExecutor();

	/**
	 * ServerSocketFactory to use for creating ServerSockets.
	 */
	private ServerSocketFactory socketFactory;

	/**
	 * Creates a SocketLayer that will use the specified
	 * CommunicationLayerMessageConstructor. It will listen on the specified
	 * port. If socketFactory is not null it will use the SocketFactory to
	 * create a new ServerSocket. To use SecureSockets, pass in an instance of
	 * an SSLServerSocketFactory. To require client authentication on the
	 * SecureSockets set requireClientAuth to true - if this is set to true the
	 * ServerSocketFactory must be an instance of SSLServerSocketFactory.
	 * 
	 * @param msgConstructor
	 *            CommunicationLayerMessageConstructor to use for creating
	 *            message objects
	 * @param listenPort
	 *            int port to listen on for incoming connections
	 * @param socketFactory
	 *            ServerSocketFactory to use for creating the ServerSocket, pass
	 *            in an SSLServerSocketFactory to use SecureSockets
	 * @param requireClientAuth
	 *            boolean, set to true to require client authentication on
	 *            sockets
	 * @throws IOException
	 */
	public SocketLayer(CommunicationLayerMessageConstructor<E> msgConstructor, int listenPort,
			ServerSocketFactory socketFactory, boolean requireClientAuth) throws IOException {
		super(msgConstructor);
		this.socketFactory = socketFactory;

		if (socketFactory != null) {
			this.serverSocket = this.socketFactory.createServerSocket();
			if (requireClientAuth && this.socketFactory instanceof SSLServerSocketFactory) {
				((SSLServerSocket) serverSocket).setNeedClientAuth(true);
			} else {
				throw new IOException(
						"Socket factory does not support SecureSockets, cannot require client authentication");
			}
			serverSocket.setReuseAddress(true);
			InetSocketAddress sa = new InetSocketAddress(listenPort);
			serverSocket.bind(sa);
		} else {
			this.serverSocket = new ServerSocket(listenPort);

		}
		this.listener = new SocketListener(this.serverSocket, this);
	}

	/**
	 * Creates a SocketLayer without a factory using standard ServerSockets
	 * without SSL
	 * 
	 * @param msgConstructor
	 *            CommunicationLayerMessageConstructor to use for creating
	 *            message objects
	 * @param listenPort
	 *            int port to listen on for incoming connections
	 * @throws IOException
	 */
	public SocketLayer(CommunicationLayerMessageConstructor<E> msgConstructor, int listenPort) throws IOException {
		this(msgConstructor, listenPort, null, false);
	}

	/**
	 * Starts the SocketListener and prepares to receive incoming connections
	 */
	public void start() {
		listenerExec.execute(listener);
	}

	/**
	 * Shuts down the listener
	 */
	public void shutdown() {
		this.listener.shutdown();
		this.listenerExec.shutdownNow();
	}

	/**
	 * Receives a byte array message and constructs a message object of the
	 * appropriate type and passes it on to the super class.
	 * 
	 * @param msg
	 *            byte array of message
	 * @throws CommunicationLayerMessageException
	 */
	public void receive(byte[] msg) throws CommunicationLayerMessageException {
		super.messageReceived(super.getMsgConstructor().constructMessage(msg));
	}

}

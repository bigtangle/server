/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.comms;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import au.edu.unimelb.cs.culnane.comms.exceptions.CommunicationLayerMessageException;
import au.edu.unimelb.cs.culnane.comms.exceptions.UnknownChannelIdException;

/**
 * Abstract class to provide the basic functionality of the communication layer.
 * It provides methods for broadcasting, sending, and receiving messages. As
 * well as registering listeners for received messages. An implementation of a
 * communication layer should extend this class.
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            The type of the CommunicationLayerMessage
 */
public abstract class CommunicationLayer<E extends CommunicationLayerMessage<?>> {

	/**
	 * Concurrent list of registered listeners
	 */
	private CopyOnWriteArrayList<CommunicationLayerMessageListener<E>> listeners = new CopyOnWriteArrayList<CommunicationLayerMessageListener<E>>();
	/**
	 * Concurrent map of channels indexed by their Ids
	 */
	private ConcurrentHashMap<String, CommunicationLayerChannel<E>> channels = new ConcurrentHashMap<String, CommunicationLayerChannel<E>>();

	/**
	 * CommunicationLayerMessageConstructor - a utility class to convert a byte
	 * stream into the appropriate CommunicationLayerMessage type
	 */
	private CommunicationLayerMessageConstructor<E> msgConstructor;

	/**
	 * Create a new CommunicationLayer, specifying the
	 * CommunicationLayerMessageConstructor to use for incoming messages
	 * 
	 * @param msgConstructor
	 *            CommunicationLayerMessageConstructor that has the same type as
	 *            the CommunicationLayer
	 */
	public CommunicationLayer(CommunicationLayerMessageConstructor<E> msgConstructor) {
		this.msgConstructor = msgConstructor;
	}

	/**
	 * Broadcasts a messages to all channels
	 * 
	 * @param msg
	 *            message to broadcast
	 * @throws IOException
	 */
	public void broadcast(E msg) throws IOException {
		Enumeration<CommunicationLayerChannel<E>> enumeration = channels.elements();
		while (enumeration.hasMoreElements()) {
			enumeration.nextElement().send(msg);
		}
	}

	/**
	 * Broadcasts a raw byte message to all channels
	 * 
	 * @param msg
	 *            byte array message to broadcast
	 * @throws IOException
	 */
	public void broadcast(byte[] msg) throws IOException {
		Enumeration<CommunicationLayerChannel<E>> enumeration = channels.elements();
		while (enumeration.hasMoreElements()) {
			enumeration.nextElement().send(msg);
		}
	}

	/**
	 * Sends the specified message to the channel with the specified channelId.
	 * A corresponding channel must exist, otherwise an
	 * UnknownChannelIdException will be thrown.
	 * 
	 * @param msg
	 *            Message to send
	 * @param channelId
	 *            String id of the channel to send it to
	 * @throws UnknownChannelIdException
	 *             if no channel with channelId can be found
	 * @throws IOException
	 */
	public void send(E msg, String channelId) throws UnknownChannelIdException, IOException {
		CommunicationLayerChannel<E> channel = channels.get(channelId);
		if (channel != null) {
			channel.send(msg);
		} else {
			throw new UnknownChannelIdException("Cannot find channel in channels list");
		}
	}

	/**
	 * Sends the specified raw byte array message to the channel with the
	 * specified channelId. A corresponding channel must exist, otherwise an
	 * UnknownChannelIdException will be thrown.
	 * 
	 * @param msg
	 *            Message to send
	 * @param channelId
	 *            String id of the channel to send it to
	 * @throws UnknownChannelIdException
	 *             if no channel with channelId can be found
	 * @throws IOException
	 */
	public void send(byte[] msg, String channelId) throws UnknownChannelIdException, IOException {
		CommunicationLayerChannel<E> channel = channels.get(channelId);
		if (channel != null) {
			channel.send(msg);
		} else {
			throw new UnknownChannelIdException("Cannot find channel in channels list");
		}
	}

	/**
	 * Adds a CommunicateLayerMessageListener to the lister of listeners. It
	 * will receive a notification whenever a message is received.
	 * 
	 * @param listener
	 *            CommunicationLayerMessageListener to register for receive
	 *            events
	 */
	public void addMessageListener(CommunicationLayerMessageListener<E> listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	/**
	 * Removes the specified CommunicationLayerMessageListener from the list of
	 * listeners. If the listeners does not exists nothing happens. It is
	 * therefore safe to call this even when not certain if the listeners still
	 * exists.
	 * 
	 * @param listener
	 *            CommunicationLayerMessageListener to remove
	 */
	public void removeMessageListener(CommunicationLayerMessageListener<E> listener) {
		listeners.remove(listener);
	}

	/**
	 * Gets the CommunicationLayerMessageConstructor used by the communication
	 * layer
	 * 
	 * @return CommunicationLayerMessageConstructor used by this
	 *         CommunicationLayer
	 */
	public CommunicationLayerMessageConstructor<E> getMsgConstructor() {
		return this.msgConstructor;
	}

	/**
	 * Called when a message is received, notifies all listeners of the receipt
	 * of the message
	 * 
	 * @param msg
	 *            Message that has been received
	 * @throws CommunicationLayerMessageException
	 */
	public void messageReceived(E msg) throws CommunicationLayerMessageException {
		Iterator<CommunicationLayerMessageListener<E>> itr = listeners.iterator();
		while (itr.hasNext()) {
			itr.next().messageReceived(msg);
		}
	}

	/**
	 * Adds a CommunicationLayerChannel of the same type, if it does not already
	 * exist. It will be stored in the channel map, indexed by its Id.
	 * 
	 * @param endPoint
	 *            CommunicationLayerChannel to add
	 */
	public void addChannel(CommunicationLayerChannel<E> endPoint) {
		channels.putIfAbsent(endPoint.getId(), endPoint);
	}

	/**
	 * Removes and closes the specified channel.
	 * 
	 * @param endPointId
	 *            String id of channel to remove
	 * @throws IOException
	 */
	public void removeAndCloseChannel(String endPointId) throws IOException {
		CommunicationLayerChannel<E> channel = channels.remove(endPointId);
		if (channel != null) {
			channel.close();
		}
	}

	/**
	 * Gets the number of channels currently registered
	 * 
	 * @return integer of the number of channels
	 */
	public int getChannelCount() {
		return this.channels.size();
	}

	/**
	 * Gets a Set of String channelIds
	 * 
	 * @return Set<String> channelIds
	 */
	public Set<String> getChannelIds() {
		return this.channels.keySet();
	}
}

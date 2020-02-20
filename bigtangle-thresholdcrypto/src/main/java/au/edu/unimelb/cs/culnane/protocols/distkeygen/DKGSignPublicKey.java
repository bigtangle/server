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

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingDeque;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.catools.CertTools;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * Signs the public and broadcasts to other peers. Waits for all other peers to
 * send their signed keys and checks they are consistent.
 * 
 * @author Chris Culnane
 *
 */
public class DKGSignPublicKey<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(DKGSignPublicKey.class);

	/**
	 * Incoming Message Queue
	 */
	private LinkedBlockingDeque<E> incomingMessages = new LinkedBlockingDeque<E>();

	/**
	 * Static String STEP_ID
	 */
	public static final String STEP_ID = "DKG_SignPublicKey";

	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * String id of the key
	 */
	private String keyId;

	/**
	 * Static class containing message keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class MESSAGES {
		public static final String PUBLIC_KEY_SIG = "publicKeySig";
		public static final String PUBLIC_KEY_CERT = "publicKeyCert";
	}

	/**
	 * Static class containing storage keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class STORAGE {
		public static final String PUBLIC_KEY_SIGS = "publicKeySigs";
	}

	

	/**
	 * Construct a new DKGShareSecretKey protocol step, to share and receive
	 * secret key shares
	 * 
	 * @param keyId
	 *            String id of the key to share
	 * 
	 */
	public DKGSignPublicKey(String keyId) {
		this.keyId = keyId;
		
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", getStepId());

		StorageObject<E> publickeyData = protocol.getFromStorage(DKGCombinePublicKey.STEP_ID + ":" + keyId).get();

		StorageObject<E> mydata = this.protocol.getNewStorageObject();
		Map<String, StorageObject<E>> sigsReceived = new HashMap<String, StorageObject<E>>();

		BigInteger jointPublicKey = publickeyData.getBigInteger(DKGCombinePublicKey.STORAGE.JOINT_PUBLIC_KEY);

		//File ksFile = new File("./" + protocol.getServerId()+ ".jks");
		KeyStore ks = CertTools.loadKeyStore("./" + protocol.getServerId() + ".jks");
		Signature sig = Signature.getInstance("SHA256withRSA", new BouncyCastleProvider());
		sig.initSign((PrivateKey)ks.getKey(this.protocol.getServerId(), "".toCharArray()));
		sig.update(jointPublicKey.toByteArray());
		byte[] signature = sig.sign();
		StorageObject<E> shareMessage = this.protocol.getNewStorageObject();
		shareMessage.set(MESSAGES.PUBLIC_KEY_SIG, signature);
		shareMessage.set(MESSAGES.PUBLIC_KEY_CERT, ks.getCertificate(protocol.getServerId()).getEncoded());
		shareMessage.set(Protocol.MESSAGES.STEP, this.getStepId());
		
		this.protocol.sendBroadcast(shareMessage.get());
		boolean receivedAll = false;
		
		TreeSet<String> peerIds = new TreeSet<String>();
		peerIds.addAll(this.protocol.getPeerIds());

		ByteArrayInputStream bais;// = new ByteArrayInputStream(certHolder.getEncoded());
		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		
		
		while (!receivedAll) {
			try {
				E msg = this.incomingMessages.take();
				logger.info("Processing incoming message: {}", msg);
				String sender = msg.getString(Protocol.MESSAGES.SENDER);
				byte[] sendSignature = msg.getBytes(MESSAGES.PUBLIC_KEY_SIG);
				byte[] sendCertBytes = msg.getBytes(MESSAGES.PUBLIC_KEY_CERT);
				if (!sigsReceived.containsKey(sender)) {
					sigsReceived.put(sender, msg);
					bais = new ByteArrayInputStream(sendCertBytes);
					X509Certificate sendCert = (X509Certificate)certFactory.generateCertificate(bais);			
					PKIXCertPathValidatorResult certResult = CertTools.verifyCertificateAndGetKey(sendCert, ks);
					sig.initVerify(certResult.getPublicKey());
					sig.update(jointPublicKey.toByteArray());
					sendCert.checkValidity();
					if(!CertTools.getCertificateName(sendCert.getSubjectDN()).equals(sender)){
						throw new ProtocolExecutionException("Sender and Certificate do not match");
					}
					
					if(!sig.verify(sendSignature)){
						throw new ProtocolExecutionException("PublicKey Signature Failed Verification");
					}else{
						logger.info("Signature from {} passed verification",sender);
					}
					
					if (!peerIds.remove(sender)) {
						throw new ProtocolExecutionException("Received message from unknown peer");
					}
					if (peerIds.isEmpty()) {
						receivedAll = true;
						break;
					}
				} else {
					throw new ProtocolExecutionException("Received duplicate message from peer:" + sender);
				}
			} catch (StorageException e) {
				throw new ProtocolExecutionException("Received message was not correctly formatted", e);
			}
		}

		mydata.set(STORAGE.PUBLIC_KEY_SIGS, sigsReceived);
		// Store my data
		protocol.addToStorage(getStepId(), mydata);
		return true;
	}

	@Override
	public String getStepId() {
		return STEP_ID + ":" + this.keyId;
	}

	@Override
	public void processMessage(E msg) {
		this.protocol.addToTranscript(msg);
		incomingMessages.add(msg);
	}

	@Override
	public void setProtocol(Protocol<E> protocol) {
		this.protocol = protocol;

	}
	
	

}

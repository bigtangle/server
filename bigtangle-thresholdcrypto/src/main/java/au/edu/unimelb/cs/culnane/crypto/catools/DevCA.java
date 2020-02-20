/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.crypto.catools;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.security.cert.CertificateException;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development CA that will sign anything. This is used to provide automatic
 * signing of CSRs to allow dynamic creation of additional peers. This should
 * not be used in production for obvious security reasons.
 * 
 * The class is implemented as a singleton and requires no initial setup. If the
 * CA does not except on first request a new CA will be created.
 * 
 * @author Chris Culnane
 *
 */
public class DevCA {

	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(DevCA.class);

	/**
	 * Static instance of this singleton
	 */
	private static final DevCA _instance = new DevCA();

	/**
	 * PrivateKey holding a reference to the CA private key
	 */
	private PrivateKey privateKey;

	/**
	 * CA KeyStore
	 */
	private KeyStore caStore;

	/**
	 * String of path to CA keystore
	 */
	private static final String caPath = "./CA_Store.jks";

	/**
	 * String password for the CA Keystore
	 */
	private static final String ksPwd = "";

	/**
	 * Gets the instance of the DevCA singleton
	 * 
	 * @return DevCA instance
	 */
	public static final DevCA getInstance() {
		return _instance;
	}

	/**
	 * Constructs a new DevCA. Either loading it from the existing CA keystore,
	 * or generating a new CA
	 */
	private DevCA() {
		try {
			
			caStore = null;
			File f = new File(caPath);
			if (f.exists()) {
				logger.info("Loading CA from existing keystore");
				caStore = CertTools.loadKeyStore(caPath, ksPwd.toCharArray());
				X509Certificate caCert = ((X509Certificate) caStore.getCertificate("DevCA"));
				try {
					caCert.checkValidity();
				} catch (CertificateExpiredException | CertificateNotYetValidException e) {
					logger.info("CA certificate is invalid, will regenerate");
					CertTools.renewSelfSignedCA("DevCA", caStore,
							new KeyPair(caCert.getPublicKey(), (PrivateKey) caStore.getKey("DevCA", "".toCharArray())),
							"SHA256withRSA");
					CertTools.saveKeyStore(caStore, caPath, ksPwd.toCharArray());

				}

			} else {
				logger.info("Creating new DevCA");
				CertTools.generateKeyAndCSR("DevCA", caPath, null, "SHA256withRSA", true);
				caStore = CertTools.loadKeyStore(caPath, ksPwd.toCharArray());
			}
			privateKey = (PrivateKey) caStore.getKey("DevCA", "".toCharArray());
		} catch (KeyStoreException | NoSuchAlgorithmException | java.security.cert.CertificateException | IOException
				| NoSuchProviderException | InvalidAlgorithmParameterException | OperatorCreationException
				| UnrecoverableKeyException e) {
			logger.error("Exception creating DevCA", e);

		}
	}

	/**
	 * Gets the CA certificate
	 * 
	 * @return Certificate containing CA root certificate
	 * @throws KeyStoreException
	 */
	public java.security.cert.Certificate keyCACertificate() throws KeyStoreException {
		return this.caStore.getCertificate("DevCA");
	}

	/**
	 * Signs a Certificate Signing Request. Note, since this is a DevCA, it does
	 * not record the CSRs already signed or check any of the details.
	 * 
	 * @param inputCSR
	 *            PKCS10CertificationRequest containing the CSR
	 * @return X509Certificate containing the signed certificate
	 * @throws InvalidKeyException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws SignatureException
	 * @throws IOException
	 * @throws OperatorCreationException
	 * @throws CertificateException
	 * @throws java.security.cert.CertificateException
	 * @throws KeyStoreException
	 */
	public X509Certificate sign(PKCS10CertificationRequest inputCSR)
			throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, SignatureException,
			IOException, OperatorCreationException, CertificateException, java.security.cert.CertificateException,
			KeyStoreException {
	
		SubjectPublicKeyInfo keyInfo = inputCSR.getSubjectPublicKeyInfo();// SubjectPublicKeyInfo.getInstance(pair.getPublic().getEncoded());
		X509v3CertificateBuilder myCertificateGenerator = new X509v3CertificateBuilder(
				new X500Name(CertTools.createDN("DevCA")), new BigInteger("1"), new Date(System.currentTimeMillis()),
				new Date(System.currentTimeMillis() + 30L * 365L * 24L * 60L * 60L * 1000L), inputCSR.getSubject(), keyInfo);
		JcaX509ExtensionUtils jcaUtils = new JcaX509ExtensionUtils();
		myCertificateGenerator.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
		myCertificateGenerator.addExtension(Extension.subjectKeyIdentifier, false,
				jcaUtils.createSubjectKeyIdentifier(inputCSR.getSubjectPublicKeyInfo()));
		myCertificateGenerator.addExtension(Extension.authorityKeyIdentifier, false,
				jcaUtils.createAuthorityKeyIdentifier((X509Certificate) this.keyCACertificate()));

		JcaContentSignerBuilder contentSignerBuilder = new JcaContentSignerBuilder("SHA256withRSA");
		// Set the provider as BouncyCastle
		contentSignerBuilder.setProvider("BC");

		ContentSigner sigGen = contentSignerBuilder.build(privateKey);

		X509CertificateHolder holder = myCertificateGenerator.build(sigGen);
		Certificate eeX509CertificateStructure = holder.toASN1Structure();

		CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");

		// Read Certificate
		InputStream is1 = new ByteArrayInputStream(eeX509CertificateStructure.getEncoded());
		X509Certificate theCert = (X509Certificate) cf.generateCertificate(is1);
		is1.close();
		return theCert;
		// return null;
	}

}

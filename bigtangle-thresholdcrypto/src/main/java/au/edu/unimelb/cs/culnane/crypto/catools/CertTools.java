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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.crypto.exceptions.CertificateValidationException;
import au.edu.unimelb.cs.culnane.crypto.exceptions.SSLInitException;
import au.edu.unimelb.cs.culnane.crypto.ssl.SSLManager;

/**
 * Utility class with methods needed for generating and signing certificate
 * requests
 * 
 * @author Chris Culnane
 *
 */
public class CertTools {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(CertTools.class);

	/**
	 * Adds the key/value pair to the distinguished name buffer
	 * 
	 * @param dn
	 *            StringBuffer containing distinguished name
	 * @param dnKey
	 *            String key
	 * @param value
	 *            String value
	 */
	protected static void addToDistinguishedName(StringBuffer dn, String dnKey, String value) {
		dn.append(", ");
		dn.append(dnKey);
		dn.append("=");
		dn.append(value);
	}

	/**
	 * Either loads or generates new SHA256withRSA SSL keys. If new keys are
	 * generated a CSR is constructed and signed by the built in DevCA. TODO
	 * don't hard code file path to id, add parameter for file path
	 * 
	 * @param id
	 *            String id of key holder
	 * @return SSLContext with the appropriate keys and trust managers
	 * @throws SSLInitException
	 */
	public static SSLContext initSSLKeys(String id) throws SSLInitException {
		try {
			Security.addProvider(new BouncyCastleProvider());
			DevCA devCA = DevCA.getInstance();
			File ksFile = new File("./" + id + ".jks");
			if (!ksFile.exists()) {
				PKCS10CertificationRequest certReq = CertTools.generateKeyAndCSR(id, "./" + id + ".jks",
						devCA.keyCACertificate(), "SHA256withRSA", false);
				KeyStore ks = CertTools.loadKeyStore("./" + id + ".jks");
				Certificate cert = devCA.sign(certReq);
				ks.setKeyEntry(id, ks.getKey(id, "".toCharArray()), "".toCharArray(),
						new Certificate[] { cert, devCA.keyCACertificate() });
				CertTools.saveKeyStore(ks, "./" + id + ".jks");

			}
			KeyStore ks = CertTools.loadKeyStore("./" + id + ".jks");
			X509Certificate myCert = (X509Certificate) ks.getCertificate(id);
			logger.info("Checking certificate validity");
			try {
				myCert.checkValidity();
				logger.info("Ceritifcate is valid");
			} catch (CertificateExpiredException | CertificateNotYetValidException e) {
				logger.info("Ceritificate is invalid, will generate new one");
				PKCS10CertificationRequest certReq = CertTools.generateCSR(
						new KeyPair(myCert.getPublicKey(), (PrivateKey) ks.getKey(id, "".toCharArray())),
						myCert.getSubjectX500Principal().getName(), "SHA256withRSA", false);

				Certificate cert = devCA.sign(certReq);
				ks.setKeyEntry(id, ks.getKey(id, "".toCharArray()), "".toCharArray(),
						new Certificate[] { cert, devCA.keyCACertificate() });
				CertTools.saveKeyStore(ks, "./" + id + ".jks");

			}
			X509Certificate caCert = (X509Certificate) ks.getCertificate("DevCA");
			if (!caCert.equals(devCA.keyCACertificate())) {
				logger.info("CA Certificate is out of date");
				ks.setCertificateEntry("DevCA", devCA.keyCACertificate());
				ks.setKeyEntry(id, ks.getKey(id, "".toCharArray()), "".toCharArray(),
						new Certificate[] { ks.getCertificate(id), devCA.keyCACertificate() });
				CertTools.saveKeyStore(ks, "./" + id + ".jks");
			}
			SSLManager sslManager = initTruststore("./" + id + ".jks");
			SSLContext ctx = SSLContext.getInstance("TLSv1.2");

			ctx.init(sslManager.getKeyManagerFactory().getKeyManagers(),
					sslManager.getTrustManagerFactory().getTrustManagers(), new SecureRandom());
			return ctx;
		} catch (IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException
				| NoSuchProviderException | InvalidAlgorithmParameterException | OperatorCreationException
				| InvalidKeyException | SignatureException | javax.security.cert.CertificateException
				| UnrecoverableKeyException | KeyManagementException e) {
			throw new SSLInitException("Exception initialising SSLkeys", e);
		}
	}

	/**
	 * Creates an SSLManager with respective trust and key managers from the
	 * specified KeyStore.
	 * 
	 * @param pathToKS
	 *            String path to Keystore to load keys and certificates from
	 * @return SSLManager containing trust and key managers
	 * @throws SSLInitException
	 */
	public static SSLManager initTruststore(String pathToKS) throws SSLInitException {
		try {
			KeyStore ks = CertTools.loadKeyStore(pathToKS);

			TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
			PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(ks, new X509CertSelector());
			pkixParams.setRevocationEnabled(false);
			tmf.init(ks);
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ks, "".toCharArray());
			return new SSLManager(tmf, kmf);
		} catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException
				| UnrecoverableKeyException | InvalidAlgorithmParameterException e) {
			throw new SSLInitException("Exception whilst initialising Trust and Key Managers", e);
		}

	}

	/**
	 * Creates a new Distinguished name with common name as specified. All other
	 * fields are pre-configured
	 * 
	 * @param name
	 *            String common name
	 * @return String of complete distinguished name
	 */
	public static String createDN(String name) {
		// Prepare the distinguished name for the certificate from any available
		// parameter
		StringBuffer sb = new StringBuffer();
		sb.append("CN");
		sb.append("=");
		sb.append(name);

		addToDistinguishedName(sb, "O", "ThresholdCrypto");
		addToDistinguishedName(sb, "OU", "ThresholdCryptoDev");
		addToDistinguishedName(sb, "C", "Australia");
		addToDistinguishedName(sb, "L", "Melbourne");
		addToDistinguishedName(sb, "ST", "Victoria");
		return sb.toString();
	}

	/**
	 * Generate a Certificate Signing Request (CSR) with the KeyPair and
	 * distinguished name. This defaults to creating a certificate for 720 days.
	 * 
	 * @param kp
	 *            KeyPair to create the CSR for
	 * @param dn
	 *            String of the distinguished name
	 * @param type
	 *            String containing the Key type
	 * @param isCa
	 *            boolean, if true means it is self signing as the CA, false if
	 *            normal CSR
	 * @return PKCS10CertificationRequest containing the CSR
	 * @throws IOException
	 * @throws OperatorCreationException
	 */
	protected static PKCS10CertificationRequest generateCSR(KeyPair kp, String dn, String type, boolean isCa)
			throws IOException, OperatorCreationException {
		return generateCertificateSigningRequest(dn, kp, 720, "", type, isCa);
	}

	/**
	 * Generates and key and CSR with the specified name, storing it in the
	 * specified KeyStore path and imports the caCert into the keystore.
	 * 
	 * @param name
	 *            String common name
	 * @param keyStorePath
	 *            String path to save keystore
	 * @param caCert
	 *            Certificate containing CA certificate
	 * @return PKCS10CertificationRequest containing the CSR
	 * @throws KeyStoreException
	 * @throws CertificateException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws InvalidAlgorithmParameterException
	 * @throws OperatorCreationException
	 * @throws IOException
	 */
	public static PKCS10CertificationRequest generateKeyAndCSR(String name, String keyStorePath, Certificate caCert)
			throws KeyStoreException, CertificateException, NoSuchAlgorithmException, NoSuchProviderException,
			InvalidAlgorithmParameterException, OperatorCreationException, IOException {
		return generateKeyAndCSR(name, keyStorePath, caCert, "SHA256withRSA", false);
	}

	/**
	 * Generates and key and CSR with the specified name, storing it in the
	 * specified KeyStore path and imports the caCert into the keystore. This
	 * takes additional arguments for the keyType and whether it is for a CA.
	 * 
	 * @param name
	 *            String common name
	 * @param keyStorePath
	 *            String path to save keystore
	 * @param caCert
	 *            Certificate containing CA certificate
	 * @param keyType
	 *            String representing key type
	 * @param isCa
	 *            boolean true if key is for a CA.
	 * @return
	 * @throws KeyStoreException
	 * @throws CertificateException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws InvalidAlgorithmParameterException
	 * @throws OperatorCreationException
	 * @throws IOException
	 */
	public static PKCS10CertificationRequest generateKeyAndCSR(String name, String keyStorePath, Certificate caCert,
			String keyType, boolean isCa) throws KeyStoreException, CertificateException, NoSuchAlgorithmException,
			NoSuchProviderException, InvalidAlgorithmParameterException, OperatorCreationException, IOException {
		KeyStore ks;
		File ksPath = new File(keyStorePath);

		if (ksPath.exists()) {
			ks = loadKeyStore(keyStorePath);

		} else {
			ks = KeyStore.getInstance("JKS");
			ks.load(null, "".toCharArray());
		}
		if (caCert != null) {
			ks.setCertificateEntry("DevCA", caCert);
		}
		KeyPair kp = null;

		if (keyType.indexOf("RSA") >= 0) {
			kp = generateKeyPair("RSA");
		} else {
			throw new IOException("Unknown keytype");
			// kp = generateKeyPair("DSA");
		}
		// KeyPair kp = generateKeyPair();
		String dn = createDN(name);
		// Prepare the distinguished name for the certificate from any available
		// parameters
		PKCS10CertificationRequest certReq = generateCSR(kp, dn, keyType, isCa);

		ContentSigner sigGen = new JcaContentSignerBuilder(keyType).setProvider(new BouncyCastleProvider())
				.build(kp.getPrivate());
		Date startDate = new Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L);
		Date endDate = new Date(System.currentTimeMillis() + 365L * 24L * 60L * 60L * 1000L);

		JcaX509ExtensionUtils jcaUtils = new JcaX509ExtensionUtils();
		SubjectPublicKeyInfo subPubKeyInfo = SubjectPublicKeyInfo.getInstance(kp.getPublic().getEncoded());
		X509v3CertificateBuilder myCertificateGenerator = new X509v3CertificateBuilder(
				new X500Name(CertTools.createDN("DevCA")), new BigInteger("1"), startDate, endDate, new X500Name(dn),
				subPubKeyInfo);

		if (isCa) {
			System.out.println("Setting CA");
			myCertificateGenerator.addExtension(Extension.basicConstraints, false, new BasicConstraints(true));

			KeyUsage usage = new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature | KeyUsage.keyEncipherment
					| KeyUsage.dataEncipherment | KeyUsage.cRLSign);
			myCertificateGenerator.addExtension(Extension.keyUsage, false, usage);

			myCertificateGenerator.addExtension(Extension.authorityKeyIdentifier, false,
					jcaUtils.createAuthorityKeyIdentifier(kp.getPublic(), new X500Principal(dn), BigInteger.ONE));

		} else {
			myCertificateGenerator.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));

		}
		myCertificateGenerator.addExtension(Extension.subjectKeyIdentifier, false,
				jcaUtils.createSubjectKeyIdentifier(subPubKeyInfo));

		X509CertificateHolder certHolder = myCertificateGenerator.build(sigGen);
		if (isCa) {
			FileOutputStream fos1 = new FileOutputStream("ca.cert");
			fos1.write(certHolder.getEncoded());
			fos1.flush();
			fos1.close();
		}

		ByteArrayInputStream bais = new ByteArrayInputStream(certHolder.getEncoded());
		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		Certificate ccert = certFactory.generateCertificate(bais);

		ks.setKeyEntry(name, kp.getPrivate(), "".toCharArray(), new Certificate[] { ccert });

		saveKeyStore(ks, keyStorePath);

		return certReq;
	}

	public static void renewSelfSignedCA(String name, KeyStore ks, KeyPair kp, String keyType)
			throws KeyStoreException, CertificateException, NoSuchAlgorithmException, NoSuchProviderException,
			InvalidAlgorithmParameterException, OperatorCreationException, IOException {

		// KeyPair kp = generateKeyPair();
		String dn = ((X509Certificate) ks.getCertificate(name)).getSubjectX500Principal().getName();
		// Prepare the distinguished name for the certificate from any available
		// parameters

		ContentSigner sigGen = new JcaContentSignerBuilder(keyType).setProvider(new BouncyCastleProvider())
				.build(kp.getPrivate());
		Date startDate = new Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L);
		Date endDate = new Date(System.currentTimeMillis() + 30L * 365L * 24L * 60L * 60L * 1000L);

		JcaX509ExtensionUtils jcaUtils = new JcaX509ExtensionUtils();
		SubjectPublicKeyInfo subPubKeyInfo = SubjectPublicKeyInfo.getInstance(kp.getPublic().getEncoded());
		X509v3CertificateBuilder myCertificateGenerator = new X509v3CertificateBuilder(
				new X500Name(CertTools.createDN("DevCA")), new BigInteger("1"), startDate, endDate, new X500Name(dn),
				subPubKeyInfo);

		myCertificateGenerator.addExtension(Extension.basicConstraints, false, new BasicConstraints(true));

		KeyUsage usage = new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature | KeyUsage.keyEncipherment
				| KeyUsage.dataEncipherment | KeyUsage.cRLSign);
		myCertificateGenerator.addExtension(Extension.keyUsage, false, usage);

		myCertificateGenerator.addExtension(Extension.authorityKeyIdentifier, false,
				jcaUtils.createAuthorityKeyIdentifier(kp.getPublic(), new X500Principal(dn), BigInteger.ONE));

		myCertificateGenerator.addExtension(Extension.subjectKeyIdentifier, false,
				jcaUtils.createSubjectKeyIdentifier(subPubKeyInfo));

		X509CertificateHolder certHolder = myCertificateGenerator.build(sigGen);
		FileOutputStream fos1 = new FileOutputStream("ca.cert");
		fos1.write(certHolder.getEncoded());
		fos1.flush();
		fos1.close();

		ByteArrayInputStream bais = new ByteArrayInputStream(certHolder.getEncoded());
		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		Certificate ccert = certFactory.generateCertificate(bais);

		ks.setKeyEntry(name, kp.getPrivate(), "".toCharArray(), new Certificate[] { ccert });
	}

	/**
	 * Generates a certificate signing request (CSR)
	 * 
	 * @param dn
	 *            String distinguished name
	 * @param pair
	 *            KeyPair to generate CSR for
	 * @param days
	 *            int days to request certificate is valid for
	 * @param challengePassword
	 *            String of challenge password
	 * @param keyType
	 *            String of key type
	 * @param isCa
	 *            boolean true if generating for CA, false if not
	 * @return PKCS10CertificationRequest containing CSR
	 * @throws OperatorCreationException
	 */
	public static PKCS10CertificationRequest generateCertificateSigningRequest(String dn, KeyPair pair, int days,
			String challengePassword, String keyType, boolean isCa) throws OperatorCreationException {

		// Create the subject
		X500Name subject = new X500Name(dn);

		// Get the public key
		SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(pair.getPublic().getEncoded());

		// Create a builder object
		PKCS10CertificationRequestBuilder certificationRequestBuilder = new PKCS10CertificationRequestBuilder(subject,
				publicKeyInfo);

		if (isCa) {
			KeyUsage usage = new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature | KeyUsage.keyEncipherment
					| KeyUsage.dataEncipherment | KeyUsage.cRLSign);
			certificationRequestBuilder.addAttribute(Extension.keyUsage, usage);

		} else {
			// Set how we want to use it
			certificationRequestBuilder.addAttribute(Extension.keyUsage, new KeyUsage(KeyUsage.digitalSignature
					| KeyUsage.keyEncipherment | KeyUsage.dataEncipherment | KeyUsage.keyAgreement));
		}
		// Set the extended usage
		Vector<KeyPurposeId> ekUsages = new Vector<KeyPurposeId>();
		// Client and Server auth usage
		ekUsages.add(KeyPurposeId.id_kp_clientAuth);
		ekUsages.add(KeyPurposeId.id_kp_serverAuth);

		certificationRequestBuilder.addAttribute(Extension.extendedKeyUsage,
				new ExtendedKeyUsage(ekUsages.toArray(new KeyPurposeId[0])));

		// We need to sign our request
		JcaContentSignerBuilder contentSignerBuilder = new JcaContentSignerBuilder(keyType);
		// Set the provider as BouncyCastle
		contentSignerBuilder.setProvider("BC");

		// Create a content signer, this will do the actual signing
		ContentSigner contentSigner = contentSignerBuilder.build(pair.getPrivate());

		// We currently use a blank password
		DERPrintableString password = new DERPrintableString(challengePassword);
		certificationRequestBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_challengePassword, password);

		// Build the certificate request and encode it with PEM to a string
		PKCS10CertificationRequest certificationRequest = certificationRequestBuilder.build(contentSigner);

		return certificationRequest;

	}

	/**
	 * Generate a key pair of the specified type with a size of 2048 bits
	 * 
	 * @param type
	 *            String type of key pair to generate
	 * @return KeyPair of generated keys
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws InvalidAlgorithmParameterException
	 */
	public static KeyPair generateKeyPair(String type)
			throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {

		KeyPairGenerator gen = null;

		gen = KeyPairGenerator.getInstance(type, "BC");
		gen.initialize(2048, new SecureRandom());
		KeyPair pair = gen.generateKeyPair();

		return pair;
	}

	/**
	 * Loads a keystore from a string path
	 * 
	 * @param filePath
	 *            String path to keystore
	 * @return KeyStore loaded from path
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws KeyStoreException
	 */
	public static KeyStore loadKeyStore(String filePath) throws NoSuchAlgorithmException, CertificateException,
			FileNotFoundException, IOException, KeyStoreException {
		return loadKeyStore(filePath, "".toCharArray());
	}

	/**
	 * Loads a keystore from a string path
	 * 
	 * @param filePath
	 *            String path to keystore
	 * @param password
	 *            keystore password
	 * @return KeyStore loaded from path
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws KeyStoreException
	 */
	public static KeyStore loadKeyStore(String filePath, char[] password) throws NoSuchAlgorithmException,
			CertificateException, FileNotFoundException, IOException, KeyStoreException {
		KeyStore ks = KeyStore.getInstance("JKS");
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(new File(filePath));
			ks.load(fis, password);
		} finally {
			if (fis != null) {
				fis.close();
			}
		}
		return ks;
	}

	/**
	 * Saves a keystore to a string path
	 * 
	 * @param ks
	 *            Keystore to save
	 * @param filePath
	 *            String path to save keystore
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws KeyStoreException
	 */
	public static void saveKeyStore(KeyStore ks, String filePath) throws NoSuchAlgorithmException, CertificateException,
			FileNotFoundException, IOException, KeyStoreException {
		saveKeyStore(ks, filePath, "".toCharArray());
	}

	/**
	 * Saves a keystore to a string path
	 * 
	 * @param ks
	 *            Keystore to save
	 * @param filePath
	 *            String path to save keystore
	 * @param password
	 *            keystore password
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws KeyStoreException
	 */
	public static void saveKeyStore(KeyStore ks, String filePath, char[] password) throws NoSuchAlgorithmException,
			CertificateException, FileNotFoundException, IOException, KeyStoreException {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(new File(filePath));
			ks.store(fos, password);
		} finally {
			if (fos != null) {
				fos.close();
			}
		}

	}

	/**
	 * Verifies that a certificate path, consistent of a single certificate, is
	 * valid for the trust store in ks.
	 * 
	 * @param certs
	 *            Certificate path to check
	 * @param ks
	 *            KeyStore to act as trust store
	 * @return PKIXCertPathValidatorResult if the result is valid, otherwise
	 *         throws an exception
	 * @throws CertificateValidationException
	 *             if the certificate path is not valid
	 */
	public static PKIXCertPathValidatorResult verifyCertificateAndGetKey(X509Certificate certs, KeyStore ks)
			throws CertificateValidationException {
		return verifyCertificateAndGetKey(new X509Certificate[] { certs }, ks);
	}

	/**
	 * Verifies that a certificate path is valid for the trust store in ks.
	 * 
	 * @param certs
	 *            Certificate path to check
	 * @param ks
	 *            KeyStore to act as trust store
	 * @return PKIXCertPathValidatorResult if the result is valid, otherwise
	 *         throws an exception
	 * @throws CertificateValidationException
	 *             if the certificate path is not valid
	 */
	public static PKIXCertPathValidatorResult verifyCertificateAndGetKey(X509Certificate[] certs, KeyStore ks)
			throws CertificateValidationException {
		try {
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			List<X509Certificate> mylist = new ArrayList<X509Certificate>();
			for (X509Certificate c : certs) {
				mylist.add(c);
			}
			CertPath cp = cf.generateCertPath(mylist);
			PKIXParameters params = new PKIXParameters(ks);
			params.setRevocationEnabled(false);
			CertPathValidator cpv = CertPathValidator.getInstance(CertPathValidator.getDefaultType());
			return (PKIXCertPathValidatorResult) cpv.validate(cp, params);
		} catch (CertificateException | KeyStoreException | InvalidAlgorithmParameterException
				| NoSuchAlgorithmException | CertPathValidatorException e) {
			throw new CertificateValidationException(e);
		}

	}

	/**
	 * Gets the name of a certificate for an X500 principal.
	 * 
	 * @param principal
	 *            The X500 principal.
	 * @return The certificate name.
	 */
	public static String getCertificateName(Principal principal) {
		X500Name certName = new X500Name(principal.getName());
		RDN cn = certName.getRDNs(BCStyle.CN)[0];

		return IETFUtils.valueToString(cn.getFirst().getValue());
	}
}

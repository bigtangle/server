/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.web;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.KeyValue;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.docker.DockerHelper;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;

/**
 * <p>
 * Provides services for sync blocks from remote servers via p2p.
 * 
 * sync remote chain data from chainlength, if chainlength = null, then sync the
 * chain data from the total rating with chain 100% For the sync from given
 * checkpoint, the server must be restarted.
 * 
 * 
 * </p>
 */
@Service
public class SyncBlockService {

	// @Autowired
	// protected NetworkParameters networkParameters;

	private static final Logger log = LoggerFactory.getLogger(SyncBlockService.class);

	// default start sync of chain and non chain data
	public void startSingleProcess() throws BlockStoreException, JsonProcessingException, IOException {

		log.debug(" Start syncServerInfo  : ");

		localFileServerInfoWrite(DispatcherController.PATH, DispatcherController.CONFPATH,
				"https://p.bigtangle.org:8088/", false);
		log.debug(" end syncServerInfo: ");

	}

	/*
	 * write to local file and read it at start only
	 */
	public static void localFileServerInfoWrite(String zipDir, String confDir, String bigtangleServer, boolean noshell)
			throws JsonProcessingException, IOException {

		String path = zipDir;

		HashMap<String, Object> requestParam = new HashMap<String, Object>();
		byte[] response = OkHttp3Util.post(bigtangleServer + ReqCmd.searchWebTokens.name(),
				Json.jsonmapper().writeValueAsString(requestParam).getBytes());

		GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(response, GetTokensResponse.class);
		List<Token> tokens = getTokensResponse.getTokens();
		if (tokens != null && !tokens.isEmpty()) {
			for (Token token : tokens) {
				TokenKeyValues tokenKeyValues = token.getTokenKeyValues();
				if (tokenKeyValues.getKeyvalues() != null && !tokenKeyValues.getKeyvalues().isEmpty()) {
					KeyValue keyValue = tokenKeyValues.getKeyvalues().get(0);
					String serverName = token.getTokenname();
					for (KeyValue kv : tokenKeyValues.getKeyvalues()) {
						if (kv.getKey().equals("aliasService")) {
							serverName = kv.getValue();
						}
						if (kv.getKey().equals("site")) {
							keyValue = kv;
						}
					}
					String unzipDirPath = path + "/" + token.getTokenFullname();
					File unZipDir = new File(unzipDirPath);
					String versionString = "0";
					boolean flag = false;
					if (unZipDir.exists()) {
						String webVerPath = unzipDirPath + "/version/";
						File versionDir = new File(webVerPath);
						if (versionDir.exists() && versionDir.isDirectory()) {
							versionString = versionDir.list()[0].split("\\.")[0];
						}

					} else {
						flag = true;
					}

					if (flag || (token.getTokenindex() > Long.valueOf(versionString))) {
						byte[] zipFile = Base64.decodeBase64(keyValue.getValue());
						byte2File(zipFile, path, token.getTokenFullname() + ".zip");

						FileUtils.deleteDirectory(unZipDir);
						new Zip().unZip(unzipDirPath + ".zip");
						byte2File(token.getTokenFullname().getBytes(), unzipDirPath + "/version/",
								token.getTokenindex() + ".txt");
						deployConf(confDir, serverName);
						if (!noshell) {
							DockerHelper dockerHelper = new DockerHelper();
							try {
								dockerHelper.shellExecuteLocal("service apache reload");
							} catch (Exception e) {
								log.error("", e);
							}
						}
						File zip = new File(unzipDirPath + ".zip");
						zip.delete();

					}

				}
			}
		}

	}

	public static void deployConf(String confDir, String tokenname) {
		if (new File(confDir + tokenname + ".bigtangle.org" + ".conf").exists()) {
			return;
		}
		StringBuffer stringBuffer = new StringBuffer();

		stringBuffer.append("<VirtualHost *:80>\n");
		stringBuffer.append("ServerName " + tokenname + ".bigtangle.org\n");
		stringBuffer.append("DocumentRoot /var/www/" + tokenname + "\n");
		stringBuffer.append("ErrorLog ${APACHE_LOG_DIR}/error.log\n");
		stringBuffer.append("CustomLog ${APACHE_LOG_DIR}/access.log combined\n");
		stringBuffer.append("RewriteEngine on\n");
		stringBuffer.append("RewriteCond %{SERVER_NAME} =" + tokenname + ".bigtangle.org\n");
		stringBuffer.append("RewriteRule ^ https://%{SERVER_NAME}%{REQUEST_URI} [END,NE,R=permanent]\n");
		stringBuffer.append("</VirtualHost>\n");

		stringBuffer.append("<VirtualHost *:443>\n");
		stringBuffer.append("ServerName  " + tokenname + ".bigtangle.org\n");
		stringBuffer.append("DocumentRoot /var/www/" + tokenname + "\n");
		stringBuffer.append("ErrorLog ${APACHE_LOG_DIR}/error.log\n");
		stringBuffer.append("CustomLog ${APACHE_LOG_DIR}/access.log combined\n");
		stringBuffer.append("SSLProxyEngine on\n");
		stringBuffer.append("SSLProxyVerify none\n");
		stringBuffer.append("SSLProxyCheckPeerCN off\n");
		stringBuffer.append("SSLProxyCheckPeerName off\n");
		stringBuffer.append("SSLProxyCheckPeerExpire off\n");
		stringBuffer.append("    SSLEngine on\n");
		stringBuffer.append("    SSLOptions +StrictRequire\n");
		stringBuffer.append("SSLProxyVerify none\n");
		stringBuffer.append("SSLProxyCheckPeerCN off\n");
		stringBuffer.append(" <Directory />\n");
		stringBuffer.append("SSLRenegBufferSize 2098200000\n");
		stringBuffer.append("SSLRequireSSL\n");
		stringBuffer.append("</Directory>\n");
		stringBuffer.append(" SSLCipherSuite HIGH:MEDIUM:!aNULL:+SHA1:+MD5:+HIGH:+MEDIUM\n");
		stringBuffer.append(" SSLSessionCacheTimeout 600\n");
		stringBuffer.append("SSLProxyEngine on\n");
		stringBuffer.append("Include /etc/letsencrypt/options-ssl-apache.conf\n");
		stringBuffer.append("SSLCertificateFile /etc/letsencrypt/live/www.bigtangle.org/fullchain.pem\n");
		stringBuffer.append("SSLCertificateKeyFile /etc/letsencrypt/live/www.bigtangle.org/privkey.pem\n");
		stringBuffer.append("</VirtualHost>");
		byte2File(stringBuffer.toString().getBytes(), confDir, tokenname + ".bigtangle.org" + ".conf");

	}

	public static File byte2File(byte[] buf, String filePath, String fileName) {
		BufferedOutputStream bos = null;
		FileOutputStream fos = null;
		File file = null;
		try {
			File dir = new File(filePath);
			if (!dir.exists() && dir.isDirectory()) {
				dir.mkdirs();
			}
			file = new File(filePath + File.separator + fileName);
			if (file.exists()) {
				file.delete();
			}
			fos = new FileOutputStream(file);
			bos = new BufferedOutputStream(fos);
			bos.write(buf);

		} catch (Exception e) {
			log.error("", e);
		} finally {
			if (bos != null) {
				try {
					bos.close();
				} catch (IOException e) {
					log.error("", e);
				}
			}
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					log.error("", e);
				}
			}
		}
		return file;
	}

}

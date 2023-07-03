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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.KeyValue;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.GetTokensResponse;
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
		// syncServerInfo();
		localFileServerInfoWrite();
		log.debug(" end syncServerInfo: ");

	}

	/*
	 * write to local file and read it at start only
	 */
	public static void localFileServerInfoWrite() throws JsonProcessingException, IOException {

		String path = DispatcherController.PATH;

		File file = new File(path);
		if (file.exists()) {
			file.delete();
		}
		HashMap<String, Object> requestParam = new HashMap<String, Object>();
		byte[] response = OkHttp3Util.post("https://p.bigtangle.org.8088/" + ReqCmd.searchTokens.name(),
				Json.jsonmapper().writeValueAsString(requestParam).getBytes());

		GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(response, GetTokensResponse.class);
		List<Token> tokens = getTokensResponse.getTokens();
		if (tokens != null && !tokens.isEmpty()) {
			for (Token token : tokens) {
				TokenKeyValues tokenKeyValues = token.getTokenKeyValues();
				if (tokenKeyValues.getKeyvalues() != null && !tokenKeyValues.getKeyvalues().isEmpty()) {
					KeyValue keyValue = tokenKeyValues.getKeyvalues().get(0);
					byte[] zipFile = Base64.decodeBase64(keyValue.getValue());
					byte2File(zipFile, path, keyValue.getKey() + ".zip");
					new Zip().unZip(path + keyValue.getKey() + ".zip");
				}
			}
		}

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

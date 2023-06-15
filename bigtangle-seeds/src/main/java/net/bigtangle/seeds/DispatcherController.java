/*******************************************************************************
 *  
 *  Copyright   2018  Inasset GmbH. 
 *******************************************************************************/
package net.bigtangle.seeds;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Stopwatch;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.ErrorResponse;
import net.bigtangle.utils.Gzip;
import net.bigtangle.utils.Json;

@RestController
@RequestMapping("/")
public class DispatcherController {

	private static final Logger logger = LoggerFactory.getLogger(DispatcherController.class);

	public static List<ServerInfo> serverinfoList;
	public static String PATH = "./logs/serverinfo.json";
	@Autowired
	protected SyncBlockService syncBlockService;

	@SuppressWarnings("unchecked")
	@RequestMapping(value = "{reqCmd}", method = { RequestMethod.POST, RequestMethod.GET })
	public void process(@PathVariable("reqCmd") String reqCmd, @RequestBody byte[] contentBytes,
			HttpServletResponse httpServletResponse, HttpServletRequest httprequest) throws Exception {

		ExecutorService executor = Executors.newSingleThreadExecutor();
		@SuppressWarnings("rawtypes")
		final Future<String> handler = executor.submit(new Callable() {
			@Override
			public String call() throws Exception {
				processDo(reqCmd, contentBytes, httpServletResponse, httprequest);
				return "";
			}
		});
		try {
			handler.get(30, TimeUnit.MINUTES);
		} catch (TimeoutException e) {
			logger.debug(" process  Timeout  ");
			handler.cancel(true);
			AbstractResponse resp = ErrorResponse.create(100);
			StringWriter sw = new StringWriter();
			resp.setMessage(sw.toString());

		} finally {
			executor.shutdownNow();
		}

	}

	@SuppressWarnings("unchecked")
	public void processDo(@PathVariable("reqCmd") String reqCmd, @RequestBody byte[] contentBytes,
			HttpServletResponse httpServletResponse, HttpServletRequest httprequest) throws Exception {
		Stopwatch watch = Stopwatch.createStarted();

		String backMessage = "";
		byte[] bodyByte = new byte[0];
		try {

			logger.trace("reqCmd : {} from {}, size : {}, started.", reqCmd, httprequest.getRemoteAddr(),
					contentBytes.length);

			bodyByte = Gzip.decompressOut(contentBytes);
			ReqCmd reqCmd0000 = ReqCmd.valueOf(reqCmd);

			switch (reqCmd0000) {

			case register: {
				String reqStr = new String(bodyByte, "UTF-8");
				Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
				ServerInfo serverInfo = new ServerInfo();
				String url = (String) request.get("url");
				logger.debug("url==" + url);
				String servertype = (String) request.get("servertype");
				boolean flag = false;
				if (serverinfoList != null) {
					for (ServerInfo temp : serverinfoList) {
						if (temp.getUrl().equals(url) && temp.getServertype().equals(servertype)) {
							flag = true;
						}
					}
				}

				if (!flag) {

					serverInfo.setUrl(url);
					serverInfo.setServertype(servertype);
					serverInfo.setStatus("active");
					if (serverinfoList == null) {
						serverinfoList = new ArrayList<ServerInfo>();
					}
					try {
						SyncBlockService.getMaxConfirmedReward(serverInfo.getUrl());
						serverinfoList.add(serverInfo);
					} catch (Exception e) {
						logger.error("", e);
					}

				}

				// this.outPrintJSONString(httpServletResponse,
				// GetStringResponse.create(urlTobyte(url)), watch);

			}
				break;
			case serverinfolist: {
				for (ServerInfo serverInfo : serverinfoList) {
					
				}
				AbstractResponse response = ServerinfoResponse.create(serverinfoList);
				this.gzipBinary(httpServletResponse, response);

			}
				break;

			default:
				break;
			}
		} catch (Throwable exception) {
			logger.error("reqCmd : {}, reqHex : {}, {},error.", reqCmd, bodyByte.length, remoteAddr(httprequest),
					exception);
			AbstractResponse resp = ErrorResponse.create(100);
			StringWriter sw = new StringWriter();
			sw.append(backMessage);
			exception.printStackTrace(new PrintWriter(sw));
			resp.setMessage(sw.toString());
			this.outPrintJSONString(httpServletResponse, resp, watch);
		} finally {
			if (watch.elapsed(TimeUnit.MILLISECONDS) > 1000)
				logger.info(reqCmd + " takes {} from {}", watch.elapsed(TimeUnit.MILLISECONDS),
						remoteAddr(httprequest));
			watch.stop();
		}
	}

	@RequestMapping("/")
	public String index() {
		return "Bigtangle-seeds";
	}

	public void gzipBinary(HttpServletResponse httpServletResponse, List<ServerInfo> response) throws Exception {
		GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

		servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(response));
		servletOutputStream.flush();
		servletOutputStream.close();
	}

	private void errorLimit(HttpServletResponse httpServletResponse, Stopwatch watch) throws Exception {
		AbstractResponse resp = ErrorResponse.create(101);
		resp.setErrorcode(403);
		resp.setMessage(" limit reached. ");
		this.outPrintJSONString(httpServletResponse, resp, watch);
	}

	public void outPutDataMap(HttpServletResponse httpServletResponse, Object data) throws Exception {
		httpServletResponse.setCharacterEncoding("UTF-8");
		HashMap<String, Object> result = new HashMap<String, Object>();
		result.put("data", data);
		GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

		servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(result));
		servletOutputStream.flush();
		servletOutputStream.close();
	}

	public void outPointBinaryArray(HttpServletResponse httpServletResponse, byte[] data) throws Exception {
		httpServletResponse.setCharacterEncoding("UTF-8");

		HashMap<String, Object> result = new HashMap<String, Object>();
		result.put("dataHex", Utils.HEX.encode(data));
		GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

		servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(result));
		servletOutputStream.flush();
		servletOutputStream.close();
	}

	public void outPrintJSONString(HttpServletResponse httpServletResponse, AbstractResponse response, Stopwatch watch)
			throws Exception {
		long duration = watch.elapsed(TimeUnit.MILLISECONDS);
		response.setDuration(duration);
		gzipBinary(httpServletResponse, response);
	}

	public void gzipBinary(HttpServletResponse httpServletResponse, AbstractResponse response) throws Exception {
		GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

		servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(response));
		servletOutputStream.flush();
		servletOutputStream.close();
	}

	public String remoteAddr(HttpServletRequest request) {
		String remoteAddr = "";
		remoteAddr = request.getHeader("X-FORWARDED-FOR");
		if (remoteAddr == null || "".equals(remoteAddr)) {
			remoteAddr = request.getRemoteAddr();
		} else {
			StringTokenizer tokenizer = new StringTokenizer(remoteAddr, ",");
			while (tokenizer.hasMoreTokens()) {
				remoteAddr = tokenizer.nextToken();
				break;
			}
		}
		return remoteAddr;
	}

	private static final String Huobi15Fee = "15";
}

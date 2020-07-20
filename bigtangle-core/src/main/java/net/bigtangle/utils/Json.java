/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.utils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class Json {

	public static ObjectMapper jsonmapper() {

		// getJsonName(response);
		ObjectMapper mapper = new ObjectMapper();

		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
		mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
		// SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy");
		// mapper.setDateFormat(outputFormat);

		mapper.setSerializationInclusion(Include.NON_EMPTY);
		mapper.setSerializationInclusion(Include.NON_NULL);
		mapper.configure( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper;
		// mapper.writeValue(System.out, response);
	}
}

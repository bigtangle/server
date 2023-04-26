package net.bigtangle.userpay;

import java.math.BigDecimal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Created by JB on 18/11/2020.
 */
public class GasPriceSpread {
	public static long RAPID_SECONDS = 15;
	public static long FAST_SECONDS = 60;
	public static long STANDARD_SECONDS = 60 * 3;
	public static long SLOW_SECONDS = 60 * 10;

	public final BigDecimal rapid;
	public final BigDecimal fast;
	public final BigDecimal standard;
	public final BigDecimal slow;

	public final long timeStamp;

	private int customIndex;

	public GasPriceSpread(String apiReturn) throws JsonSyntaxException {
		BigDecimal rRapid = BigDecimal.ZERO;
		BigDecimal rFast = BigDecimal.ZERO;
		BigDecimal rStandard = BigDecimal.ZERO;
		BigDecimal rSlow = BigDecimal.ZERO;
		long rTimeStamp = 0;

		JsonElement jsonElement = new JsonParser().parse(apiReturn);

		JsonObject result = jsonElement.getAsJsonObject();

		// JsonObject result = new JsonObject(apiReturn);
		JsonObject data = result.get("result").getAsJsonObject();
		;
		// rRapid = new BigDecimal(data.get("rapid").toString());
		rFast = new BigDecimal(data.get("FastGasPrice").toString());
		// .divide(BigDecimal.TEN).setScale(2,
		// RoundingMode.HALF_DOWN);
		// rStandard = new BigDecimal(result.get("standard").toString() );
		// rSlow = new BigDecimal(data.get("slow").toString() );

		rapid = rFast;
		fast = rFast;
		standard = rFast;
		slow = rFast;
		timeStamp = rTimeStamp;
	}

	public GasPriceSpread(BigDecimal currentAvGasPrice) {
		rapid = BigDecimal.ZERO;
		fast = BigDecimal.ZERO;
		standard = currentAvGasPrice;
		slow = BigDecimal.ZERO;
		timeStamp = System.currentTimeMillis();
	}

	public GasPriceSpread(String r, String f, String st, String sl, long timeSt) {
		rapid = new BigDecimal(r);
		fast = new BigDecimal(f);
		standard = new BigDecimal(st);
		slow = new BigDecimal(sl);
		timeStamp = timeSt;
	}

	public int getCustomIndex() {
		return customIndex;
	}
}

package net.bigtangle.tools.config;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.UnitTestParams;

public class Configure {

    public final static String SIMPLE_SERVER_CONTEXT_ROOT = "https://test1.bigtangle.org:8088/";

    public final static String ORDER_MATCH_CONTEXT_ROOT = "http://localhost:8089/";

    public final static NetworkParameters PARAMS = UnitTestParams.get();

    public final static ECKey OUT_KEY = new ECKey();
}

package net.bigtangle.tools.config;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.UnitTestParams;

public class Configure {

    public final static String SIMPLE_SERVER_CONTEXT_ROOT = "https://test1.bigtangle.org:8088/";

    public final static String ORDER_MATCH_CONTEXT_ROOT = "https://test2market.bigtangle.org:8090/";

    public final static NetworkParameters PARAMS = UnitTestParams.get();
}

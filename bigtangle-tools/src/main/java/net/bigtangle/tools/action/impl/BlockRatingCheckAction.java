package net.bigtangle.tools.action.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockRatingCheckAction {

    public void execute0() throws Exception {
        logger.info("block rating check action start");
//        try {
//            HashMap<String, Object> requestParams = new HashMap<String, Object>();
//            String resp = OkHttp3Util.postString(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.getAllEvaluations.name(),
//                    Json.jsonmapper().writeValueAsString(requestParams));
//            HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);
//            List<HashMap<String, Object>> evaluations = (List<HashMap<String, Object>>) result.get("evaluations");
//            for (HashMap<String, Object> map : evaluations) {
//                String blockHexStr = (String) map.get("blockHexStr");
//                int rating = (int) map.get("rating");
//                if (!blockHashContainer.contains(blockHexStr)) {
//                    continue;
//                }
//                if (rating == 0) {
//                    logger.info("block hex str : " + blockHexStr + ", rating : " + rating + ", exit");
//                    System.exit(0);
//                }
//            }
//        } catch (Exception e) {
//            logger.error("block rating check action exception", e);
//        }
        logger.info("block rating check action end");
    }

    //private BlockHashContainer blockHashContainer = BlockHashContainer.getInstance();

    private static final Logger logger = LoggerFactory.getLogger(BlockRatingCheckAction.class);
}

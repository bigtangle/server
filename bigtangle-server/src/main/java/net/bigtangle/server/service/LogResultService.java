package net.bigtangle.server.service;

import java.util.Date;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.LogResult;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.UUIDUtil;

@Service
public class LogResultService {

    @Autowired
    protected FullPrunedBlockStore store;
    
    public void submitLogResult(Map<String, Object> request) throws BlockStoreException {
        LogResult logResult = new LogResult();
        String logContent = (String) request.get("logContent");
        logResult.setLogResultId(UUIDUtil.randomUUID());
        logResult.setLogContent(logContent);
        logResult.setSubmitDate(new Date());
        this.store.insertLogResult(logResult);
    }
}

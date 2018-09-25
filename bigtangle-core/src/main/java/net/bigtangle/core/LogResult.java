/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.util.Date;

public class LogResult implements java.io.Serializable {

    private static final long serialVersionUID = -844681325873277696L;

    private String logResultId;
    
    private String logContent;
    
    private Date submitDate;

    public String getLogResultId() {
        return logResultId;
    }

    public void setLogResultId(String logResultId) {
        this.logResultId = logResultId;
    }

    public String getLogContent() {
        return logContent;
    }

    public void setLogContent(String logContent) {
        this.logContent = logContent;
    }

    public Date getSubmitDate() {
        return submitDate;
    }

    public void setSubmitDate(Date submitDate) {
        this.submitDate = submitDate;
    }
}

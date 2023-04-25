package net.bigtangle.userpay;

import java.io.Serializable;

public class UserPay implements  Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private Long payid;
    private String status;
    private String tokenname;
    private String tokenid;
    private String fromaddress;
    private String fromsystem;
    private String toaddress;
    private String tosystem;
    private String gaslimit;
    private String gasprice;
    private String fee;
    private Long userid;
    private String fromblockhash;
    private String transactionhash;
    private String toblockhash;
    private String remark;
    // amount is BigDecimal toString
    private String amount;

    //Only UI
    public Boolean uiselection = false;
    
    public String columns() {
        return " payid,status, userid, tokenname,tokenid" + ",amount, gaslimit,gasprice,fee,"
                + " fromaddress, fromsystem, toaddress,tosystem" + ",fromblockhash,transactionhash,toblockhash,"
                + "remark,changedby,changeddate,changedTolast";
    }

    public String insertSQL() {
        return "insert into userpay (" + columns() + ") values (?,?,? ,?,?,? ,?,?,?, ?,?,? ,?, ?,?,?, ?,?,?,?)";
    }

    public String deleteSQL() {
        return "delete from userpay  where payid=? ";
    }
    
    public String updateStatusRemark() {
        return "update userpay set status=?, remark=? ,changedby=?,changeddate=? where payid=?";

    }
 

    public String updateFromblockhashStatus() {
        return "update userpay set fromblockhash=?, status=?,changedby=?, changeddate=? where payid=?";

    }

    public String updateToblockhashStatus() {
        return "update userpay set toblockhash=?, status=?,changedby=?, changeddate=?  where payid=?";

    }
    public String updateTransactionhashStatus() {
        return "update userpay set transactionhash=?, status=?,changedby=? , changeddate=? where payid=?";

    }

    public String selectSQL() {
        return "select " + columns() + " from userpay";

    }

    public String selectByStatusAndUseridSQL() {
        return "select" + columns() + " from userpay   " + "where (status=? or status=? ) and userid=? and (fromsystem=? or tosystem=?)";

    }

    public String selectLikeSQL(String like) {
        return "select " + columns() + " from userpay    where (fromsystem=? or tosystem=?)  " + like;

    }

    public UserPay() {

    }

   

    public UserPay(Long payid, String status, String tokenname, String tokenid, String fromaddress, String fromsystem,
            String toaddress, String tosystem, String amount, String gaslimit, String gasprice,
            String fee, Long userid, String fromblockhash, String transactionhash, String toblockhash,
            String remark) {
        super();
        this.payid = payid;
        this.status = status;
        this.tokenname = tokenname;
        this.tokenid = tokenid;
        this.fromaddress = fromaddress;
        this.fromsystem = fromsystem;
        this.toaddress = toaddress;
        this.tosystem = tosystem;
        this.amount = amount;
        this.gaslimit = gaslimit;
        this.gasprice = gasprice;
        this.fee = fee;
        this.userid = userid;
        this.fromblockhash = fromblockhash;
        this.transactionhash = transactionhash;
        this.toblockhash = toblockhash;
        this.remark = remark;
 
     
    }

    public Long getPayid() {
        return payid;
    }

    public void setPayid(Long payid) {
        this.payid = payid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTokenname() {
        return tokenname;
    }

    public void setTokenname(String tokenname) {
        this.tokenname = tokenname;
    }

    public String getTokenid() {
        return tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenid = tokenid;
    }

    public String getFromaddress() {
        return fromaddress;
    }

    public void setFromaddress(String fromaddress) {
        this.fromaddress = fromaddress;
    }

    public String getFromsystem() {
        return fromsystem;
    }

    public void setFromsystem(String fromsystem) {
        this.fromsystem = fromsystem;
    }

    public String getToaddress() {
        return toaddress;
    }

    public void setToaddress(String toaddress) {
        this.toaddress = toaddress;
    }

    public String getTosystem() {
        return tosystem;
    }

    public void setTosystem(String tosystem) {
        this.tosystem = tosystem;
    } 
    
     
  
    public String getGaslimit() {
        return gaslimit;
    }

    public void setGaslimit(String gaslimit) {
        this.gaslimit = gaslimit;
    }

    public String getGasprice() {
        return gasprice;
    }

    public void setGasprice(String gasprice) {
        this.gasprice = gasprice;
    }

    public String getFee() {
        return fee;
    }

    public void setFee(String fee) {
        this.fee = fee;
    }

    public Long getUserid() {
        return userid;
    }

    public void setUserid(Long userid) {
        this.userid = userid;
    }

    public String getTransactionhash() {
        return transactionhash;
    }

    public void setTransactionhash(String transactionhash) {
        this.transactionhash = transactionhash;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
 

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getFromblockhash() {
        return fromblockhash;
    }

    public void setFromblockhash(String fromblockhash) {
        this.fromblockhash = fromblockhash;
    }

    public String getToblockhash() {
        return toblockhash;
    }

    public void setToblockhash(String toblockhash) {
        this.toblockhash = toblockhash;
    }

    public Boolean getUiselection() {
        return uiselection;
    }

    public void setUiselection(Boolean uiselection) {
        this.uiselection = uiselection;
    }

    @Override
    public String toString() {
        return "UserPay [payid=" + payid + ", status=" + status + ", tokenname=" + tokenname + ", tokenid=" + tokenid
                + ", fromaddress=" + fromaddress + ", fromsystem=" + fromsystem + ", toaddress=" + toaddress
                + ", tosystem=" + tosystem + ", gaslimit=" + gaslimit + ", gasprice=" + gasprice + ", fee=" + fee
                + ", userid=" + userid + ", fromblockhash=" + fromblockhash + ", transactionhash=" + transactionhash
                + ", toblockhash=" + toblockhash + ", remark=" + remark  + ", amount=" + amount
                + ", uiselection=" + uiselection + "]";
    }

}
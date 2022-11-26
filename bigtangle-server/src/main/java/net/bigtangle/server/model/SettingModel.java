/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.model;

/*
  * Block output dynamic evaluation data
  */
public class SettingModel {
    private String name; 
    private String settingvalue;
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getSettingvalue() {
        return settingvalue;
    }
    public void setSettingvalue(String settingvalue) {
        this.settingvalue = settingvalue;
    } 
    
    
}

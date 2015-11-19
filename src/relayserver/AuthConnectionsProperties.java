package relayserver;

import common.LocalProperties;

public class AuthConnectionsProperties extends LocalProperties {
    private String primaryHost;
    private String primaryPort;
    private String primarySid;
    private String primaryUser;
    private String primaryPwd;
    private String altHost;
    private String altPort;
    private String altSid;
    private String altUser;
    private String altPwd;
    private int loginTimeout;
    private static int LOGIN_TIMEOUT_DEFAULT = 5;

    public AuthConnectionsProperties(String fileName) throws Exception {
        super(fileName);
        validateProps();
    }

    private void validateProps() throws Exception {
        //check we have all the ora details we need keep ready for use

        primaryHost = getStoredProperty("primary_host");
        primaryPort = getStoredProperty("primary_port");
        primarySid = getStoredProperty("primary_sid");
        primaryUser = getStoredProperty("primary_user");
        primaryPwd = getStoredProperty("primary_pwd");
        altHost = getStoredProperty("alt_host");
        altPort = getStoredProperty("alt_port");
        altSid = getStoredProperty("alt_sid");
        altUser = getStoredProperty("alt_user");
        altPwd = getStoredProperty("alt_pwd");

        loginTimeout =
                getStoredProperty("login_timeout", LOGIN_TIMEOUT_DEFAULT);
        
        if (loginTimeout <= 0) {
          loginTimeout = LOGIN_TIMEOUT_DEFAULT;
        }
    }

    private String getStoredProperty(String propName) throws Exception {
        String returnProperty;
        returnProperty = this.getProperties().getProperty(propName);
        if (returnProperty == null) {
            throw new Exception(propName + " is missing from " +
                                this.getFileName() + "!");
        }
        return returnProperty;
    }

    private int getStoredProperty(String propName,
                                  int defaultValue) throws Exception {
        String propValue;
        propValue = this.getProperties().getProperty(propName);

        int returnProperty = 0;

        if (propValue == null) {
            returnProperty = defaultValue;
        } else {
            try {
                returnProperty = Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                returnProperty = defaultValue;
            }
        }
        return returnProperty;
    }


    public String getPrimaryHost() {
        return primaryHost;
    }


    public String getPrimaryPort() {
        return primaryPort;
    }


    public String getPrimarySid() {
        return primarySid;
    }


    public String getPrimaryUser() {
        return primaryUser;
    }


    public String getPrimaryPwd() {
        return primaryPwd;
    }


    public String getAltHost() {
        return altHost;
    }


    public String getAltPort() {
        return altPort;
    }


    public String getAltSid() {
        return altSid;
    }


    public String getAltUser() {
        return altUser;
    }


    public String getAltPwd() {
        return altPwd;
    }

    public int getLoginTimeout() {
        return loginTimeout;
    }
    
    public String toString() {
     return   
      "primaryHost=" +primaryHost+
      ",primaryPort="+primaryPort+
      ",primarySid="+primarySid+
      ",primaryUser="+primaryUser+
      ",primaryPwd="+primaryPwd+
      ",altHost="+altHost+
      ",altPort="+altPort+
      ",altSid="+altSid+
      ",altUser="+altUser+
      ",altPwd="+altPwd+
      ",loginTimeout="+loginTimeout;     
    }
    
    
}

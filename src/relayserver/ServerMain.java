package relayserver;

import common.security.SecureSocketFactory;
import common.Constants;
import java.util.*;
import java.io.*;

/**
 * Main entry point for TelnetRelay Servers. This reads the properties file and
 * launches other bits depending on the command line options.
 *
 */
public class ServerMain {

    private static final String VERSION = "Telnet Relay Version 13_01_00";
    private static final String PROPSFILE = "TelnetRelayServer.ini";
    private static final String DBFILE = "TelnetRelayDB.txt";
    private static final String PATCHFILE = "PatchInterfaceDB.txt";

    /**
     * Creates new Main
     */
    private ServerMain() {
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        System.out.println(VERSION);
        //initialise the secure socket factory for use throughout
        SecureSocketFactory ssf = new SecureSocketFactory();


        // startup for relay server
        loadProperties(PROPSFILE);
        if (Constants.properties == null) {
            return;
        }
        String ip = (String) Constants.properties.get("RELAYSERVER");
        if (ip == null) {
            System.out.println("Property RELAYSERVER not defined.");
            return;
        }
        String compat = (String) Constants.properties.get("RELAYCOMPATIBILITYVERSION");
        if (compat == null) {
            System.out.println("Property RELAYCOMPATIBILITYVERSION not defined.");
            return;
        }
        String logfile = null;
        if (args.length > 1) {
            logfile = args[1];
        }
        String patchfile = PATCHFILE;
        if (args.length > 2) {
            patchfile = args[2];
        }
        try {
            RelayServer rs = new RelayServer(ip, logfile, patchfile, compat); //create and launch
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

}

private static void loadProperties(String filename) {
        try {
            Properties p = new Properties();
            FileInputStream fis = new FileInputStream(filename);
            p.load(fis);
            Constants.properties = p;
            Constants.runningPlatform = p.getProperty("RUNNINGPLATFORM")
                                                        .trim().toUpperCase();  
        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
    }
    
    public static Properties getProperties() {
        return Constants.properties;
    }

    public static String getVersion()
    {
      return VERSION;
    }
}

package relayserver;

import common.security.SecureSocketFactory;
import common.Constants;
import java.util.*;
import java.net.*;
import java.io.*;
import relayserver.PatchInterfaceDB;
import common.Logger;
import common.FileLogger;

/**
 *
 * This owns a ServerSocket, and listens for connections from RelayClients.
 * Whan a connection arrives, it creates and runs a RequestServicer for that connection.
 *
 */
public class RelayServer implements Runnable {

    //static final int PORT = 44276;
    private static final int BACKLOG = 10;

    private String ipString;
    private String relayCompatibilityVersion;
    private ServerSocket ssock;
    private boolean running = false;
    private Logger logger;
    private String lineSep;
    private String name = "Telnet Relay Server";
    private String config;
    private Thread myThread;
    private List logLines = new ArrayList();


    public volatile PatchInterfaceDB patchdb;  
    private volatile boolean closed;
    private Constants constants;


    /**
     * Create an idle RelayServer.
     */
    public RelayServer() {
        lineSep = System.getProperty("line.separator");
    }

    /** Create a running relay server.
     * Use close() when finished with it.
     * If LogFile is null, messages go to System.out.
     */
    public RelayServer(String listenIp, String logFile, String patchFileName, String compat, Constants c, Logger l) throws Exception {
        constants = c;
        logger = l;
        relayCompatibilityVersion = compat;
        patchdb = new PatchInterfaceDB(patchFileName);
        if(logFile == null) {
            start("RelayServer", listenIp, null);
        } else {
            start("RelayServer", listenIp + " " + logFile, null);
        }
    }

    /** Start a service.
     * @param name is a name for the service.
     * @param config is the configuration arguments. These are:
     * 1) the listening IP address. 2) Optional log file name
     * @param finder allows Services to find other services by name
     * in order to interact. E.g. a syslog parsing service might want to
     * find a syslog logging service and register as a listener.
 */
//     public void start(String name, String config,
//             uk.co.cwcom.serviceman.Finder finder) throws Exception {
    public void start(String name, String config, serviceman.Finder f) throws Exception {
        this.name = name;
        this.config = config;
        setRunningFlag(true);
        myThread = new Thread(this);
        myThread.start();
    }

    private synchronized void setRunningFlag(boolean b) {
        running = b;
    }

    public void run() {
        Thread.currentThread().setName("RelayServer");
        try {
            // decode the config line
            StringTokenizer t = new StringTokenizer(config);
            ipString = t.nextToken();
            String logfilename = null;
            if(t.hasMoreTokens()) {
                logfilename = t.nextToken();
            }
            logger.log("Relay Server starting.");
            // create the socket
            InetAddress bindAddress = InetAddress.getByName(ipString);
//            ssock = new ServerSocket(PORT, BACKLOG, bindAddress);
            SecureSocketFactory ssf = new SecureSocketFactory(logger);
            ssock = ssf.getSSLServerSocket(Constants.RELAY_PORT, BACKLOG, bindAddress);
           
            // loop listening for client connections and making RelayServers for them
            while(true) {
                Socket sock = ssock.accept();
                RelayWorker rw = new RelayWorker(sock, logger, patchdb, relayCompatibilityVersion );
                new Thread(rw).start();
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        } finally {
            close();
        }
    }

    /** Close (stop) a service once it is no longer wanted.  */
    public void close() {
        logger.log("Relay Server closing.");
        setRunningFlag(false);
        try {
            ssock.close();
        } catch(Exception ex) {
        }
    }

    /** See if the service is still running. This should return
     * false if the Service stops for <b>any</b> reason.
    */
    public synchronized boolean isRunning() {
        return running;
    }

}

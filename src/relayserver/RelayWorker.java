package relayserver;

import java.io.*;
import java.net.*;
import common.Logger;
import common.Constants;
import common.RelayRequest;
import common.RelayHelperln;
import common.RelayReply;
import common.RelayHelper;
import ping.TraceRoutePingConverter;

/**
 * This does the relay work for one client connection.
 * It is given a Socket and then run.
 * It decodes the relay request (with timeout), then makes the onward call,
 * then begins relaying.
 * It creates a RelayHelper to relay data in the reverse direction.
 *
 */
public class RelayWorker implements Runnable {

    private Socket clientSocket;
    private Socket hostSocket;
    private InputStream clientIn;
    private OutputStream clientOut;
    private InputStream hostIn;
    private BufferedReader hostErr;
    private OutputStream hostOut;
    private Logger logger;
    private String relayCompatibilityVersion;
    private PatchInterfaceDB patchifdb;
    private final Constants constants;


    /**
     * Constructor for objects of class RelayWorker
     */
    public RelayWorker(Socket clientSocket, Logger logger, PatchInterfaceDB pidb, String relayCompatibilityVersion, Constants c) {
        constants = c;
        this.clientSocket = clientSocket;
        this.logger = logger;
        this.relayCompatibilityVersion = relayCompatibilityVersion;
        this.patchifdb = pidb;
    }

    
    // inner class to declare internal PatchingNotSupported execption
    private class PatchingNotSupportedException extends Exception 
    {
      private String appName;
      //constructor without parameters


     //constructor for exception description
     public PatchingNotSupportedException(String description, String appName)
     {
        super(description);
        this.appName = appName;
     }
     
     public String getAppName ()
     {
       return this.appName;
     }
     
    }
    
    
    /** Do the work. This is a long duration call, so it is best to create a
     * new Thread to do it. */
    public void run() {
        Process p = null;
        Thread.currentThread().setName("RelayWorker");
        boolean forwarding = false;
        try {
        
            clientIn = clientSocket.getInputStream();
            clientOut = clientSocket.getOutputStream();
            // get the request (with timeout)
            clientSocket.setSoTimeout(10000);
            RelayRequest rrq = common.RelayRequest.read(clientIn);
            logger.log(rrq.toString());
            clientSocket.setSoTimeout(0);
            //check version compatability. 0 means dont check
            if ( !relayCompatibilityVersion.equals("0") &&
                 !rrq.isVersionCompatable(relayCompatibilityVersion))
            {
              throw new Exception("Client version incompatible with relay server");
            }

            //determine if call relaying or running a local request
            if (rrq.appName.equals("PING")||rrq.appName.equals("TRACEROUTE"))
            {
              String cmdName = rrq.platform+rrq.appName+"COMMAND";
              String cmd = constants.getProperties().getProperty(cmdName);
              if (cmd == null)
                   throw new Exception(rrq.appName+
                       " COMMAND not defined for "+rrq.platform);
              /*
               * substitute parameters into command
               */
              if (rrq.options != null)
              {
                String options[] = common.IoUtil.split(rrq.options,',');
                for (int i=0;i<options.length;i++)
                {
                  String opt[] = common.IoUtil.split(options[i],'=');
                  cmd = common.IoUtil.replace(cmd,opt[0],opt[1]);
                }
              }
              cmd = common.IoUtil.replace(cmd,"<DESTIP>",rrq.relayDstIp);
              cmd = common.IoUtil.replace(cmd,"<SOURCEIP>",rrq.relaySrcIp);

              p = Runtime.getRuntime().exec(cmd);
              hostErr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
              /*
               * at the time of writing, ping on vcms was implemented via
               * traceroute. The output from traceroute is converted to look
               * like ping. The test allows switching to ping by simply changing
               * the command in the ini file (if we ever get a ping that works
               * on vcms.)
               */
              if (rrq.platform.equals("VCMS") && rrq.appName.equals("PING")&&
                  (cmd.startsWith("/usr/sbin/traceroute") |
                   cmd.startsWith("tracert")))
              {
                hostIn = new TraceRoutePingConverter( p.getInputStream(), hostErr);
              }else
              {
                hostIn = p.getInputStream();
              }
              hostOut = p.getOutputStream();
            }else if (rrq.appName.equals("CONFIG"))
            {
              hostIn = new ConfigCollectorStream(rrq, constants);
              hostOut = System.out;
            }else  // It must be a relay request, IP cross connect patches and any other application 
            {
              if (rrq.appName.startsWith("TCP-"))  // check IP Cross Connect Patch Request is Authorised
              {
                // only accept if the requested Relay Source Ip address is specified in the Patch interface file.
                if (patchifdb.patchSrcList.contains(rrq.relaySrcIp.trim())) 
                {
                  logger.log(rrq.toString().replaceFirst(rrq.KEY,"RequestAccepted:"));
                }
                else 
                {
                  String msg = rrq.toString().replaceFirst(rrq.KEY,"RequestRejected:");
                  logger.log(msg);
                  throw new PatchingNotSupportedException(msg, rrq.appName);
                }                
              } 
              // place the ongoing call
              logger.log("About to place ongoing call to destination " + rrq.relayDstIp + " on port " + rrq.relayDstPort);
              InetAddress localAddr = InetAddress.getByName(rrq.relaySrcIp);
              logger.log("localAddr = " + localAddr);
              
              hostSocket = new Socket(rrq.relayDstIp, rrq.relayDstPort, localAddr, 0);
              logger.log("hostSocket = " + hostSocket);
              hostIn = hostSocket.getInputStream();
              logger.log("hostIn = " + hostIn);
              hostOut = hostSocket.getOutputStream();
              logger.log("hostOut = " + hostOut);
            }
            // send the response
            new RelayReply(true, "Go ahead, caller.").write(clientOut);
            logger.log("success message sent back to client");
            forwarding = true;
            // create the helpers, one for each direction
            RelayHelper rh1 = null;
            if (rrq.appName.equals("TRACEROUTE"))
            {
                rh1 = new RelayHelperln(hostIn, clientOut, hostErr);
            }else
            {
              rh1 = new RelayHelper(hostIn, clientOut);
            }
            RelayHelper rh2 = new RelayHelper(clientIn, hostOut, p);
            new Thread(rh1).start();
            // loop forwarding data
            rh2.run();
        }catch(PatchingNotSupportedException pex) {
            pex.printStackTrace();
            if(!forwarding) {
                try {
                    String s = "TCP patching request " + pex.getAppName() + " is not supported. Please contact your key user";
                    new RelayReply(false, s).write(clientOut);
                } catch(Exception ex2) {
                }
            }
        } 
        catch(Exception ex) {
           logger.log("Exception occured " + ex);

            ex.printStackTrace();
            if(!forwarding) {
                try {
                    String s = ex.getMessage();
                    if(s.length() == 0) {
                        s = ex.toString();
                    }
                    new RelayReply(false, s).write(clientOut);
                } catch(Exception ex2) {
                }
            }
        } finally {
            // clean up all used resources
            if (p != null) {
              //System.out.println("Killing process");
              p.destroy();
            }
            //System.out.println("relay worker closing down");
            common.IoUtil.close(clientIn);
            common.IoUtil.close(clientOut);
            common.IoUtil.close(clientSocket);
            common.IoUtil.close(hostIn);
            common.IoUtil.close(hostErr);
            common.IoUtil.close(hostOut);
            common.IoUtil.close(hostSocket);
        }
    }




    private String dateTime() {
        long now = System.currentTimeMillis();
        java.sql.Date d = new java.sql.Date(now);
        java.sql.Time t = new java.sql.Time(now);
        return d + " " + t;
    }


}
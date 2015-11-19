package relayserver;
/*
 * Class submits an individual config collection request and
 * supports returning feedback and the config back to a client process
 * The threaded run method does the action and populates an output buffer, 
 * whilst the instance acts as an input stream to service the output buffer 
 * to the client.
 */
import java.io.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import common.IoUtil;
import configcollector.CiscoSnmpCollector;
import configcollector.CiscoSnmpJob;
import configcollector.Syslogger;
import common.buffwriter.BuffWriterStream;
import common.buffwriter.OutDirector;
import common.Constants;
import common.RelayRequest;
 
public class ConfigCollectorStream extends BuffWriterStream
{
  private RelayRequest rrq;
  private Constants constants;
  private static final int MAX_TIMEOUT_INTERATIONS = 20;

  public ConfigCollectorStream(RelayRequest rrq, Constants c)
  {
    super();
    constants = c;
    this.rrq = rrq;
    new Thread(this).start();
  }

  /**
   * run method carries out the config collection and handles the reading of
   * the config for return to the client
   */
  public void run()
  {
    // get and kep the properties, we use them a lot
    Properties props = constants.getProperties();
    // build the necerssary structures for the config request
    List syslogTargets = new ArrayList();
    CiscoSnmpCollector csc = null;
    Syslogger syslogger = null;
    String ftpIp = null;
    String ccProhibited = null;

    writeln("Starting config collection for "+rrq.deviceName);

    try {
      // some devices require directory backup ... this is not supported by manual config collection
      ccProhibited = (String) props.get("CC_PROHIBITED");
    
      ftpIp  = (String) props.get("RELAYSERVER");
      // build a collector job
      
			String[] options = common.IoUtil.split(rrq.options, ':');
      String protocol = options[0];
      String type = options[1];
      String filename = options[2];
   
      if (ccProhibited.indexOf(type) != -1)
      {
        writeln("Directory based config collection cannot be invoked manually");
        throw new Exception("Error, directory based config collection cannot be invoked manually");
      }
   
      String line =
                  protocol+"\t"+
                  rrq.relaySrcIp+"\t"+
                  rrq.deviceName+"\t"+
                  ((protocol.equals("SNMP")||protocol.equals("CASCADE")) ? rrq.relayDstIp+"\t"+rrq.writeCommunity+"\t" : rrq.relayDstIp+"\t")+
                  rrq.relaySrcIp+"\t"+
                  filename+"\t"+
                  type;
                  
      // create the job
      CiscoSnmpJob csjob = new CiscoSnmpJob(line);
      // give the job to the collector
      if(csc == null) {
        csc = new CiscoSnmpCollector();
      }
      csc.add(csjob);

      // build syslogger
      String syslogProp = null;

      syslogProp  = (String) props.get("SYSLOGTO");
      StringTokenizer st = new StringTokenizer(syslogProp);
      while(st.hasMoreTokens()) {
        syslogTargets.add(st.nextToken());
      }

      // Create a Syslogger if needed
      if(syslogTargets.size() > 0) {
        syslogger = new Syslogger();
        for(int x = 0 ; x < syslogTargets.size() ; x++) {
          String s = (String) syslogTargets.get(x);
          syslogger.addTarget(s);
        }
      }
      
      // probably a redundant check but do it anyway
      if (csc == null)
      {
        throw new Exception("Error, no config job established");
      }

      // if we have sysloggers then add them to the config job
      if(syslogger != null) {
        csc.setLogger(syslogger);
      }

      // run the collection redirecting output to our buffer
      OutDirector od = new OutDirector(this); // redirect output from csc to us
      csc.run();
      od.resetOut(); // rest system.out back to where it was before
      od = null;

      // see if a success
      if (csc.getSuccesses() == 0) 
      {
        throw new Exception("Config collection failed!");
      }

      /*
       * we need to give the tftp server a chance to put the file where we want 
       * it, so have a kip, then see if it is there. (A bit clumsy but we've
       * done worse before and will again)
       */
      // build the file name to get
      String fileDir = (String)props.get(rrq.platform + "_TFTPFOLDER");
      fileDir = common.IoUtil.replace(fileDir,"<DOMAIN>",rrq.domain).trim();
      int attempts = 0;
      File file = null;
      do
      {
        try
        {
          Thread.currentThread().sleep(1000);
        }catch(Exception e){}
      
        file = new File(fileDir,csjob.getFname());
        if (file != null && file.isFile()) break;
      }while(attempts++ < MAX_TIMEOUT_INTERATIONS);
             
      if (file == null || !file.isFile()) 
      {
        writeln("config file not found");
        throw new FileNotFoundException();
      }

      // read the file and punt it to the output buffer
      List fileLines;
      try{
        fileLines = common.IoUtil.readFileLines(file.getAbsolutePath().trim());
      }catch(Exception e)
      {
        e.printStackTrace();
        writeln("Error reading config");
        writeEnd();
        return;
      }

      // mark display end with ##DISPLAYEND##
      writeln("##DISPLAYEND##");
      // write the file name to the out buffer first
      writeln(file.getName().trim());
      // now dump the file contents 
      for (Iterator i=fileLines.iterator();i.hasNext();)
      {
        writeln((String)i.next());
      }
      // all done now so send end tag to kill the stream
      writeEnd();
    } catch(Exception ex) {
      ex.printStackTrace();
      write("Error running config collection");
      writeEnd();
      return;
    }
    return;
  }
}
package ping;
/**
 * TraceRoutePingConverter.java
 * Provides a wrapper for an InputStream to convert the output of
 * a traceroute to look like a ping.
 * OK not nice but Ping doesn't work on the vCMS platform.
 */
import common.Constants;
import java.io.*;
import java.net.*;
import java.util.*;
import common.IoUtil;
import ping.PingStats;

public class TraceRoutePingConverter extends InputStream 
{
  private BufferedReader trIn;
  private BufferedReader trErr;
  // extracted detail storage
  private InetAddress ipAddress = null;
  private int savedTTL = 30;
  private int savedPacketSize = 40;
  //flow control
  private boolean inStreamEmpty = false;
  private boolean doneFirstLine = false;
  // buffer for output
  private Vector outList = new Vector();
  // output control
  private int nextOutLine = 0;
  private int retLen = 0;

  // constants
  private static final int OUT_BUFF_EMPTY = -2;
  
  /**
   * Construct a new instance of TraceRoutePingConverter
   * @param trIn InputStream fromtraceroute process (hopefully!)
   */
  public TraceRoutePingConverter(InputStream trIn, BufferedReader trErr)
  {
     // wrap a buffered reader round the stream for easy usage (or lazy usage)
      this.trIn= new BufferedReader(new InputStreamReader(trIn));
      this.trErr= trErr;
  }
  /**
   * have to provide this but don't want to support it so please don't use it
   */
  public int read() throws IOException{
    return 0;
  }
  /**
   * Over ride of super method where all the work happens.
   * There are three phases :- read, process and send
   * The read phase fishes for lines we are interested in
   * The process phase strips the data out
   * The send phase returns the next available line of ping output.
   */
  public int read(byte[] b) throws IOException
  { 
    String inLine = null;
    // read phase will only exit when a non empty line is read or
    // the stream has been emptied. (read null)
    do {
      /*
       * would you beleive that on unix traceroute outputs the header line
       * on standard err! so we have to pull it out specially!!!!!!!!!!:-(
       * dont want to block on it incase it varies from unix to unix so
       * will give it a sec, test it, then move on if there is none
       */
      if (!doneFirstLine && Constants.runningPlatform.equals("UNIX"))
      {
        try
          {
            Thread.currentThread().sleep(3000);
          }catch(Exception e)
          {
            e.printStackTrace();
          }
        if (trErr.ready()){
          inLine = trErr.readLine();
        }else
        {
          do {
            inLine = trIn.readLine();
          } while (inLine != null && inLine.trim().length() == 0 ); 
          if (inLine == null) inStreamEmpty = true; // if nothing else to read 
            
        }
      }else if (!inStreamEmpty) {
        do {
         inLine = trIn.readLine();
        } while (inLine != null && inLine.trim().length() == 0 ); 
        if (inLine == null) inStreamEmpty = true; // if nothing else to read 
      } //!inStreamEmpty

      // process phase only if we have something to proces

      if (inLine != null)
      {
        //first line needs the IP stripped and maybe packet size and ttl
        if (!doneFirstLine)
        {
          processFirstLine(inLine);
          doneFirstLine = true;
          if (ipAddress == null)
          {
            return -1; // we haven't found an IP adress so give up
          }else
          {
            // successfully processed the first line so build a ping header line
            // and send to output buffer.
            addOutLine("Pinging "+ipAddress.getHostAddress()+" with "+
                        savedPacketSize+
                         " bytes of data :\n\n");
          }
        }else // not the first line so must be a trace line so proces.
        {
          String [] trLine = IoUtil.stringSplitter(inLine.trim()," ");
          if (trLine.length > 3) { // ignore fluff and pad lines
            // strip out the stats
            PingStats ps = new PingStats(trLine,ipAddress.getHostAddress());
            // we are only interested in the stats if it is for oiur target, and
            // the stats only record the ip for our target;
            if (ps.getIpAddress()!= null)
            {
              Vector pings = ps.getPings();
              for (int i=0;i<pings.size();i++)
              {
                addOutLine("Reply from "+ipAddress.getHostAddress()+
                             ": bytes = "+ savedPacketSize+
                              " time="+(String)pings.get(i)+"ms"+
                              " TTL="+savedTTL+"\n");
              }
              addOutLine("\n"+ps.toString());
            }
          }
        }
      }
      // send phase
      retLen = sendOutLine(b);
    } while(retLen == OUT_BUFF_EMPTY); // block if nothing in the buffer
    
    return retLen; 
  }

  /**
   * extract headre details from the first line of the trace route.
   * (Pulled out for clarity only)
   * @param firstLine the string to be processed.
   */
  private void processFirstLine(String firstLine)
  {
    boolean haveNumber = false;
    int ourNumber = 0;
    
    String [] tokens = IoUtil.stringSplitter(firstLine, " ");
    // scan through the tokens and pick out the target IP and if poss
    // the packet size and ttl values
    tokenPass: for (int i = 0;i<tokens.length;i++)
    {
      if (ipAddress == null)
      {
        try
        {
          if (tokens[i].trim().indexOf('.')!= -1)
                               ipAddress = InetAddress.getByName(tokens[i]);
          continue tokenPass;
        }catch (Exception ex)
        {
          ipAddress = null;
        }
      }
      // if we have a number stored then look for key words
      //    hops = ttl
      //    bytes yields packet size
      if (haveNumber && tokens[i].trim().equalsIgnoreCase("hops"))
      {
        savedTTL = ourNumber;
        haveNumber = false;
        continue tokenPass;
      }else if (haveNumber && (tokens[i].trim().equalsIgnoreCase("byte")|
                               tokens[i].trim().equalsIgnoreCase("byte"))) 
      {
        savedPacketSize = ourNumber;
        haveNumber = false;
        continue tokenPass;
      }else if (!haveNumber) // could be a number so store for next cycle.
      {
        try
        {
          ourNumber = Integer.parseInt(tokens[i].trim());
          haveNumber = true;
        }catch (Exception ex)
        {
          ourNumber = 0;
        }
      }
      
    }
  }

  /**
   * add line to output buffer
   * @param line line to be added to output buffer
   */
  private void addOutLine(String line)
  {
    outList.add(line);
  }

  /**
   * Populate supplied byte array with next available line of output and
   * return the length. Alternatively return finished code -1 or
   * buffer empty -2 to block.
   */
  private int sendOutLine(byte [] b) throws IOException
  {
    if (inStreamEmpty && nextOutLine >= outList.size()) return -1;
    if (nextOutLine >= outList.size()) return OUT_BUFF_EMPTY;

    byte [] buf = ((String)outList.get(nextOutLine++)).getBytes();
    System.arraycopy(buf,0,b,0,buf.length);
    return buf.length;
    
  }
}
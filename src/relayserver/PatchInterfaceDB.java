package relayserver;


import java.io.*;
import java.util.*;
import java.net.*;
import common.IoUtil;

/**
 * New class added to server package in order to perform continuous 
 * reading of PatchInterfaceDB text file.  This text file lists the 
 * source Relay IP addresses of Relay Servers that supports patching 
 * on this interface address out of the server to the customer domain.
 * 
 */

public class PatchInterfaceDB implements Runnable { 
    
    private String fileName;
    private boolean fnfReported = false;  //flag indicates if FileNotFound has been reported already.
    private volatile boolean closed; // flag to kill the thread
    private long lastFileLoad = -1;
    public Set patchSrcList = new LinkedHashSet(); // Set of the patch sources addresses provided in the file
    private volatile Thread myThread;


    /**
     * Create a PatchInterfaceDB and start the file refresh thread.
     * Use close() to kill the thread.
     */
    public PatchInterfaceDB(String filename) {
        fileName = filename;
        new Thread(this).start();
    }
    
    /** Iterate the Patch sources IP address list lines. */
    public synchronized Iterator patches() {
        return patchSrcList.iterator();
    }
    
    /**
     * Infinite loop checking the file date once a minute. 
     * re-reads the file if changed.
     */
    public void run() {
        if(myThread != null) {
            throw new RuntimeException("Thread already running!");
        }
        myThread = Thread.currentThread();
        myThread.setName("PatchInterfaceDB file watcher");
        while(!closed) {
            readFile();
            try {
                Thread.sleep(60000);
            } catch(InterruptedException ie) {
            }
        }
    }
    
    /** Read the database file only if it has been modified. */
    private void readFile() {
        File f = new File(fileName);
        long lastMod = f.lastModified();
        if(lastFileLoad == lastMod) {
            return;
        }
        Set newPatchList = new LinkedHashSet();

        try {
            List lines = IoUtil.readFileLines(fileName);
            String sourceIP = null;
                        
            for(int x = 0 ; x < lines.size() ; x++) {
                
                String line = (String) lines.get(x);                
                
                if(line.startsWith("SRCIP\t")) {
                    String[] fields = IoUtil.split(line, '\t');
                    if(fields.length > 1) {
                        // obtain and store the source IP address (with blanks removed)                                         
                        sourceIP = fields[1].trim();
                        newPatchList.add(sourceIP);
                    }
               }
            }
            synchronized(this) {
                lastFileLoad = lastMod;
                patchSrcList = newPatchList;
            }
            System.out.println(dateTime() + " Patch Relay Source IP file loaded: " + fileName);
            fnfReported = false; //reset flag in case file is renamed or removed
        } catch (FileNotFoundException fnfEx) {
           // only report file not found once 
           if (!fnfReported)
           {
             System.out.println(dateTime() + " Patch Relay Source IP file: " + fileName + " not found.");
             fnfReported = true;
           }
        
        }
        catch(Exception ex) {
            System.out.println(dateTime() + " Can't read database Relay Source IP file: " + fileName);
            ex.printStackTrace();
        }
    }
    
    private String dateTime() {
        long now = System.currentTimeMillis();
        java.sql.Date d = new java.sql.Date(now);
        java.sql.Time t = new java.sql.Time(now);
        return d + " " + t;
    }
    
    public void close() {
        closed = true;
        if(myThread != null) {
            myThread.interrupt();
        }
    }
}

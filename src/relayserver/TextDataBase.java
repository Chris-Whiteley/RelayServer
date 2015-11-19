package relayserver;


import java.io.*;
import java.util.*;
import java.net.*;
import common.IoUtil;

/**
 * This class caches a text database of domains and devices.
 * 
 */
public class TextDataBase implements Runnable {
    
    private String fileName;
    private volatile boolean closed; // flag to kill the thread
    private long lastFileLoad = -1;
    private Map domMap = new HashMap(); // Map of domainName -> Domain
    private Map devListMap = new HashMap(); // domainName -> List of devices
    private Map devMap = new HashMap(); // Map devName -> device
    private volatile Thread myThread;

/* Database format:
!
! key	  cust name	    ss t2 ip	domain t1 ip	domain	iv t2 ip  platform
!
DOMAIN	Local_testing	127.0.0.1	148.185.82.42	LOCAL	127.0.0.1 VCMS
!
! key	  device.domain	device ip	    iv_t2_ip	wrt cs	cc  access
!
DEVICE	iptndev.local	172.50.72.40	127.0.0.1	private	Y TELNET
*/

    /**
     * Create a TextDataBase and start the file refresh thread.
     * Use close() to kill the thread.
     */
    public TextDataBase(String filename) {
        fileName = filename;
        new Thread(this).start();
    }
    
    /** Iterate the DOMAIN lines. */
    public synchronized Iterator domains() {
        return domMap.values().iterator();
    }
    
    /** Iterate the Devices in a domain. 
     * Returns null if no such domain. */
    public synchronized Iterator devices(String domainName) {
        List devs = (List) devListMap.get(domainName);
        if(devs == null) {
            return null;
        }
        return devs.iterator();
    }
    
    /** Get one Domain by name. */
    public synchronized String getDomain(String name) {
        return (String) domMap.get(name);
    }
    
    /** Get one Device by name. */
    public synchronized String getDevice(String name) {
        return (String) devMap.get(name);
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
        myThread.setName("TextDataBase file watcher");
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
        Map newDevMap = new HashMap();
        Map newDomMap = new HashMap();
        Map newDevListMap = new HashMap();
        try {
            List lines = IoUtil.readFileLines(fileName);
            String domName = null;
            List devList = null;
            for(int x = 0 ; x < lines.size() ; x++) {
                String line = (String) lines.get(x);
                if(line.startsWith("DOMAIN\t")) {
                    String[] fields = IoUtil.split(line, '\t');
                    if(fields.length > 2) {
                        domName = fields[1];    // keep this name for following DEVICE lines
                        String domStr = line.substring(7);
                        newDomMap.put(domName, domStr);
                        devList = new ArrayList();
                        newDevListMap.put(domName, devList);
                    }
                }
                else if(line.startsWith("DEVICE\t")) {
                    String[] fields = IoUtil.split(line, '\t');
                    if(fields.length > 2) {
                        String name = fields[1];
                        String devStr = line.substring(7);
                        devList.add(devStr);
                        newDevMap.put(name, devStr);
                    }
                }
            }
            synchronized(this) {
                lastFileLoad = lastMod;
                devMap = newDevMap;
                domMap = newDomMap;
                devListMap = newDevListMap;
            }
            System.out.println(dateTime() + " File loaded: " + fileName);
        } catch(Exception ex) {
            System.out.println(dateTime() + " Can't read database file: " + fileName);
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

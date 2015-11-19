package relayserver;
import java.io.*;
import java.util.*;
import java.net.*;
import common.IoUtil;

/**
 * This class caches a file as an array list to served to clients
 */

public class ListDataBase implements Runnable
{
    private String fileName;
    private volatile boolean closed; // flag to kill the thread
    private long lastFileLoad = -1;
    private List fileList = null;
    private volatile Thread myThread;

  public ListDataBase(String filename) {
        fileName = filename;
        new Thread(this).start();
    }
    /** Read the database file only if it has been modified. */
    private void readFile() {
        File f = new File(fileName);
        long lastMod = f.lastModified();
        if(lastFileLoad == lastMod) {
            return;
        }
        List newList = null;
        try {
            newList = IoUtil.readFileLines(fileName);
            synchronized(this) {
                lastFileLoad = lastMod;
                fileList = newList;
            }
            System.out.println(dateTime() + " File loaded: " + fileName);
        } catch(Exception ex) {
            System.out.println(dateTime() + " Can't read database file: " + fileName);
            ex.printStackTrace();
        }
    }
    /** return an iterator for the list. */
    public synchronized Iterator iterator() {
        return fileList.iterator();
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
        myThread.setName("ListDataBase file watcher");
        while(!closed) {
            readFile();
            try {
                Thread.sleep(60000);
            } catch(InterruptedException ie) {
            }
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
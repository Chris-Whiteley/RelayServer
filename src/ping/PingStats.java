package ping;

/*
 * PingStats.java
 * Calculates ping stats from a traceroute output line.
 * Used to convert traceroute output to ping details for vcms.
 */
import java.net.InetAddress;
import java.util.*;
/**
 *
 * 
 */

public class PingStats {
    private String [] vals;
    private Vector pings = new Vector(); // returnable list of stats
    // declare and initialise stats
    private double minVal = 9999.9999; // big for initial min compare
    private double maxVal = 0.0;
    private double totVal = 0.0;
    private double avVal  = 0.0;
    private int numVals = 0;
    private int numLost = 0;
    private double lostPercent = 0.0;
    private String ipAddress; // the ping target (atleast for this traceroute)
    /** 
     * Creates a new instance of PingStats:-
     * Process the traceroute line extracting the ping stats and calculates the 
     * stats.
     * It also produces a vector of the individual ping stats for external usage.
     * @param vals string array representation of a traceroute line
     * @param targetIp the ip address of the 'ping' target
     */
    public PingStats(String [] vals, String targetIp) {
        this.setVals(vals);
        for (int i=1;i<vals.length;i++){ // ignore the first field as route number
                                         // from traceroute.
            if (vals[i].trim().length() == 0) continue; // ignore empty/blank lines
            // try and process the entry as a number first since there are more
            // of them than anything else.
            try{
                processValue(vals[i]);
            }catch(NumberFormatException nfe){
                // not a number so could be :-
                //  * a dropped packet
                // !<c> an error code (treat as dropped packet)
                // <1 on windows ie less than 1 ms treat as 0.5
                // an ip address, see if it is the one we want. (It should be!!)
                
                if (vals[i].trim().equals("*")||
                    vals[i].indexOf("!") != -1){
                    setNumLost(getNumLost() + 1);
                }else if (vals[i].trim().equals("<1")){
                    processValue("0.5"); // on av it will be OK (windows anyway)
                }else if (vals[i].trim().equals(targetIp)){
                    try{
                        setIpAddress(vals[i]);
                    }catch(Exception e){
                        setIpAddress(null);
                    }
                }
            }
        }
        // if we didn't have any valid values then set the min to 0.
        // (it was initialised to abig number for the min test!!! one solution!)

        if (getNumVals() == 0) setMinVal(0.0);
        
        // calculate summary stats.
        setAvVal(rounder(getTotVal() / getNumVals(),3));
        setLostPercent(rounder(((double)getNumLost() /(double) (getNumVals() +
                                             getNumLost())) * 100.0,1));
        /*
         * traceroute on unix returns n * line if host not reached so have to
         * handle
         */
         if (getLostPercent() == 100.0 && vals[0].trim().equals("30"))
         {
           setIpAddress(targetIp);
         }
    }

    /**
     * pull out of recurring code to try and process a number value
     */
    private void  processValue(String val) throws NumberFormatException
    {
      double dVal = 0.0;
      dVal = Double.valueOf(val).doubleValue(); //try convert string to double

      // got a  double so adjust stats accordingly
      
      setMinVal(Math.min(dVal, getMinVal()));
      setMaxVal(Math.max(dVal, getMaxVal()));
      setTotVal(getTotVal() + dVal);
      setNumVals(getNumVals() + 1);
      // keep the number for external use
      pings.add(val);
    }

    /**
     * Utility function to round double dval to precision decimal places
     */
    public static double rounder(double dval,int precision){
      double shift = Math.pow(10,(double)precision);
      return ((double)(Math.round(dval*shift)))/shift;
    }
    
    /**
     * Override toString to return the summary stats line
     */
    public String toString(){
        return "Ping statistics for "+getIpAddress()+":\n"+
                "    Packets: Sent = "+Integer.toString(getNumVals()+getNumLost())+
                ", Received = "+ Integer.toString(getNumVals())+
                ", Lost = "+Integer.toString(getNumLost())+" ("+
                Double.toString(getLostPercent())+"% loss),\n"+
                "Approximate round trip times in milli-seconds:\n"+
                "    Minimum = "+Double.toString(getMinVal())+"ms, "+
                "Maximum = "+Double.toString(getMaxVal())+"ms, "+
                "Average = "+Double.toString(getAvVal())+"ms\n";
    }

    /*
     * get and sets....
     */
    public Vector getPings()
    {
      return pings;
    }
    
    public String[] getVals() {
        return vals;
    }

    public void setVals(String[] vals) {
        this.vals = vals;
    }

    public double getMinVal() {
        return minVal;
    }

    public void setMinVal(double minVal) {
        this.minVal = minVal;
    }

    public double getMaxVal() {
        return maxVal;
    }

    public void setMaxVal(double maxVal) {
        this.maxVal = maxVal;
    }

    public double getTotVal() {
        return totVal;
    }

    public void setTotVal(double totVal) {
        this.totVal = totVal;
    }

    public double getAvVal() {
        return avVal;
    }

    public void setAvVal(double avVal) {
        this.avVal = avVal;
    }

    public int getNumVals() {
        return numVals;
    }

    public void setNumVals(int numVals) {
        this.numVals = numVals;
    }

    public int getNumLost() {
        return numLost;
    }

    public void setNumLost(int numLost) {
        this.numLost = numLost;
    }

    public double getLostPercent() {
        return lostPercent;
    }

    public void setLostPercent(double lostPercent) {
        this.lostPercent = lostPercent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
}

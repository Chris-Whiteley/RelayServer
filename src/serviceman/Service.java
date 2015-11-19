package serviceman;


/**
 * A Service which the ServiceManager can start and stop.
 * 
 */
public interface Service {

    /** Start a service. 
     * @param name is a name for the service. 
     * @param config is the configuration arguments.
     * @param finder allows Services to find other services by name
     * in order to interact. E.g. a syslog parsing service might want to 
     * find a syslog logging service and register as a listener.
     */
    public void start(String name, String config, 
            serviceman.Finder finder) throws Exception;
    
    /** Close (stop) a service once it is no longer wanted. */
    public void close();
    
    /** See if the service is still running. This should return 
     * false if the Service stops for <b>any</b> reason. 
     */
    public boolean isRunning();
}


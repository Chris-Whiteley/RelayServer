package serviceman;


/**
 * Allows a service to find another running Service by name.
 */
public interface Finder {
    
    /** Find a service by name. 
     * Returns null if the named service is not running.
     */
    public Service find(String name);
}


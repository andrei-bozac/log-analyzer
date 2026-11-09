package ro.loganalyzer.gateway;

public final class ServiceException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String serviceName;

    public ServiceException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
    }

    public String serviceName() {
        return serviceName;
    }
}
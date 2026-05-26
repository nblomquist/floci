package io.github.hectorvent.floci.services.docdb;

import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.AwsNamespaces;
import io.github.hectorvent.floci.core.common.AwsQueryResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DocDbQueryHandler {

    private static final Logger LOG = Logger.getLogger(DocDbQueryHandler.class);

    public Response handle(String action, MultivaluedMap<String, String> params) {
        LOG.infov("DocumentDB action: {0}", action);
        try {
            return AwsQueryResponse.error("UnsupportedOperation",
                    "Operation " + action + " is not supported by DocumentDB.", AwsNamespaces.RDS, 400);
        } catch (AwsException e) {
            return AwsQueryResponse.error(e.getErrorCode(), e.getMessage(), AwsNamespaces.RDS, e.getHttpStatus());
        } catch (Exception e) {
            LOG.errorv(e, "Unexpected error in DocumentDB {0}", action);
            return Response.serverError().entity("Unexpected error: " + e.getMessage()).build();
        }
    }
}

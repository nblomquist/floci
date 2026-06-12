package io.github.hectorvent.floci.services.iot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class IotDataController {

    private final IotService iotService;
    private final RegionResolver regionResolver;
    private final ObjectMapper objectMapper;

    @Inject
    public IotDataController(IotService iotService, RegionResolver regionResolver, ObjectMapper objectMapper) {
        this.iotService = iotService;
        this.regionResolver = regionResolver;
        this.objectMapper = objectMapper;
    }

    @GET
    @Path("/things/{thingName}/shadow")
    public Response getThingShadow(@Context HttpHeaders headers,
                                   @PathParam("thingName") String thingName,
                                   @QueryParam("name") String shadowName) {
        return Response.ok(iotService.getThingShadow(thingName, shadowName, regionResolver.resolveRegion(headers))).build();
    }

    @POST
    @Path("/things/{thingName}/shadow")
    @Consumes(MediaType.WILDCARD)
    public Response updateThingShadow(@Context HttpHeaders headers,
                                      @PathParam("thingName") String thingName,
                                      @QueryParam("name") String shadowName,
                                      String body) {
        try {
            JsonNode request = objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
            return Response.ok(iotService.updateThingShadow(thingName, shadowName, request, regionResolver.resolveRegion(headers))).build();
        } catch (Exception e) {
            throw new AwsException("InvalidRequestException", e.getMessage(), 400);
        }
    }

    @DELETE
    @Path("/things/{thingName}/shadow")
    public Response deleteThingShadow(@Context HttpHeaders headers,
                                      @PathParam("thingName") String thingName,
                                      @QueryParam("name") String shadowName) {
        return Response.ok(iotService.deleteThingShadow(thingName, shadowName, regionResolver.resolveRegion(headers))).build();
    }

    @GET
    @Path("/api/things/shadow/ListNamedShadowsForThing/{thingName}")
    public Response listNamedShadowsForThing(@Context HttpHeaders headers,
                                             @PathParam("thingName") String thingName) {
        ObjectNode response = objectMapper.createObjectNode();
        var results = response.putArray("results");
        iotService.listNamedShadowsForThing(thingName, regionResolver.resolveRegion(headers)).forEach(results::add);
        return Response.ok(response).build();
    }

    @POST
    @Path("/topics/{topic: .+}")
    @Consumes(MediaType.WILDCARD)
    public Response publish(@PathParam("topic") String topic, byte[] payload) {
        iotService.publish(topic, payload == null ? new byte[0] : payload);
        return Response.ok(objectMapper.createObjectNode()).build();
    }
}

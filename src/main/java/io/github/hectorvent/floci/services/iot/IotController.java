package io.github.hectorvent.floci.services.iot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.services.iot.model.Thing;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IotController {

    private final IotService iotService;
    private final RegionResolver regionResolver;
    private final ObjectMapper objectMapper;

    @Inject
    public IotController(IotService iotService, RegionResolver regionResolver, ObjectMapper objectMapper) {
        this.iotService = iotService;
        this.regionResolver = regionResolver;
        this.objectMapper = objectMapper;
    }

    @GET
    @Path("/endpoint")
    public Response describeEndpoint(@QueryParam("endpointType") String endpointType) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("endpointAddress", iotService.describeEndpoint(endpointType));
        return Response.ok(response).build();
    }

    @POST
    @Path("/things/{thingName}")
    public Response createThing(@PathParam("thingName") String thingName,
                                @Context HttpHeaders headers,
                                String body) {
        String region = regionResolver.resolveRegion(headers);
        Thing thing = iotService.createThing(thingName, parseAttributes(body), region);
        return Response.ok(buildThingResponse(thing)).build();
    }

    @GET
    @Path("/things/{thingName}")
    public Response describeThing(@PathParam("thingName") String thingName,
                                  @Context HttpHeaders headers) {
        String region = regionResolver.resolveRegion(headers);
        return Response.ok(buildThingResponse(iotService.describeThing(thingName, region))).build();
    }

    @GET
    @Path("/things")
    public Response listThings(@Context HttpHeaders headers) {
        String region = regionResolver.resolveRegion(headers);
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode things = response.putArray("things");
        for (Thing thing : iotService.listThings(region)) {
            ObjectNode summary = objectMapper.createObjectNode();
            summary.put("thingName", thing.getThingName());
            summary.put("thingArn", thing.getThingArn());
            things.add(summary);
        }
        return Response.ok(response).build();
    }

    @PATCH
    @Path("/things/{thingName}")
    public Response updateThing(@PathParam("thingName") String thingName,
                                @Context HttpHeaders headers,
                                String body) {
        String region = regionResolver.resolveRegion(headers);
        Thing thing = iotService.updateThing(thingName, parseAttributes(body), region);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("thingName", thing.getThingName());
        response.put("thingArn", thing.getThingArn());
        response.put("version", thing.getVersion());
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/things/{thingName}")
    public Response deleteThing(@PathParam("thingName") String thingName,
                                @Context HttpHeaders headers) {
        String region = regionResolver.resolveRegion(headers);
        iotService.deleteThing(thingName, region);
        return Response.ok(objectMapper.createObjectNode()).build();
    }

    private Map<String, String> parseAttributes(String body) {
        try {
            JsonNode request = objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
            JsonNode attributesNode = request.get("attributePayload");
            if (attributesNode == null || !attributesNode.has("attributes")) {
                attributesNode = request.get("AttributePayload");
            }
            if (attributesNode != null && attributesNode.has("attributes")) {
                attributesNode = attributesNode.get("attributes");
            } else if (attributesNode != null && attributesNode.has("Attributes")) {
                attributesNode = attributesNode.get("Attributes");
            }
            Map<String, String> attributes = new HashMap<>();
            if (attributesNode != null && attributesNode.isObject()) {
                attributesNode.fields().forEachRemaining(entry -> attributes.put(entry.getKey(), entry.getValue().asText()));
            }
            return attributes;
        } catch (JsonProcessingException e) {
            throw new AwsException("InvalidRequestException", e.getMessage(), 400);
        }
    }

    private ObjectNode buildThingResponse(Thing thing) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("thingName", thing.getThingName());
        response.put("thingArn", thing.getThingArn());
        response.put("thingId", thing.getThingId());
        response.set("attributes", objectMapper.valueToTree(thing.getAttributes()));
        response.put("version", thing.getVersion());
        putEpoch(response, "creationDate", thing.getCreationDate());
        putEpoch(response, "lastModifiedDate", thing.getLastModifiedDate());
        return response;
    }

    private void putEpoch(ObjectNode node, String field, Instant instant) {
        if (instant != null) {
            node.put(field, instant.toEpochMilli() / 1000.0);
        }
    }
}

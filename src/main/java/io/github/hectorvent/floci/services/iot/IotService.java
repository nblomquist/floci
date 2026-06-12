package io.github.hectorvent.floci.services.iot;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.services.iot.model.Thing;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class IotService {

    static final String DEFAULT_ENDPOINT_TYPE = "iot:Data-ATS";

    private static final Pattern THING_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9:_-]{1,128}");

    private final StorageBackend<String, Thing> thingStore;
    private final EmulatorConfig config;
    private final RegionResolver regionResolver;

    @Inject
    public IotService(StorageFactory storageFactory, EmulatorConfig config, RegionResolver regionResolver) {
        this(storageFactory.create("iot", "iot-things.json", new TypeReference<Map<String, Thing>>() {}),
                config, regionResolver);
    }

    IotService(StorageBackend<String, Thing> thingStore, EmulatorConfig config, RegionResolver regionResolver) {
        this.thingStore = thingStore;
        this.config = config;
        this.regionResolver = regionResolver;
    }

    public String describeEndpoint(String endpointType) {
        String effectiveType = endpointType == null || endpointType.isBlank() ? DEFAULT_ENDPOINT_TYPE : endpointType;
        if (!DEFAULT_ENDPOINT_TYPE.equals(effectiveType)) {
            throw new AwsException("InvalidRequestException", "Unsupported endpoint type: " + effectiveType, 400);
        }
        URI baseUri = URI.create(config.effectiveBaseUrl());
        return baseUri.getAuthority();
    }

    public Thing createThing(String thingName, Map<String, String> attributes, String region) {
        validateThingName(thingName);
        String key = thingKey(region, thingName);
        if (thingStore.get(key).isPresent()) {
            throw new AwsException("ResourceAlreadyExistsException", "Thing already exists: " + thingName, 409);
        }

        Instant now = Instant.now();
        Thing thing = new Thing();
        thing.setThingName(thingName);
        thing.setThingArn(regionResolver.buildArn("iot", region, "thing/" + thingName));
        thing.setThingId(UUID.randomUUID().toString());
        thing.setAttributes(attributes);
        thing.setVersion(1L);
        thing.setCreationDate(now);
        thing.setLastModifiedDate(now);
        thingStore.put(key, thing);
        return thing;
    }

    public Thing describeThing(String thingName, String region) {
        validateThingName(thingName);
        return thingStore.get(thingKey(region, thingName))
                .orElseThrow(() -> new AwsException("ResourceNotFoundException", "Thing not found: " + thingName, 404));
    }

    public List<Thing> listThings(String region) {
        String prefix = "thing:" + region + ":";
        return thingStore.scan(key -> key.startsWith(prefix)).stream()
                .sorted(Comparator.comparing(Thing::getThingName))
                .toList();
    }

    public Thing updateThing(String thingName, Map<String, String> attributes, String region) {
        Thing thing = describeThing(thingName, region);
        thing.setAttributes(attributes);
        thing.setVersion(thing.getVersion() + 1);
        thing.setLastModifiedDate(Instant.now());
        thingStore.put(thingKey(region, thingName), thing);
        return thing;
    }

    public void deleteThing(String thingName, String region) {
        describeThing(thingName, region);
        thingStore.delete(thingKey(region, thingName));
    }

    public Map<String, String> listTagsForResource(String resourceArn) {
        Thing thing = thingForArn(resourceArn);
        return new TreeMap<>(thing.getTags());
    }

    public void tagResource(String resourceArn, Map<String, String> tags) {
        Thing thing = thingForArn(resourceArn);
        Map<String, String> updatedTags = new TreeMap<>(thing.getTags());
        updatedTags.putAll(tags);
        thing.setTags(updatedTags);
        thingStore.put(thingKey(regionFromArn(resourceArn), thing.getThingName()), thing);
    }

    public void untagResource(String resourceArn, List<String> tagKeys) {
        Thing thing = thingForArn(resourceArn);
        Map<String, String> updatedTags = new TreeMap<>(thing.getTags());
        tagKeys.forEach(updatedTags::remove);
        thing.setTags(updatedTags);
        thingStore.put(thingKey(regionFromArn(resourceArn), thing.getThingName()), thing);
    }

    private void validateThingName(String thingName) {
        if (thingName == null || !THING_NAME_PATTERN.matcher(thingName).matches()) {
            throw new AwsException("InvalidRequestException", "Invalid thing name: " + thingName, 400);
        }
    }

    private String thingKey(String region, String thingName) {
        return "thing:" + region + ":" + thingName;
    }

    private Thing thingForArn(String resourceArn) {
        String thingName = thingNameFromArn(resourceArn);
        String region = regionFromArn(resourceArn);
        return thingStore.get(thingKey(region, thingName))
                .orElseThrow(() -> new AwsException("ResourceNotFoundException", "Resource not found: " + resourceArn, 404));
    }

    private String thingNameFromArn(String resourceArn) {
        String resource = resourceFromArn(resourceArn);
        if (!resource.startsWith("thing/") || resource.length() == "thing/".length()) {
            throw new AwsException("InvalidRequestException", "Invalid resource ARN: " + resourceArn, 400);
        }
        return resource.substring("thing/".length());
    }

    private String regionFromArn(String resourceArn) {
        String[] parts = arnParts(resourceArn);
        return parts[3];
    }

    private String resourceFromArn(String resourceArn) {
        String[] parts = arnParts(resourceArn);
        return parts[5];
    }

    private String[] arnParts(String resourceArn) {
        if (resourceArn == null || resourceArn.isBlank()) {
            throw new AwsException("InvalidRequestException", "Invalid resource ARN: " + resourceArn, 400);
        }
        String[] parts = resourceArn.split(":", 6);
        if (parts.length != 6 || !"arn".equals(parts[0]) || !"iot".equals(parts[2]) || parts[3].isBlank() || parts[5].isBlank()) {
            throw new AwsException("InvalidRequestException", "Invalid resource ARN: " + resourceArn, 400);
        }
        return parts;
    }
}

/*
 * Copyright 2018-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.odtn.impl;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.commons.lang3.tuple.Pair;
import org.onlab.graph.ScalarWeight;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ChannelSpacing;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultPath;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Direction;
import org.onosproject.net.GridType;
import org.onosproject.net.Link;
import org.onosproject.net.OchSignal;
import org.onosproject.net.Path;
import org.onosproject.net.behaviour.PowerConfig;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentId;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.odtn.GnpyService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.onosproject.net.ChannelSpacing.CHL_50GHZ;
import static org.onosproject.net.optical.util.OpticalIntentUtility.createOpticalIntent;

/**
 * Implementation of GnpyService.
 */

@Component(immediate = true, service = GnpyService.class)
public class GnpyManager implements GnpyService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;

    HttpUtil gnpyHttpUtil;

    private static final String APP_ID = "org.onosproject.odtn-service";

    private static final ProviderId PROVIDER_ID = new ProviderId("odtn", "gnpy");

    private ApplicationId appId;

    @Activate
    protected void activate() {
        log.info("Started");
        appId = coreService.getAppId(APP_ID);
    }


    @Override
    public boolean connectGnpy(String protocol, String ip, String port, String username, String password) {
        gnpyHttpUtil = new HttpUtil(protocol, ip, port);
        gnpyHttpUtil.connect(username, password);
        //TODO test connectivity
        return true;
    }

    @Override
    public boolean disconnectGnpy() {
        gnpyHttpUtil.disconnect();
        gnpyHttpUtil = null;
        return true;
    }

    @Override
    public boolean isConnected() {
        return gnpyHttpUtil != null;
    }

    @Override
    public Pair<IntentId, Double> obtainConnectivity(ConnectPoint ingress, ConnectPoint egress, boolean bidirectional) {
        ByteArrayOutputStream connectivityRequest = createGnpyRequest(ingress, egress, bidirectional);
        String response = gnpyHttpUtil.post(null, "",
                                            new ByteArrayInputStream(connectivityRequest.toByteArray()),
                                            MediaType.APPLICATION_JSON_TYPE, String.class);
        ObjectMapper om = new ObjectMapper();
        final ObjectReader reader = om.reader();
        JsonNode jsonNode;
        try {
            jsonNode = reader.readTree(response);
            if (jsonNode == null) {
                log.error("JsonNode is null for response {}", response);
                return null;
            }
            OchSignal ochSignal = createOchSignal(jsonNode);
            Map<DeviceId, Double> deviceAtoBPowerMap = new HashMap<>();
            Map<DeviceId, Double> deviceBtoAPowerMap = new HashMap<>();
            //TODO this map is currently only populated in the forward direction
            List<DeviceId> deviceIds = getDeviceAndPopulatePowerMap(jsonNode, deviceAtoBPowerMap, deviceBtoAPowerMap);
            Path suggestedPath = createSuggestedPath(deviceIds);
            for (Link link : suggestedPath.links()) {
                Device ingressDev = deviceService.getDevice(link.src().deviceId());
                if (ingressDev.is(PowerConfig.class)) {
                    if (deviceAtoBPowerMap.get(link.src().deviceId()) != -99) {
                        log.warn("Configuring power {} for {}",
                                 deviceAtoBPowerMap.get(link.src().deviceId()),
                                 link.src().deviceId());
                        ingressDev.as(PowerConfig.class)
                                .setTargetPower(link.src().port(), ochSignal,
                                                deviceAtoBPowerMap.get(link.src().deviceId()));
                    } else {
                        log.warn("Can't determine power for {}", link.src().deviceId());
                    }
                }
                Device egressDev = deviceService.getDevice(link.dst().deviceId());
                if (egressDev.is(PowerConfig.class)) {
                    if (deviceBtoAPowerMap.get(link.dst().deviceId()) != -99) {
                        log.warn("Configuring power {} for {}",
                                 deviceBtoAPowerMap.get(link.dst().deviceId()),
                                 link.dst().deviceId());
                        egressDev.as(PowerConfig.class)
                                .setTargetPower(link.dst().port(), ochSignal,
                                                deviceBtoAPowerMap.get(link.dst().deviceId()));
                    } else {
                        log.warn("Can't determine power for {}", link.dst().deviceId());
                    }
                }
            }
            Device ingressDevice = deviceService.getDevice(ingress.deviceId());
            if (ingressDevice.is(PowerConfig.class)) {
                Double power = getLaunchPower(jsonNode);
                if (power != -99) {
                    ingressDevice.as(PowerConfig.class)
                            .setTargetPower(ingress.port(), Direction.ALL, power);
                }
            }
            Device egressDevice = deviceService.getDevice(egress.deviceId());
            if (egressDevice.is(PowerConfig.class)) {
                Double power = getLaunchPower(jsonNode);
                if (power != -99) {
                    ingressDevice.as(PowerConfig.class)
                            .setTargetPower(ingress.port(), Direction.ALL, power);
                }
            }
            Intent intent = createOpticalIntent(ingress, egress, deviceService,
                                                null, appId, bidirectional, ochSignal, suggestedPath);

            intentService.submit(intent);
            return Pair.of(intent.id(), getOsnr(jsonNode));
        } catch (IOException e) {
            log.error("Exception while reading response {}", response, e);
            return null;
        }
    }

    protected Path createSuggestedPath(List<DeviceId> deviceIds) {
        List<Link> listLinks = new ArrayList<>();
        for (int i = 0; i < deviceIds.size() - 1; i++) {
            Set<Link> links = linkService.getDeviceLinks(deviceIds.get(i));

            for (Link link : links) {
                if (link.dst().deviceId().equals(deviceIds.get(i + 1))) {
                    listLinks.add(link);
                }
            }
        }
        return new DefaultPath(PROVIDER_ID, listLinks, new ScalarWeight(1));

    }

    protected List<DeviceId> getDeviceAndPopulatePowerMap(JsonNode connectivityReply,
                                                          Map<DeviceId, Double> deviceAtoBPowerMap,
                                                          Map<DeviceId, Double> deviceBtoAPowerMap) {
        List<DeviceId> deviceIds = new ArrayList<>();
        if (connectivityReply.has("result")
                && connectivityReply.get("result").has("response")) {
            JsonNode response = connectivityReply.get("result").get("response");
            //getting the a-b path.
            JsonNode responseObj = response.elements()
                    .next();
            Iterator<JsonNode> elements = responseObj.get("path-properties")
                    .get("path-route-objects").elements();
            Iterable<JsonNode> iterable = () -> elements;
            List<JsonNode> elementsList = StreamSupport
                    .stream(iterable.spliterator(), false)
                    .collect(Collectors.toList());
            Iterator<JsonNode> reversePathRoute = responseObj.get("path-properties")
                    .get("reversed-path-route-objects").elements();
            Iterable<JsonNode> reversedIterable = () -> reversePathRoute;
            List<JsonNode> reversedElementsList = StreamSupport
                    .stream(reversedIterable.spliterator(), false)
                    .collect(Collectors.toList());
            for (int i = 0; i < elementsList.size() - 1; i++) {
                if (elementsList.get(i).get("path-route-object").has("num-unnum-hop")) {
                    String elementId = elementsList.get(i).get("path-route-object")
                            .get("num-unnum-hop").get("node-id")
                            .asText();
                    //TODO this is a workaround until we understand better the
                    // topology mapping between ONOS and GNPy
                    if (elementId.startsWith("netconf:")) {
                        double power = -99;
                        if (!elementsList.get(i).get("path-route-object")
                                .get("num-unnum-hop").get("gnpy-node-type")
                                .asText().equals("transceiver")) {
                            power = getPerHopPower(elementsList.get(i + 2));
                        }
                        deviceAtoBPowerMap.put(DeviceId.deviceId(elementId), power);
                        for (int j = 0; j < reversedElementsList.size() - 1; j++) {
                            if (reversedElementsList.get(j).get("path-route-object").has("num-unnum-hop")) {
                                String reversedElementId = reversedElementsList.get(j).get("path-route-object")
                                        .get("num-unnum-hop").get("node-id")
                                        .asText();
                                double reversePower = -99;
                                if (reversedElementId.equals(elementId)) {
                                    reversePower = getPerHopPower(reversedElementsList.get(j + 2));
                                    System.out.println("Reverse power" + reversePower);
                                    deviceBtoAPowerMap.put(DeviceId.deviceId(elementId), reversePower);
                                }
                            }
                        }
                        deviceIds.add(DeviceId.deviceId(elementId));
                    }
                }
            }
        } else {
            log.warn("Can't retrieve devices {}", connectivityReply);
        }
        return deviceIds;
    }

    protected OchSignal createOchSignal(JsonNode connectivityReply) throws IllegalArgumentException {
        if (connectivityReply.has("result")
                && connectivityReply.get("result").has("response")) {
            Iterator<JsonNode> elements = connectivityReply.get("result").get("response").elements()
                    .next().get("path-properties").get("path-route-objects").elements();
            Iterable<JsonNode> iterable = () -> elements;
            List<JsonNode> elementsList = StreamSupport
                    .stream(iterable.spliterator(), false)
                    .collect(Collectors.toList());
            int n = 0;
            int m = 0;
            for (JsonNode node : elementsList) {
                if (node.get("path-route-object").has("label-hop")) {
                    n = node.get("path-route-object").get("label-hop").get("N").asInt();
                    m = node.get("path-route-object").get("label-hop").get("M").asInt();
                    break;
                }
            }
            int offset = 193100;
            double grid = 6.25;
            double grid2 = 0.05;

            //double central_freq = offset + n * grid;
            double centralFreq = offset + n * grid2;
            //double channel_width = 2 * m * grid;
            try {
                int multiplier = getMultplier(centralFreq, GridType.DWDM, CHL_50GHZ);
                return new OchSignal(GridType.DWDM, CHL_50GHZ, multiplier, 4);
            } catch (RuntimeException e) {
                /* catching RuntimeException as both NullPointerException (thrown by
                 * checkNotNull) and IllegalArgumentException (thrown by checkArgument)
                 * are subclasses of RuntimeException.
                 */
                throw new IllegalArgumentException(e);
            }
        }
        return null;
    }

    protected double getLaunchPower(JsonNode connectivityReply) {
        double power = -99;
        if (connectivityReply.has("result")
                && connectivityReply.get("result").has("response")) {
            Iterator<JsonNode> elements = connectivityReply.get("result").get("response")
                    .elements().next().get("path-properties").get("path-metric").elements();
            Iterable<JsonNode> iterable = () -> elements;
            List<JsonNode> elementsList = StreamSupport
                    .stream(iterable.spliterator(), false)
                    .collect(Collectors.toList());
            for (JsonNode node : elementsList) {
                if (node.has("metric-type") &&
                        node.get("metric-type").asText().equals("reference_power")) {
                    power = node.get("accumulative-value").asDouble();
                    break;
                }
            }
        }
        return power;
    }

    protected double getPerHopPower(JsonNode pathRouteObj) {
        double power = -99;
        if (pathRouteObj.get("path-route-object").has("target-channel-power")) {
            power = pathRouteObj.get("path-route-object")
                    .get("target-channel-power").get("value")
                    .asDouble();
        }
        return power;
    }

    protected double getOsnr(JsonNode connectivityReply) {
        double osnr = -1;
        if (connectivityReply.has("result")
                && connectivityReply.get("result").has("response")) {
            Iterator<JsonNode> elements = connectivityReply.get("result").get("response")
                    .elements().next().get("path-properties").get("path-metric").elements();
            Iterable<JsonNode> iterable = () -> elements;
            List<JsonNode> elementsList = StreamSupport
                    .stream(iterable.spliterator(), false)
                    .collect(Collectors.toList());
            for (JsonNode node : elementsList) {
                if (node.has("metric-type") &&
                        node.get("metric-type").asText().equals("OSNR-0.1nm")) {
                    osnr = node.get("accumulative-value").asDouble();
                    break;
                }
            }
        }
        return osnr;
    }

    private int getMultplier(double wavelength, GridType gridType, ChannelSpacing channelSpacing) {
        long baseFreq;
        switch (gridType) {
            case DWDM:
                baseFreq = 193100;
                break;
            case CWDM:
            case FLEX:
            case UNKNOWN:
            default:
                baseFreq = 0L;
                break;
        }
        System.out.println(channelSpacing.frequency().asTHz());
        if (wavelength > baseFreq) {
            return (int) ((wavelength - baseFreq) / (channelSpacing.frequency().asTHz()));
        } else {
            return (int) ((baseFreq - wavelength) / (channelSpacing.frequency().asTHz()));
        }
    }

    protected ByteArrayOutputStream createGnpyRequest(ConnectPoint ingress,
                                                      ConnectPoint egress, boolean bidirectional) {
        /*
        {
  "path-request": [
    {
      "request-id": "first",
      "source": "trx-Amsterdam",
      "destination": "trx-Bremen",
      "src-tp-id": "trx-Amsterdam",
      "dst-tp-id": "trx-Bremen",
      "bidirectional": false,
      "path-constraints": {
        "te-bandwidth": {
          "technology": "flexi-grid",
          "trx_type": "Voyager",
          "trx_mode": null,
          "effective-freq-slot": [
            {
              "N": "null",
              "M": "null"
            }
          ],
          "spacing": 50000000000.0,
          "max-nb-of-channel": null,
          "output-power": null,
          "path_bandwidth": 100000000000.0
        }
      }
    }
    ]
    }
         */
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            JsonGenerator generator = getJsonGenerator(stream);
            generator.writeStartObject();
            generator.writeArrayFieldStart("path-request");
            generator.writeStartObject();
            generator.writeStringField("request-id", "test");
            generator.writeStringField("source", ingress.deviceId().toString());
            generator.writeStringField("destination", egress.deviceId().toString());
            generator.writeStringField("src-tp-id", ingress.deviceId().toString());
            generator.writeStringField("dst-tp-id", egress.deviceId().toString());
            generator.writeBooleanField("bidirectional", bidirectional);
            generator.writeObjectFieldStart("path-constraints");
            generator.writeObjectFieldStart("te-bandwidth");
            generator.writeStringField("technology", "flexi-grid");
            generator.writeStringField("trx_type", "Cassini"); //TODO make variable
            generator.writeNullField("trx_mode");
            generator.writeArrayFieldStart("effective-freq-slot");
            generator.writeStartObject();
            generator.writeStringField("N", "null");
            generator.writeStringField("M", "null");
            generator.writeEndObject();
            generator.writeEndArray();
            generator.writeNumberField("spacing", 50000000000.0);
            generator.writeNullField("max-nb-of-channel");
            generator.writeNullField("output-power");
            generator.writeNumberField("path_bandwidth", 100000000000.0);
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeEndArray();
            generator.writeEndObject();
            generator.close();
            return stream;
        } catch (IOException e) {
            log.error("Cant' create json", e);
        }
        return stream;

    }

    private JsonGenerator getJsonGenerator(ByteArrayOutputStream stream) throws IOException {
        JsonFactory factory = new JsonFactory();
        return factory.createGenerator(stream, JsonEncoding.UTF8);
    }
}

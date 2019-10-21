/*
 * Copyright 2019-present Open Networking Foundation
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultLink;
import org.onosproject.net.DeviceId;
import org.onosproject.net.GridType;
import org.onosproject.net.Link;
import org.onosproject.net.OchSignal;
import org.onosproject.net.Path;
import org.onosproject.net.link.LinkServiceAdapter;
import org.onosproject.net.provider.ProviderId;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;

/**
 * Test for parsing gNPY json files.
 */
public class GnpyManagerTest {

    private static final String REQUEST = "{\"path-request\":[{\"request-id\":\"test\"," +
            "\"source\":\"netconf:1.2.3.4:830\"," +
            "\"destination\":\"netconf:1.2.3.5:830\",\"src-tp-id\":" +
            "\"netconf:1.2.3.4:830\",\"dst-tp-id\":" +
            "\"netconf:1.2.3.5:830\",\"bidirectional\":true," +
            "\"path-constraints\":{\"te-bandwidth\":" +
            "{\"technology\":\"flexi-grid\",\"trx_type\":\"Cassini\"," +
            "\"trx_mode\":null,\"effective-freq-slot\":" +
            "[{\"N\":\"null\",\"M\":\"null\"}],\"spacing\":5.0E10," +
            "\"max-nb-of-channel\":null,\"output-power\":null," +
            "\"path_bandwidth\":1.0E11}}}]}";

    private ConnectPoint tx1 = ConnectPoint.fromString("netconf:1.2.3.4:830/1");
    private ConnectPoint rdm1tx1 = ConnectPoint.fromString("netconf:1.2.3.5:830/1");
    private ConnectPoint rdm1rdm2 = ConnectPoint.fromString("netconf:1.2.3.5:830/2");
    private ConnectPoint rdm2tx2 = ConnectPoint.fromString("netconf:1.2.3.6:830/1");
    private ConnectPoint rdm2rmd1 = ConnectPoint.fromString("netconf:1.2.3.6:830/2");
    private ConnectPoint tx2 = ConnectPoint.fromString("netconf:1.2.3.7:830/1");
    private Link tx1rdm1Link = DefaultLink.builder().type(Link.Type.OPTICAL)
            .providerId(ProviderId.NONE).src(tx1).dst(rdm1tx1).build();
    private Link rmd1rdm2Link = DefaultLink.builder().type(Link.Type.OPTICAL)
            .providerId(ProviderId.NONE).src(rdm1rdm2).dst(rdm2rmd1).build();
    private Link tx2rmd2Link = DefaultLink.builder().type(Link.Type.OPTICAL)
            .providerId(ProviderId.NONE).src(rdm2tx2).dst(tx2).build();

    private GnpyManager manager;
    private JsonNode reply;

    @Before
    public void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        reply = mapper.readTree(this.getClass().getResourceAsStream("gnpy-response.json"));
        manager = new GnpyManager();
        manager.linkService = new InternalLinkService();
    }

    @Test
    public void testCreateSuggestedPath() throws IOException {
        Map<DeviceId, Double> deviceAtoBPowerMap = new HashMap<>();
        Map<DeviceId, Double> deviceBtoAPowerMap = new HashMap<>();
        //TODO this map is currently only populated in the forward direction
        List<DeviceId> deviceIds = manager.getDeviceAndPopulatePowerMap(reply, deviceAtoBPowerMap, deviceBtoAPowerMap);
        Path path = manager.createSuggestedPath(deviceIds);
        assertTrue(path.links().contains(tx1rdm1Link));
        assertTrue(path.links().contains(rmd1rdm2Link));
        assertTrue(path.links().contains(tx2rmd2Link));
        assertEquals(path.src(), tx1);
        assertEquals(path.dst(), tx2);

    }

    @Test
    public void testgetDevicePowerMap() throws IOException {
        Map<DeviceId, Double> deviceAtoBPowerMap = new HashMap<>();
        Map<DeviceId, Double> deviceBtoAPowerMap = new HashMap<>();
        manager.getDeviceAndPopulatePowerMap(reply, deviceAtoBPowerMap, deviceBtoAPowerMap);
        System.out.println("Device Power Map" + deviceAtoBPowerMap);
        System.out.println("Device Power Map" + deviceBtoAPowerMap);
        assertEquals(-1.0, deviceAtoBPowerMap.get(DeviceId.deviceId("netconf:1.2.3.5:830")));
        assertEquals(-12.0, deviceAtoBPowerMap.get(DeviceId.deviceId("netconf:1.2.3.6:830")));
        assertEquals(1.0, deviceBtoAPowerMap.get(DeviceId.deviceId("netconf:1.2.3.6:830")));
        assertEquals(-12.0, deviceBtoAPowerMap.get(DeviceId.deviceId("netconf:1.2.3.5:830")));
    }

    @Test
    public void testGetLaunchPower() throws IOException {
        double power = manager.getLaunchPower(reply);
        assertEquals(0.001, power);
    }

    @Test
    public void testGetPerHopPower() throws IOException {
        JsonNode response = reply.get("result").get("response");
        //getting the a-b path.
        JsonNode responseObj = response.elements()
                .next();
        Iterator<JsonNode> elements = responseObj.get("path-properties")
                .get("path-route-objects").elements();
        Iterable<JsonNode> iterable = () -> elements;
        List<JsonNode> elementsList = StreamSupport
                .stream(iterable.spliterator(), false)
                .collect(Collectors.toList());
        double power = manager.getPerHopPower(elementsList.get(5));
        assertEquals(-12.0, power);
    }

    @Test
    public void testGetOsnr() throws IOException {
        double osnr = manager.getOsnr(reply);
        assertEquals(21.0, osnr);
    }

    @Test
    public void testCreateOchSignal() throws IOException {
        OchSignal signal = manager.createOchSignal(reply);
        System.out.println(signal);
        assertEquals(signal.gridType(), GridType.DWDM);
        assertEquals(signal.slotWidth().asGHz(), 50.000);
        assertEquals(signal.spacingMultiplier(), 284);
    }

    @Test
    public void testCreateGnpyRequest() {
        ConnectPoint ingress = ConnectPoint.fromString("netconf:1.2.3.4:830/1");
        ConnectPoint egress = ConnectPoint.fromString("netconf:1.2.3.5:830/2");
        String output = manager.createGnpyRequest(ingress, egress, true).toString();
        System.out.println(output);
        assertEquals("Json to create network connectivity is wrong", REQUEST, output);
    }

    private class InternalLinkService extends LinkServiceAdapter {
        @Override
        public Set<Link> getDeviceLinks(DeviceId deviceId) {
            if (deviceId.equals(DeviceId.deviceId("netconf:1.2.3.4:830"))) {
                return ImmutableSet.of(tx1rdm1Link);
            } else if (deviceId.equals(DeviceId.deviceId("netconf:1.2.3.5:830"))) {
                return ImmutableSet.of(rmd1rdm2Link);
            } else if (deviceId.equals(DeviceId.deviceId("netconf:1.2.3.6:830"))) {
                return ImmutableSet.of(tx2rmd2Link);
            }
            return ImmutableSet.of();
        }
    }

}

package org.onosproject.drivers.odtn;

import com.google.common.collect.Range;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onosproject.drivers.utilities.XmlConfigParser;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.PowerConfig;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.netconf.NetconfController;
import org.onosproject.netconf.NetconfDevice;
import org.onosproject.netconf.NetconfException;
import org.onosproject.netconf.NetconfSession;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.onosproject.odtn.behaviour.OdtnDeviceDescriptionDiscovery.OC_NAME;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Driver Implementation of the PowerConfig for OpenConfig terminal devices.
 */
public class GrooveOpenConfigDevicePowerConfig<T>
        extends AbstractHandlerBehaviour implements PowerConfig<T> {
    private static final String RPC_TAG_NETCONF_BASE =
            "<rpc xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">";

    private static final String RPC_CLOSE_TAG = "</rpc>";

    private static final long NO_POWER = -50;

    private static final Logger log = getLogger(GrooveOpenConfigDevicePowerConfig.class);

    private ComponentType state = ComponentType.DIRECTION;

    /**
     * Returns the NetconfSession with the device for which the method was called.
     *
     * @param deviceId device indetifier
     * @return The netconf session or null
     */
    private NetconfSession getNetconfSession(DeviceId deviceId) {
        NetconfController controller = handler().get(NetconfController.class);
        NetconfDevice ncdev = controller.getDevicesMap().get(deviceId);
        if (ncdev == null) {
            log.trace("No netconf device, returning null session");
            return null;
        }
        return ncdev.getSession();
    }

    /**
     * Get the deviceId for which the methods apply.
     *
     * @return The deviceId as contained in the handler data
     */
    private DeviceId did() {
        return handler().data().deviceId();
    }

    /**
     * Execute RPC request.
     *
     * @param session Netconf session
     * @param message Netconf message in XML format
     * @return XMLConfiguration object
     */
    private XMLConfiguration executeRpc(NetconfSession session, String message) {
        try {
            CompletableFuture<String> fut = session.rpc(message);
            String rpcReply = fut.get();
            XMLConfiguration xconf = (XMLConfiguration) XmlConfigParser.loadXmlString(rpcReply);
            xconf.setExpressionEngine(new XPathExpressionEngine());
            return xconf;
        } catch (NetconfException ne) {
            log.error("Exception on Netconf protocol: {}.", ne);
        } catch (InterruptedException ie) {
            log.error("Interrupted Exception: {}.", ie);
        } catch (ExecutionException ee) {
            log.error("Concurrent Exception while executing Netconf operation: {}.", ee);
        }
        return null;
    }

    /**
     * Get the target-output-power value on specific optical-channel.
     *
     * @param port the port
     * @param component the port component. It should be 'oc-name' in the Annotations of Port.
     *                  'oc-name' could be mapped to '/component/name' in openconfig yang.
     * @return target power value
     */
    @Override
    public Optional<Double> getTargetPower(PortNumber port, T component) {
        checkState(component);
        return state.getTargetPower(port, component);
    }

    @Override
    public void setTargetPower(PortNumber port, T component, double power) {
        checkState(component);
        state.setTargetPower(port, component, power);
    }

    @Override
    public Optional<Double> currentPower(PortNumber port, T component) {
        checkState(component);
        return state.currentPower(port, component);
    }

    @Override
    public Optional<Double> currentInputPower(PortNumber port, T component) {
        checkState(component);
        return state.currentInputPower(port, component);
    }

    @Override
    public Optional<Range<Double>> getTargetPowerRange(PortNumber port, T component) {
        checkState(component);
        return state.getTargetPowerRange(port, component);
    }

    @Override
    public Optional<Range<Double>> getInputPowerRange(PortNumber port, T component) {
        checkState(component);
        return state.getInputPowerRange(port, component);
    }

    @Override
    public List<PortNumber> getPorts(T component) {
        checkState(component);
        return state.getPorts(component);
    }


    /**
     * Set the ComponentType to invoke proper methods for different template T.
     *
     * @param component the component.
     */
    void checkState(Object component) {
        String clsName = component.getClass().getName();
        switch (clsName) {
            case "org.onosproject.net.Direction":
                state = ComponentType.DIRECTION;
                break;
            case "org.onosproject.net.OchSignal":
                state = ComponentType.OCHSIGNAL;
                break;
            default:
                log.error("Cannot parse the component type {}.", clsName);
                log.info("The component content is {}.", component.toString());
        }
        state.groove = this;
    }

    /**
     * Component type.
     */
    enum ComponentType {

        /**
         * Direction.
         */
        DIRECTION() {
            @Override
            public Optional<Double> getTargetPower(PortNumber port, Object component) {
                return super.getTargetPower(port, component);
            }

            @Override
            public void setTargetPower(PortNumber port, Object component, double power) {
                super.setTargetPower(port, component, power);
            }
        },

        /**
         * OchSignal.
         */
        OCHSIGNAL() {
            @Override
            public Optional<Double> getTargetPower(PortNumber port, Object component) {
                return super.getTargetPower(port, component);
            }

            @Override
            public void setTargetPower(PortNumber port, Object component, double power) {
                super.setTargetPower(port, component, power);
            }
        };



        GrooveOpenConfigDevicePowerConfig groove;

        /**
         * mirror method in the internal class.
         *
         * @param port port
         * @param component component
         * @return target power
         */
        Optional<Double> getTargetPower(PortNumber port, Object component) {
            if (!isOpticalChannelInPort(port)) {
                return Optional.empty();
            }
            NetconfSession session = groove.getNetconfSession(groove.did());
            checkNotNull(session);
            String filter = parsePort(groove, port, null, null);
            StringBuilder rpcReq = new StringBuilder();
            rpcReq.append(RPC_TAG_NETCONF_BASE)
                    .append("<get>")
                    .append("<filter type='subtree'>")
                    .append(filter)
                    .append("</filter>")
                    .append("</get>")
                    .append(RPC_CLOSE_TAG);
            XMLConfiguration xconf = groove.executeRpc(session, rpcReq.toString());
            try {
                HierarchicalConfiguration config =
                        xconf.configurationAt("data/components/component/optical-channel/config");
                double power = Float.valueOf(config.getString("target-output-power")).doubleValue();
                return Optional.of(power);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        /**
         * mirror method in the internal class.
         * @param port port
         * @param component component
         * @param power target value
         */
        void setTargetPower(PortNumber port, Object component, double power) {
            NetconfSession session = groove.getNetconfSession(groove.did());
            checkNotNull(session);
            String editConfig = parsePort(groove, port, null, power);
            StringBuilder rpcReq = new StringBuilder();
            rpcReq.append(RPC_TAG_NETCONF_BASE)
                    .append("<edit-config>")
                    .append("<target><running/></target>")
                    .append("<config>")
                    .append(editConfig)
                    .append("</config>")
                    .append("</edit-config>")
                    .append(RPC_CLOSE_TAG);
            XMLConfiguration xconf = groove.executeRpc(session, rpcReq.toString());
            // The successful reply should be "<rpc-reply ...><ok /></rpc-reply>"
            if (!xconf.getRoot().getChild(0).getName().equals("ok")) {
                log.error("The <edit-config> operation to set target-output-power of Port({}:{}) is failed.",
                          port.toString(), component.toString());
            }
        }
        /**
         * mirror method in the internal class.
         * @param port port
         * @param component the component.
         * @return current output power.
         */
        Optional<Double> currentPower(PortNumber port, Object component) {
            if (!isOpticalChannelInPort(port)) {
                return Optional.empty();
            }

            XMLConfiguration xconf = getOpticalChannelState(
                    groove, port, "<output-power><instant/></output-power>");
            try {
                HierarchicalConfiguration config =
                        xconf.configurationAt("data/components/component/optical-channel/state/output-power");
                double currentPower = Float.valueOf(config.getString("instant")).doubleValue();
                return Optional.of(currentPower);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        /**
         * mirror method in the internal class.
         * @param port port
         * @param component the component
         * @return current input power
         */
        Optional<Double> currentInputPower(PortNumber port, Object component) {
            if (!isOpticalChannelInPort(port)) {
                return Optional.empty();
            }

            XMLConfiguration xconf = getOpticalChannelState(
                    groove, port, "<input-power><instant/></input-power>");
            try {
                HierarchicalConfiguration config =
                        xconf.configurationAt("data/components/component/optical-channel/state/input-power");
                double currentPower = Float.valueOf(config.getString("instant")).doubleValue();
                return Optional.of(currentPower);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        Optional<Range<Double>> getTargetPowerRange(PortNumber port, Object component) {
            // Only line ports have power
            if (isOpticalChannelInPort(port)) {
                return Optional.of(Range.open(-20., 6.));
            } {
                return Optional.empty();
            }
        }

        boolean isOpticalChannelInPort(PortNumber port) {
            DeviceService deviceService = DefaultServiceDirectory.getService(DeviceService.class);
            DeviceId deviceId = groove.handler().data().deviceId();
            return (deviceService.getPort(deviceId, port).type() == Port.Type.OCH );
        }

        Optional<Range<Double>> getInputPowerRange(PortNumber port, Object component) {
            if (!isOpticalChannelInPort(port)) {
                return Optional.empty();
            }
            XMLConfiguration xconf = getOpticalChannelState(groove, port, "<input-power-range/>");
            try {
                HierarchicalConfiguration config =
                        xconf.configurationAt("data/components/component/optical-channel/state/input-power-range");
                double inputMin = Float.valueOf(config.getString("min")).doubleValue();
                double inputMax = Float.valueOf(config.getString("max")).doubleValue();
                return Optional.of(Range.open(inputMin, inputMax));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        List<PortNumber> getPorts(Object component) {
            // FIXME
            log.warn("Not Implemented Yet!");
            return new ArrayList<PortNumber>();
        }

        /**
         * Get filtered content under <optical-channel><state>.
         * @param pc power config instance
         * @param port the port number
         * @param underState the filter condition
         * @return RPC reply
         */
        private static XMLConfiguration getOpticalChannelState(GrooveOpenConfigDevicePowerConfig pc,
                                                               PortNumber port, String underState) {
            NetconfSession session = pc.getNetconfSession(pc.did());
            checkNotNull(session);
            String name = ocName(pc, port);
            StringBuilder rpcReq = new StringBuilder(RPC_TAG_NETCONF_BASE);
            rpcReq.append("<get><filter><components xmlns=\"http://openconfig.net/yang/platform\"><component>")
                    .append("<name>").append(name).append("</name>")
                    .append("<optical-channel xmlns=\"http://openconfig.net/yang/terminal-device\">")
                    .append("<state>")
                    .append(underState)
                    .append("</state></optical-channel></component></components></filter></get>")
                    .append(RPC_CLOSE_TAG);
            XMLConfiguration xconf = pc.executeRpc(session, rpcReq.toString());
            return xconf;
        }


        /**
         * Extract component name from portNumber's annotations.
         * @param pc power config instance
         * @param portNumber the port number
         * @return the component name
         */
        private static String ocName(GrooveOpenConfigDevicePowerConfig pc, PortNumber portNumber) {
            DeviceService deviceService = DefaultServiceDirectory.getService(DeviceService.class);
            DeviceId deviceId = pc.handler().data().deviceId();
            return deviceService.getPort(deviceId, portNumber).annotations().value("oc-name");
        }



        /**
         * Parse filtering string from port and component.
         * @param portNumber Port Number
         * @param component port component (optical-channel)
         * @param power power value set.
         * @return filtering string in xml format
         */
        private static String parsePort(GrooveOpenConfigDevicePowerConfig pc, PortNumber portNumber,
                                        Object component, Double power) {
            if (component == null) {
                String name = ocName(pc, portNumber);
                StringBuilder sb = new StringBuilder("<components xmlns=\"http://openconfig.net/yang/platform\">");
                sb.append("<component>").append("<name>").append(name).append("</name>");
                if (power != null) {
                    // This is an edit-config operation.
                    sb.append("<optical-channel xmlns=\"http://openconfig.net/yang/terminal-device\">")
                            .append("<config>")
                            .append("<target-output-power>")
                            .append(power)
                            .append("</target-output-power>")
                            .append("</config>")
                            .append("</optical-channel>");
                }
                sb.append("</component>").append("</components>");
                return sb.toString();
            } else {
                log.error("Cannot process the component {}.", component.getClass());
                return null;
            }
        }
    }
}

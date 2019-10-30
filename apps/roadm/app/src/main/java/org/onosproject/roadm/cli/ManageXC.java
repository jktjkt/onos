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
 *
 * This work was done in Nokia Bell Labs Paris
 *
 */
package org.onosproject.roadm.cli;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.util.Frequency;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.cli.net.PortNumberCompleter;
import org.onosproject.net.ChannelSpacing;
import org.onosproject.net.DeviceId;
import org.onosproject.net.GridType;
import org.onosproject.net.OchSignal;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowId;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.OchSignalCriterion;
import org.onosproject.net.flow.criteria.PortCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flow.instructions.L0ModificationInstruction;
import org.onosproject.roadm.RoadmService;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * This is the command for adding and dropping XCs on the ROADMs.
 *
 */
@Service
@Command(scope = "onos", name = "manage-xc",
        description = "Adds (or Drops) XC to (from) the ROADM")
public class ManageXC extends AbstractShellCommand {

    private static final Logger log = getLogger(ManageXC.class);

    @Argument(index = 0, name = "operation",
            description = "Specifying Add or Drop action",
            required = true, multiValued = false)
    private String operation = null;

    @Argument(index = 1, name = "deviceId",
            description = "ROADM's device ID (from ONOS)",
            required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    private String deviceId = null;

    @Argument(index = 2, name = "srcPort",
            description = "XC's source port {PortNumber}",
            required = true, multiValued = false)
    private String srcPort = null;
    @Completion(PortNumberCompleter.class)

    @Argument(index = 3, name = "dstPort",
            description = "XC's destination port {PortNumber}",
            required = true, multiValued = false)
    private String dstPort = null;
    @Completion(PortNumberCompleter.class)

    @Argument(index = 4, name = "freq",
            description = "XC's central frequency in [MHz]",
            required = true, multiValued = false)
    private String freq = null;

    @Argument(index = 5, name = "sw",
            description = "Frequency Slot Width in [GHz], like 50 GHz or 62.5 GHz",
            required = true, multiValued = false)
    private String bw = null;



    @Override
    protected void doExecute() throws Exception {

        if (operation.equals("add") || operation.equals("create")) {

            FlowId check = addRule();
            if (check != null) {
                print("Rule %s was successfully added", check.toString());
                log.info("Rule {} was successfully added", check.toString());
            } else {
                print("Your rule wasn't added. Something went wrong during the process (issue on the driver side).");
                log.info("Your rule wasn't added. Something went wrong during the process (issue on the driver side).");
            }

        } else if (operation.equals("drop") || operation.equals("remove")) {

            FlowId check = dropRule();
            if (check != null) {
                print("Rule %s was successfully dropped", check.toString());
                log.info("Rule {} was successfully dropped", check.toString());
            } else {
                print("Your rule wasn't dropped. No match found.");
                log.info("Your rule wasn't dropped. No match found.");
            }

        } else {

            print("\n Unspecified operation -- %s -- :( \n Try again! \n", operation);
            log.debug("\n Unspecified operation -- {} -- :( \n Try again! \n", operation);

        }

    }


    /**
     * This method creates a XC on the device based on the parameters passed by the user.
     * Takes as an input "global" parameters (passed by user through the console).
     * @return - return the FlowId of the installed rule.
     */
    protected FlowId addRule() {

        // Preparing parameters
        DeviceId device = DeviceId.deviceId(deviceId);
        PortNumber inPort = PortNumber.portNumber(srcPort);
        PortNumber outPort = PortNumber.portNumber(dstPort);
        OchSignal signal = createOchSignal(freq, bw);

        if (inPort == null) {
            print("[addRule] Not able to find srcPort in the ONOS database");
            log.debug("[addRule] Not able to find srcPort in the ONOS database");
            return null;
        }
        if (outPort == null) {
            print("[addRule] Not able to find dstPort in the ONOS database");
            log.debug("[addRule] Not able to find dstPort in the ONOS database");
            return null;
        }

        if (signal == null) {
            print("[addRule] Not able to compose an OchSignal with passed parameters. Double check them");
            log.debug("[addRule] Not able to compose an OchSignal with passed parameters. Double check them");
            return null;
        }

        RoadmService manager = AbstractShellCommand.get(RoadmService.class);
        FlowId flow = manager.createConnection(device, 100, true, -1, inPort, outPort, signal);

        if (flow != null) {
            print("Adding XC for the device %s between port %s and port %s on frequency %s with bandwidth %s",
                  deviceId, srcPort, dstPort, freq, bw);
            log.info("[addRule] Adding XC for the device {} between port {} and port {} " +
                             "on frequency {} with bandwidth {}", deviceId, srcPort, dstPort, freq, bw);
            return flow;
        } else {
            return null;
        }
    }


    /**
     * This function drops XC installed on the device, which is matching parsed criteria.
     * Takes as an input "global" parameters (passed by user through the console).
     * @return - returns number of the rules that were dropped.
     */
    protected FlowId dropRule() {

            // Preparing parameters
            DeviceId device = DeviceId.deviceId(deviceId);
            PortNumber inPort = PortNumber.portNumber(srcPort);
            PortNumber outPort = PortNumber.portNumber(dstPort);

            // Creating some variables
            OchSignal ochSignal = null;
            PortNumber inputPortNumber = null;
            PortNumber outputPortNumber = null;

            // Main idea: Go over all flow rules (read out from the storage) of current device and
            // filter them based on input and output port with respect to OchSignal
            FlowRuleService fr = AbstractShellCommand.get(FlowRuleService.class);
            Iterable<FlowEntry> flowRules = fr.getFlowEntries(device);
            FlowId flowId = null;
            OchSignal referenceSignal = createOchSignal(freq, bw);


        for (FlowEntry flowRule : flowRules) {

                // Taken from FlowRuleParser
                for (Criterion c : flowRule.selector().criteria()) {
                    if (c instanceof OchSignalCriterion) {
                        ochSignal = ((OchSignalCriterion) c).lambda();
                    }
                    if (c instanceof PortCriterion) {
                        inputPortNumber = ((PortCriterion) c).port(); // obtain input port
                    }
                }
                for (Instruction i : flowRule.treatment().immediate()) {
                    if (i instanceof
                            L0ModificationInstruction.ModOchSignalInstruction) {
                        ochSignal =
                                ((L0ModificationInstruction.ModOchSignalInstruction) i)
                                        .lambda();
                    }
                    if (i instanceof Instructions.OutputInstruction) {
                        outputPortNumber = ((Instructions.OutputInstruction) i).port(); // obtain output port
                    }
                }

                // If we found match, then let's delete this rule
                if ((ochSignal.centralFrequency().equals(referenceSignal.centralFrequency()))
                        & (ochSignal.slotWidth().equals(referenceSignal.slotWidth()))
                        & (inputPortNumber.equals(inPort)) & (outputPortNumber.equals(outPort))) {
                    flowId = flowRule.id();

                    RoadmService manager = AbstractShellCommand.get(RoadmService.class);
                    manager.removeConnection(device, flowId);
                    print("Dropping existing XC from the device %s", deviceId);
                    return flowId;
                }
            }

        return null;
    }


    /**
     * This method creates OchSignal instance based on central frequency and the bandwidth of the channel.
     * @param frequency - central frequency of the connection.
     * @param bw - bandwidth of the optical channel.
     * @return - returns created instance of OchSignal.
     */
    protected OchSignal createOchSignal(String frequency, String bw) {

        Frequency centralFreq = Frequency.ofMHz(Integer.parseInt(frequency));
        Frequency bandwidth = Frequency.ofGHz(Integer.parseInt(bw));

        // Setting channel spacing on minimum possible (to have preciser steps)
        ChannelSpacing channelSpacing = ChannelSpacing.CHL_6P25GHZ;

        // This frequency is in [GHz] according to ITU-T 694.1
        int deffreq = 193100000;
        double centfreq = Double.parseDouble(String.valueOf(centralFreq.asHz()).
                substring(0, String.valueOf(centralFreq.asHz()).length() - 6)); // / 1000;
        // Computing spacing multiplier from definition (see OchSignal class comments)
        double spMult = (centfreq - deffreq) / 6.25;
        int spacingMultiplier = (int) (spMult / 1000);

        double bandw = Double.parseDouble(String.valueOf(bandwidth.asHz()).
                substring(0, String.valueOf(bandwidth.asHz()).length() - 6)) / 1000;
        // Computing according to the definition
        double slotgr = bandw / 12.5;
        int slotGranularity = (int) slotgr;

        OchSignal ochSignal = new OchSignal(GridType.FLEX, channelSpacing, spacingMultiplier, slotGranularity);

        return ochSignal;
    }

}

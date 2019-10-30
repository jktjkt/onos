/*
 * Copyright 2019-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * This work was done in Nokia Bell Labs.
 *
 */

package org.onosproject.net.optical.util;


import org.onlab.util.Frequency;
import org.onosproject.net.ChannelSpacing;
import org.onosproject.net.GridType;
import org.onosproject.net.OchSignal;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;


/**
 * Optical Channel Utility is a set of methods to convert different
 * set of parameters to the OchSignal instance and backwards.
 */
public final class OpticalChannelUtility {

    private static final Logger log = getLogger(OpticalChannelUtility.class);

    private OpticalChannelUtility() {}

    /**
     * This method creates OchSignal instance based on central Frequency and the Slot Width of the channel.
     * @param centralFrequency - central frequency of the connection.
     * @param slotWidth - bandwidth of the optical channel.
     * @param gridType - type of the frequency grid.
     * @param channelSpacing - channel spacing.
     * @return - returns created instance of OchSignal.
     */
    public static final OchSignal createOchSignal(Frequency centralFrequency, Frequency slotWidth,
                                     GridType gridType, ChannelSpacing channelSpacing) {

        // This frequency is in [GHz] according to ITU-T 694.1
        int deffreq = 193100000;
        double centfreq = Double.parseDouble(String.valueOf(centralFrequency.asHz()).
                substring(0, String.valueOf(centralFrequency.asHz()).length() - 6)); // / 1000;
        // Computing spacing multiplier from definition (see OchSignal class comments)
        double spMult = (centfreq - deffreq) / 6.25;
        int spacingMultiplier = (int) (spMult / 1000);

        double bandw = Double.parseDouble(String.valueOf(slotWidth.asHz()).
                substring(0, String.valueOf(slotWidth.asHz()).length() - 6)) / 1000;
        // Computing according to the definition
        double slotgr = bandw / 12.5;
        int slotGranularity = (int) slotgr;

        OchSignal ochSignal = new OchSignal(gridType, channelSpacing, spacingMultiplier, slotGranularity);

        return ochSignal;
    }

    /**
     * This method creates OchSignal instance from frequency bounds.
     * @param lowerBound - lower bound of the frequency.
     * @param upperBound - upper bound of the frequency.
     * @param gridType - type of the frequency grid.
     * @param channelSpacing - channel spacing.
     * @return - returns created instance of OchSignal.
     */
    public static final OchSignal createOchSignalFromBounds(Frequency lowerBound,
                                               Frequency upperBound, GridType gridType, ChannelSpacing channelSpacing) {

        // Transferring everything to the frequencies
        Frequency slotWidth = upperBound.subtract(lowerBound);
        Frequency halfBw = slotWidth.floorDivision(2);
        Frequency centralFrequency = lowerBound.add(halfBw);

        // This frequency is in [GHz] according to ITU-T 694.1
        int deffreq = 193100000;
        double centfreq = Double.parseDouble(String.valueOf(centralFrequency.asHz()).
                substring(0, String.valueOf(centralFrequency.asHz()).length() - 6)); // / 1000;
        // Computing spacing multiplier from definition (see OchSignal class comments)
        double spMult = (centfreq - deffreq) / 6.25;
        int spacingMultiplier = (int) (spMult / 1000);

        double bandw = Double.parseDouble(String.valueOf(slotWidth.asHz()).
                substring(0, String.valueOf(slotWidth.asHz()).length() - 6)) / 1000;
        // Computing according to the definition
        double slotgr = bandw / 12.5;
        int slotGranularity = (int) slotgr;

        OchSignal ochSignal = new OchSignal(gridType, channelSpacing, spacingMultiplier, slotGranularity);

        return ochSignal;
    }

    /**
     * This method extracts frequency bounds from OchSignal instance.
     * @param signal - OchSignal instance.
     * @param channelSpacing - channel spacing.
     * @return - HashMap with upper and lower bounds of frequency.
     */
    public static final Map<String, Frequency> extractOchFreqBounds(OchSignal signal, double channelSpacing) {
        // Spacing multiplier computed from this equation:
        // Nominal central frequency = 193.1 THz + spacingMultiplier * channelSpacing
        // So, in the end it's:
        // spacingMultiplier = (centralFrequency - 193.1THz)/channelSpacing

        // Initializing variables
        int spacingMultiplier = signal.spacingMultiplier();
        int slotGranularity = signal.slotGranularity();
        // The central central frequency
        int deffreq = 193100; // in [GHz]

        // Computing true central frequency
        double centralFreq = deffreq + spacingMultiplier * channelSpacing;
        // Computing bandwidth
        double halfBandwidth = slotGranularity * 12.5 / 2;

        // Transferring everything to the frequencies
        Frequency central = Frequency.ofGHz(centralFreq);
        Frequency hbw = Frequency.ofGHz(halfBandwidth);

        // Getting frequency bounds
        Frequency minFreq = central.subtract(hbw);
        Frequency maxFreq = central.add(hbw);
//        log.info("\n [extractOchFreqBounds] minFreq computed is {} \n" +

        Map<String, Frequency> freqs = new HashMap<String, Frequency>();
        freqs.put("minFreq", minFreq);
        freqs.put("maxFreq", maxFreq);

        return freqs;
    }

    /**
     * This method extracts central frequency and slot width from OchSignal instance.
     * @param signal - OchSignal instance.
     * @param channelSpacing - channel spacing.
     * @return - HashMap with upper and lower bounds of frequency.
     */
    public static final Map<String, Frequency> extractOch(OchSignal signal, double channelSpacing) {
        // Spacing multiplier computed from this equation:
        // Nominal central frequency = 193.1 THz + spacingMultiplier * channelSpacing
        // So, in the end it's:
        // spacingMultiplier = (centralFrequency - 193.1THz)/channelSpacing

        // Initializing variables
        int spacingMultiplier = signal.spacingMultiplier();
        int slotGranularity = signal.slotGranularity();
        // The central central frequency
        int deffreq = 193100; // in [GHz]

        // Computing true central frequency
        double centralFreq = deffreq + spacingMultiplier * channelSpacing;
        // Computing bandwidth
        double bandwidth = slotGranularity * 12.5;

        // Transferring everything to the frequencies
        Frequency central = Frequency.ofGHz(centralFreq);
        Frequency bw = Frequency.ofGHz(bandwidth);

        Map<String, Frequency> freqs = new HashMap<String, Frequency>();
        freqs.put("centralFrequency", central);
        freqs.put("slotWidth", bw);

        return freqs;
    }


}
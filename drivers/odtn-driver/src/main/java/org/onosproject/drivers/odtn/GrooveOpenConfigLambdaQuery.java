package org.onosproject.drivers.odtn;

import com.google.common.collect.ImmutableSet;
import org.onlab.util.Frequency;
import org.onlab.util.Spectrum;
import org.onosproject.net.ChannelSpacing;
import org.onosproject.net.GridType;
import org.onosproject.net.OchSignal;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.LambdaQuery;
import org.onosproject.net.driver.AbstractHandlerBehaviour;

import java.util.Set;
import java.util.stream.IntStream;


public class GrooveOpenConfigLambdaQuery extends AbstractHandlerBehaviour implements LambdaQuery {
    private static final int LAMBDA_COUNT = 96;
    private static final Frequency START_CENTER_FREQ = Frequency.ofGHz(191_350);

    @Override
    public Set<OchSignal> queryLambdas(PortNumber port) {

        int startMultiplier = (int) (START_CENTER_FREQ.subtract(Spectrum.CENTER_FREQUENCY).asHz()
                / Frequency.ofGHz(50).asHz());

        return IntStream.range(0, LAMBDA_COUNT)
                .mapToObj(x -> new OchSignal(GridType.DWDM,
                                             ChannelSpacing.CHL_50GHZ,
                                             startMultiplier + x,
                                             4))
                .collect(ImmutableSet.toImmutableSet());
    }

}

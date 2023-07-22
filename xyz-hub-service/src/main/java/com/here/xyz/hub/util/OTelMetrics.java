package com.here.xyz.hub.util;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class OTelMetrics {

    private static final Logger logger = LogManager.getLogger();
    private static final Meter meter = GlobalOpenTelemetry.meterBuilder("io.opentelemetry.metrics.memory")
            .setInstrumentationVersion("1.27.0") // as per otel.version in pom.xml
            .build();

    public static void init() {
        // This will keep collecting memory utilization in background
        meter.gaugeBuilder("mem_used_pct")
                .setDescription("Heap-Memory used percentage")
                .setUnit("percent")
                .buildWithCallback(
                        (r) -> {
                            Runtime rt = Runtime.getRuntime();
                            long max = rt.maxMemory();
                            long total = rt.totalMemory();
                            long free = rt.freeMemory();
                            long used = total - free;
                            double usedPct = ((double)used/max)*100.00;
                            BigDecimal bd = new BigDecimal(usedPct).setScale(2, RoundingMode.HALF_EVEN);
                            r.record(bd.doubleValue());
                        }
                );
    }


}

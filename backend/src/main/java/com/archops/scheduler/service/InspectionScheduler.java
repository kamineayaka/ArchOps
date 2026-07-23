package com.archops.scheduler.service;

import com.archops.asset.service.AssetService;
import com.archops.knowledge.domain.WorkLog;
import com.archops.knowledge.service.WorkLogWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodic fleet health inspection. Runs every 5 minutes for lightweight checks;
 * records an hourly work log summary (avoids flooding RAG index with noise).
 */
@Service
public class InspectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(InspectionScheduler.class);

    private final AssetService assetService;
    private final WorkLogWriter workLogWriter;

    public InspectionScheduler(AssetService assetService, WorkLogWriter workLogWriter) {
        this.assetService = assetService;
        this.workLogWriter = workLogWriter;
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void inspectAssets() {
        var assets = assetService.list();
        long servers = assets.stream().filter(a -> a.kind().name().equals("SERVER")).count();
        if (servers == 0) {
            return;
        }
        log.debug("Inspection tick: {} server assets registered", servers);
    }

    @Scheduled(cron = "0 0 * * * *")
    public void hourlySummary() {
        var assets = assetService.list();
        long servers = assets.stream().filter(a -> a.kind().name().equals("SERVER")).count();
        if (servers == 0) {
            return;
        }
        WorkLog entry = new WorkLog();
        entry.setLogType("INSPECTION");
        entry.setActorName("scheduler");
        entry.setSource("scheduler");
        entry.setLevel("L0");
        entry.setSummary("Hourly inspection summary: " + servers + " server assets registered");
        workLogWriter.save(entry);
    }
}

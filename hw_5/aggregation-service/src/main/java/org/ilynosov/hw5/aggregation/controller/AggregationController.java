package org.ilynosov.hw5.aggregation.controller;

import lombok.RequiredArgsConstructor;
import org.ilynosov.hw5.aggregation.service.AggregationService;
import org.ilynosov.hw5.aggregation.service.RetentionService;
import org.ilynosov.hw5.aggregation.service.S3ExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/aggregation")
@RequiredArgsConstructor
public class AggregationController {

    private final AggregationService aggregationService;
    private final RetentionService retentionService;
    private final S3ExportService s3ExportService;

    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> trigger(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        aggregationService.compute(date);
        return ResponseEntity.ok(Map.of("status", "triggered", "date", date.toString()));
    }

    @PostMapping("/retention")
    public ResponseEntity<Map<String, String>> triggerRetention(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        retentionService.computeAndStoreFullCohort(from, to);
        return ResponseEntity.ok(Map.of("status", "triggered", "from", from.toString(), "to", to.toString()));
    }

    @PostMapping("/export")
    public ResponseEntity<Map<String, String>> triggerExport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) throws Exception {
        s3ExportService.export(date);
        return ResponseEntity.ok(Map.of("status", "exported", "date", date.toString()));
    }
}

package mera.mera_v2.ads.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import mera.mera_v2.ads.model.AdsReportResponse;
import mera.mera_v2.ads.service.AdsReportService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dt-ads")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdsReportController {

    private final AdsReportService adsReportService;

    @GetMapping("/report")
    public ResponseEntity<AdsReportResponse> report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "cod_desc") String sort
    ) {
        AdsReportResponse response = adsReportService.getReport(from, to, sort, false);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/report/refresh")
    public ResponseEntity<AdsReportResponse> refresh(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "cod_desc") String sort
    ) {
        AdsReportResponse response = adsReportService.getReport(from, to, sort, true);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/test-api")
    public String testApi(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return adsReportService.testApiCall(from, to);
    }
}

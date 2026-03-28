package mera.mera_v2.report.sale;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mera.mera_v2.model.BitableTable;
import mera.mera_v2.model.SaleReportCacheEntry;
import mera.mera_v2.model.SaleSummaryRow;
import mera.mera_v2.customer.Service.BitableService;
import mera.mera_v2.report.sale.SaleReportCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import mera.mera_v2.lark.token.LarkTokenService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

@Controller
public class SaleReportController {

  private static final Logger log = LoggerFactory.getLogger(SaleReportController.class);

  private static final String SESSION_SALE_SUMMARY = "SESSION_SALE_SUMMARY";
  private static final String SESSION_SALE_RANGE = "SESSION_SALE_RANGE";
  private static final String SESSION_SALE_FETCHED_AT = "SESSION_SALE_FETCHED_AT";

  private final LarkTokenService tokenService;
  private final BitableService bitableService;
  private final SaleReportCacheService cacheService;

  public SaleReportController(LarkTokenService tokenService, BitableService bitableService, SaleReportCacheService cacheService) {
    this.tokenService = tokenService;
    this.bitableService = bitableService;
    this.cacheService = cacheService;
  }

  @GetMapping("/saleReport")
  public String saleReport(
      @RequestParam(value = "range", required = false, defaultValue = "CurrentMonth") String range,
      Model model,
      HttpSession session
  ) {
    model.addAttribute("range", range);
    return "saleReport";
  }

  @GetMapping("/api/saleReport/data")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> getSaleReportData(
      @RequestParam(value = "range", required = false, defaultValue = "CurrentMonth") String range,
      HttpSession session
  ) {
    Map<String, Object> response = new HashMap<>();
    
    if (!tokenService.hasToken(session)) {
      response.put("hasToken", false);
      response.put("rows", List.of());
      return ResponseEntity.ok(response);
    }

    try {
      log.info("ðŸ” Loading sale report data for range: {}", range);
      tokenService.autoRefreshTokenIfNeeded(session);

      // 1) âœ… session cache
      @SuppressWarnings("unchecked")
      List<SaleSummaryRow> cached = (List<SaleSummaryRow>) session.getAttribute(SESSION_SALE_SUMMARY);
      String cachedRange = (String) session.getAttribute(SESSION_SALE_RANGE);
      LocalDateTime fetchedAt = (LocalDateTime) session.getAttribute(SESSION_SALE_FETCHED_AT);

      if (cached != null && cachedRange != null && cachedRange.equals(range) && fetchedAt != null) {
        response.put("hasToken", true);
        response.put("rows", cached);
        response.put("range", cachedRange);
        response.put("fetchedAt", fetchedAt.toString());
        response.put("totalAgents", cached.size());
        response.put("fromCache", "SESSION");
        return ResponseEntity.ok(response);
      }

      // 2) âœ… disk cache
      Optional<SaleReportCacheEntry> disk = cacheService.get(range);
      if (disk.isPresent()) {
        SaleReportCacheEntry entry = disk.get();
        LocalDateTime dt = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(entry.getFetchedAtEpochMs()),
            ZoneId.systemDefault()
        );

        session.setAttribute(SESSION_SALE_SUMMARY, entry.getRows());
        session.setAttribute(SESSION_SALE_RANGE, range);
        session.setAttribute(SESSION_SALE_FETCHED_AT, dt);

        response.put("hasToken", true);
        response.put("rows", entry.getRows());
        response.put("range", range);
        response.put("fetchedAt", dt.toString());
        response.put("totalAgents", entry.getRows() != null ? entry.getRows().size() : 0);
        response.put("fromCache", "DISK");
        return ResponseEntity.ok(response);
      }

      // 3) âŒ cache miss -> thá»‘ng kÃª tháº­t
      List<BitableTable> saleTables = bitableService.getSaleTables(session);
      List<SaleSummaryRow> rows = new ArrayList<>();

      for (BitableTable t : saleTables) {
        rows.add(bitableService.buildSaleSummaryForTable(session, t, range));
      }

      long nowMs = Instant.now().toEpochMilli();
      LocalDateTime nowDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault());

      // save to session
      session.setAttribute(SESSION_SALE_SUMMARY, rows);
      session.setAttribute(SESSION_SALE_RANGE, range);
      session.setAttribute(SESSION_SALE_FETCHED_AT, nowDt);

      // save to disk
      cacheService.put(new SaleReportCacheEntry(range, nowMs, rows));

      response.put("hasToken", true);
      response.put("rows", rows);
      response.put("range", range);
      response.put("fetchedAt", nowDt.toString());
      response.put("totalAgents", rows.size());
      response.put("fromCache", "LIVE");

    } catch (Exception e) {
      log.error("Error loading sale report: {}", e.getMessage(), e);
      response.put("hasToken", true);
      response.put("rows", List.of());
      response.put("range", range);
      response.put("error", e.getMessage());
    }

    return ResponseEntity.ok(response);
  }

  @PostMapping("/saleReport/refresh")
  public String refreshSaleReport(
      @RequestParam(value = "range", required = false, defaultValue = "CurrentMonth") String range,
      HttpSession session
  ) {
    // clear session
    session.removeAttribute(SESSION_SALE_SUMMARY);
    session.removeAttribute(SESSION_SALE_RANGE);
    session.removeAttribute(SESSION_SALE_FETCHED_AT);

    // clear disk
    cacheService.clear(range);

    return "redirect:/saleReport?range=" + range;
  }

  @GetMapping("/saleReport/export")
  public ResponseEntity<byte[]> exportToExcel(
      @RequestParam(value = "range", required = false, defaultValue = "CurrentMonth") String range,
      HttpSession session
  ) throws IOException {
    
    if (!tokenService.hasToken(session)) {
      return ResponseEntity.badRequest().build();
    }

    try {
      tokenService.autoRefreshTokenIfNeeded(session);

      // Láº¥y data tá»« session cache hoáº·c disk cache
      @SuppressWarnings("unchecked")
      List<SaleSummaryRow> rows = (List<SaleSummaryRow>) session.getAttribute(SESSION_SALE_SUMMARY);
      String cachedRange = (String) session.getAttribute(SESSION_SALE_RANGE);

      // Náº¿u khÃ´ng cÃ³ trong session hoáº·c range khÃ¡c, láº¥y tá»« disk cache
      if (rows == null || !range.equals(cachedRange)) {
        Optional<SaleReportCacheEntry> disk = cacheService.get(range);
        if (disk.isPresent()) {
          rows = disk.get().getRows();
        } else {
          // Náº¿u khÃ´ng cÃ³ cache, load data má»›i
          List<BitableTable> saleTables = bitableService.getSaleTables(session);
          rows = new ArrayList<>();
          for (BitableTable t : saleTables) {
            rows.add(bitableService.buildSaleSummaryForTable(session, t, range));
          }
        }
      }

      if (rows == null || rows.isEmpty()) {
        return ResponseEntity.badRequest().build();
      }

      // Táº¡o Excel file
      Workbook workbook = new XSSFWorkbook();
      Sheet sheet = workbook.createSheet("Sale Report");

      // Táº¡o style cho header
      CellStyle headerStyle = workbook.createCellStyle();
      Font headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerFont.setFontHeightInPoints((short) 12);
      headerStyle.setFont(headerFont);
      headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      headerStyle.setBorderBottom(BorderStyle.THIN);
      headerStyle.setBorderTop(BorderStyle.THIN);
      headerStyle.setBorderLeft(BorderStyle.THIN);
      headerStyle.setBorderRight(BorderStyle.THIN);
      headerStyle.setAlignment(HorizontalAlignment.CENTER);

      // Táº¡o style cho sá»‘
      CellStyle numberStyle = workbook.createCellStyle();
      numberStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0"));

      // Táº¡o style cho pháº§n trÄƒm
      CellStyle percentStyle = workbook.createCellStyle();
      percentStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("0.0%"));

      // XÃ¡c Ä‘á»‹nh thÃ¡ng hiá»ƒn thá»‹ theo range
      LocalDateTime nowDtLabel = LocalDateTime.now(ZoneId.systemDefault());
      int currentMonthNum = nowDtLabel.getMonthValue();
      int targetMonthNum = "CurrentMonth".equals(range)
          ? currentMonthNum
          : (currentMonthNum == 1 ? 12 : currentMonthNum - 1);
      String monthLabel = "ThÃ¡ng " + targetMonthNum;

      // Táº¡o title
      Row titleRow = sheet.createRow(0);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue("Thá»‘ng kÃª tin nháº¯n phÃ²ng Sale " + monthLabel);
      CellStyle titleStyle = workbook.createCellStyle();
      Font titleFont = workbook.createFont();
      titleFont.setBold(true);
      titleFont.setFontHeightInPoints((short) 14);
      titleStyle.setFont(titleFont);
      titleStyle.setAlignment(HorizontalAlignment.CENTER);
      titleCell.setCellStyle(titleStyle);
      sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 13));

      // DÃ²ng trá»‘ng ngÄƒn cÃ¡ch
      Row headerRow = sheet.createRow(2);
      String[] headers = {
          "#", "TÆ° váº¥n viÃªn", "Nhu cáº§u", "TrÃ¹ng", "RÃ¡c", "KhÃ´ng tÆ°Æ¡ng tÃ¡c",
          "Chá»‘t nÃ³ng", "Chá»‘t cÅ©", "ÄÆ¡n há»§y", "Tá»•ng mess", "Tá»•ng Ä‘Æ¡n",
          "ÄÆ¡n/mess nhu cáº§u", "ÄÆ¡n/mess tá»•ng", "Tá»‰ lá»‡ há»§y"
      };
      
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // ThÃªm data rows
      int rowNum = 3;
      for (SaleSummaryRow row : rows) {
        Row dataRow = sheet.createRow(rowNum++);
        
        int colNum = 0;
        dataRow.createCell(colNum++).setCellValue(rowNum - 1); // STT
        dataRow.createCell(colNum++).setCellValue(row.getTableName() != null ? row.getTableName() : "");
        
        // Sá»‘ nguyÃªn
        Cell nhuCauCell = dataRow.createCell(colNum++);
        nhuCauCell.setCellValue(row.getNhuCau());
        nhuCauCell.setCellStyle(numberStyle);
        
        Cell trungCell = dataRow.createCell(colNum++);
        trungCell.setCellValue(row.getTrung());
        trungCell.setCellStyle(numberStyle);
        
        Cell racCell = dataRow.createCell(colNum++);
        racCell.setCellValue(row.getRac());
        racCell.setCellStyle(numberStyle);
        
        Cell khongTuongTacCell = dataRow.createCell(colNum++);
        khongTuongTacCell.setCellValue(row.getKhongTuongTac());
        khongTuongTacCell.setCellStyle(numberStyle);
        
        Cell chotNongCell = dataRow.createCell(colNum++);
        chotNongCell.setCellValue(row.getChotNong());
        chotNongCell.setCellStyle(numberStyle);
        
        Cell chotCuCell = dataRow.createCell(colNum++);
        chotCuCell.setCellValue(row.getChotCu());
        chotCuCell.setCellStyle(numberStyle);
        
        Cell donHuyCell = dataRow.createCell(colNum++);
        donHuyCell.setCellValue(row.getDonHuy());
        donHuyCell.setCellStyle(numberStyle);
        
        Cell tongMessCell = dataRow.createCell(colNum++);
        tongMessCell.setCellValue(row.getTongMess());
        tongMessCell.setCellStyle(numberStyle);
        
        Cell tongDonCell = dataRow.createCell(colNum++);
        tongDonCell.setCellValue(row.getTongDon());
        tongDonCell.setCellStyle(numberStyle);
        
        // Pháº§n trÄƒm: dá»¯ liá»‡u Ä‘ang á»Ÿ dáº¡ng %, cáº§n chia 100 Ä‘á»ƒ Excel format Ä‘Ãºng
        Cell donPerMessNhuCauCell = dataRow.createCell(colNum++);
        donPerMessNhuCauCell.setCellValue(row.getDonPerMessNhuCau() / 100.0);
        donPerMessNhuCauCell.setCellStyle(percentStyle);
        
        Cell donPerMessTongCell = dataRow.createCell(colNum++);
        donPerMessTongCell.setCellValue(row.getDonPerMessTong() / 100.0);
        donPerMessTongCell.setCellStyle(percentStyle);
        
        // tiLeHuyPercent lÃ  pháº§n trÄƒm (vÃ­ dá»¥ 12.5 cho 12.5%), cáº§n chia 100 Ä‘á»ƒ chuyá»ƒn sang tá»· lá»‡
        Cell tiLeHuyCell = dataRow.createCell(colNum++);
        tiLeHuyCell.setCellValue(row.getTiLeHuyPercent() / 100.0);
        tiLeHuyCell.setCellStyle(percentStyle);
      }

      // Auto-size columns
      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
        // TÄƒng width má»™t chÃºt Ä‘á»ƒ khÃ´ng bá»‹ cáº¯t
        sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
      }

      // Táº¡o tÃªn file vá»›i ngÃ y giá»
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
      String fileName = "saleReport_" + monthLabel + "_" + dateFormat.format(new java.util.Date()) + ".xlsx";

      // Convert workbook to byte array
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      workbook.write(outputStream);
      workbook.close();

      // Set response headers
      HttpHeaders responseHeaders = new HttpHeaders();
      responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
      responseHeaders.setContentDispositionFormData("attachment", fileName);

      return ResponseEntity.ok()
          .headers(responseHeaders)
          .body(outputStream.toByteArray());

    } catch (Exception e) {
      log.error("Error exporting Excel: {}", e.getMessage(), e);
      return ResponseEntity.internalServerError().build();
    }
  }
}
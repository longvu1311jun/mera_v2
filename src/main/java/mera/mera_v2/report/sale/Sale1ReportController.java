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
public class Sale1ReportController {

  private static final Logger log = LoggerFactory.getLogger(Sale1ReportController.class);

  private static final String SESSION_SALE_SUMMARY = "SESSION_SALE1_SUMMARY";
  private static final String SESSION_SALE_RANGE = "SESSION_SALE1_RANGE";
  private static final String SESSION_SALE_FETCHED_AT = "SESSION_SALE1_FETCHED_AT";

  private final LarkTokenService tokenService;
  private final BitableService bitableService;
  private final SaleReportCacheService cacheService;

  public Sale1ReportController(LarkTokenService tokenService, BitableService bitableService, SaleReportCacheService cacheService) {
    this.tokenService = tokenService;
    this.bitableService = bitableService;
    this.cacheService = cacheService;
  }

  @GetMapping("/sale1report")
  public String sale1Report(
      @RequestParam(value = "range", required = false, defaultValue = "CurrentMonth") String range,
      Model model,
      HttpSession session
  ) {
    model.addAttribute("range", range);
    return "sale1report";
  }

  @GetMapping("/api/sale1report/data")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> getSale1ReportData(
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
      log.info("Loading sale1 report data for range: {}", range);
      tokenService.autoRefreshTokenIfNeeded(session);

      // 1) session cache
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

      // 2) disk cache
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

      // 3) cache miss -> thống kế thật
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
      log.error("Error loading sale1 report: {}", e.getMessage(), e);
      response.put("hasToken", true);
      response.put("rows", List.of());
      response.put("range", range);
      response.put("error", e.getMessage());
    }

    return ResponseEntity.ok(response);
  }

  @PostMapping("/sale1report/refresh")
  public String refreshSale1Report(
      @RequestParam(value = "range", required = false, defaultValue = "CurrentMonth") String range,
      HttpSession session
  ) {
    // clear session
    session.removeAttribute(SESSION_SALE_SUMMARY);
    session.removeAttribute(SESSION_SALE_RANGE);
    session.removeAttribute(SESSION_SALE_FETCHED_AT);

    // clear disk
    cacheService.clear(range);

    return "redirect:/sale1report?range=" + range;
  }

  @GetMapping("/sale1report/export")
  public ResponseEntity<byte[]> exportToExcel(
      @RequestParam(value = "range", required = false, defaultValue = "CurrentMonth") String range,
      HttpSession session
  ) throws IOException {

    if (!tokenService.hasToken(session)) {
      return ResponseEntity.badRequest().build();
    }

    try {
      tokenService.autoRefreshTokenIfNeeded(session);

      // Lấy data từ session cache hoặc disk cache
      @SuppressWarnings("unchecked")
      List<SaleSummaryRow> rows = (List<SaleSummaryRow>) session.getAttribute(SESSION_SALE_SUMMARY);
      String cachedRange = (String) session.getAttribute(SESSION_SALE_RANGE);

      // Nếu không có trong session hoặc range khác, lấy từ disk cache
      if (rows == null || !range.equals(cachedRange)) {
        Optional<SaleReportCacheEntry> disk = cacheService.get(range);
        if (disk.isPresent()) {
          rows = disk.get().getRows();
        } else {
          // Nếu không có cache, load data mới
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

      // Tạo Excel file
      Workbook workbook = new XSSFWorkbook();
      Sheet sheet = workbook.createSheet("Sale1 Report");

      // Tạo style cho header
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

      // Tạo style cho số
      CellStyle numberStyle = workbook.createCellStyle();
      numberStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0"));

      // Tạo style cho phần trăm
      CellStyle percentStyle = workbook.createCellStyle();
      percentStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("0.0%"));

      // Xác định tháng hiển thị theo range
      LocalDateTime nowDtLabel = LocalDateTime.now(ZoneId.systemDefault());
      int currentMonthNum = nowDtLabel.getMonthValue();
      int targetMonthNum = "CurrentMonth".equals(range)
          ? currentMonthNum
          : (currentMonthNum == 1 ? 12 : currentMonthNum - 1);
      String monthLabel = "Tháng " + targetMonthNum;

      // Tạo title
      Row titleRow = sheet.createRow(0);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue("Thống kê tin nhắn phòng Sale " + monthLabel);
      CellStyle titleStyle = workbook.createCellStyle();
      Font titleFont = workbook.createFont();
      titleFont.setBold(true);
      titleFont.setFontHeightInPoints((short) 14);
      titleStyle.setFont(titleFont);
      titleStyle.setAlignment(HorizontalAlignment.CENTER);
      titleCell.setCellStyle(titleStyle);
      sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 13));

      // Dòng trống ngăn cách
      Row headerRow = sheet.createRow(2);
      String[] headers = {
          "#", "Tư vấn viên", "Nhu cầu", "Trùng", "Rác", "Không tương tác",
          "Chốt nóng", "Chốt cũ", "Đơn hủy", "Tổng mess", "Tổng đơn",
          "Đơn/mess nhu cầu", "Đơn/mess tổng", "Tỷ lệ hủy"
      };

      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // Thêm data rows
      int rowNum = 3;
      for (SaleSummaryRow row : rows) {
        Row dataRow = sheet.createRow(rowNum++);

        int colNum = 0;
        dataRow.createCell(colNum++).setCellValue(rowNum - 1); // STT
        dataRow.createCell(colNum++).setCellValue(row.getTableName() != null ? row.getTableName() : "");

        // Số nguyên
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

        // Phần trăm: dữ liệu đang ở dạng %, cần chia 100 để Excel format đúng
        Cell nhuCauRateCell = dataRow.createCell(colNum++);
        nhuCauRateCell.setCellValue(row.getDonPerMessNhuCau() / 100.0);
        nhuCauRateCell.setCellStyle(percentStyle);

        Cell tongRateCell = dataRow.createCell(colNum++);
        tongRateCell.setCellValue(row.getDonPerMessTong() / 100.0);
        tongRateCell.setCellStyle(percentStyle);

        Cell tiLeHuyCell = dataRow.createCell(colNum++);
        tiLeHuyCell.setCellValue(row.getTiLeHuyPercent() / 100.0);
        tiLeHuyCell.setCellStyle(percentStyle);
      }

      // Tính tổng
      long totalNhuCau = rows.stream().mapToLong(r -> r.getNhuCau()).sum();
      long totalTrung = rows.stream().mapToLong(r -> r.getTrung()).sum();
      long totalRac = rows.stream().mapToLong(r -> r.getRac()).sum();
      long totalKhongTuongTac = rows.stream().mapToLong(r -> r.getKhongTuongTac()).sum();
      long totalChotNong = rows.stream().mapToLong(r -> r.getChotNong()).sum();
      long totalChotCu = rows.stream().mapToLong(r -> r.getChotCu()).sum();
      long totalDonHuy = rows.stream().mapToLong(r -> r.getDonHuy()).sum();
      long totalTongMess = rows.stream().mapToLong(r -> r.getTongMess()).sum();
      long totalTongDon = rows.stream().mapToLong(r -> r.getTongDon()).sum();
      double totalTiLeHuy = totalTongDon > 0 ? (double) totalDonHuy / totalTongDon : 0;

      // Dòng tổng
      Row totalRow = sheet.createRow(rowNum);
      CellStyle totalStyle = workbook.createCellStyle();
      Font totalFont = workbook.createFont();
      totalFont.setBold(true);
      totalStyle.setFont(totalFont);
      totalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      totalStyle.setBorderBottom(BorderStyle.THIN);
      totalStyle.setBorderTop(BorderStyle.THIN);
      totalStyle.setBorderLeft(BorderStyle.THIN);
      totalStyle.setBorderRight(BorderStyle.THIN);

      int col = 0;
      Cell c0 = totalRow.createCell(col++);
      c0.setCellValue("");
      c0.setCellStyle(totalStyle);
      Cell c1 = totalRow.createCell(col++);
      c1.setCellValue("TỔNG");
      c1.setCellStyle(totalStyle);
      Cell c2 = totalRow.createCell(col++);
      c2.setCellValue(totalNhuCau);
      c2.setCellStyle(numberStyle);
      Cell c3 = totalRow.createCell(col++);
      c3.setCellValue(totalTrung);
      c3.setCellStyle(numberStyle);
      Cell c4 = totalRow.createCell(col++);
      c4.setCellValue(totalRac);
      c4.setCellStyle(numberStyle);
      Cell c5 = totalRow.createCell(col++);
      c5.setCellValue(totalKhongTuongTac);
      c5.setCellStyle(numberStyle);
      Cell c6 = totalRow.createCell(col++);
      c6.setCellValue(totalChotNong);
      c6.setCellStyle(numberStyle);
      Cell c7 = totalRow.createCell(col++);
      c7.setCellValue(totalChotCu);
      c7.setCellStyle(numberStyle);
      Cell c8 = totalRow.createCell(col++);
      c8.setCellValue(totalDonHuy);
      c8.setCellStyle(numberStyle);
      Cell c9 = totalRow.createCell(col++);
      c9.setCellValue(totalTongMess);
      c9.setCellStyle(numberStyle);
      Cell c10 = totalRow.createCell(col++);
      c10.setCellValue(totalTongDon);
      c10.setCellStyle(numberStyle);
      Cell c11 = totalRow.createCell(col++);
      c11.setCellValue("");
      c11.setCellStyle(totalStyle);
      Cell c12 = totalRow.createCell(col++);
      c12.setCellValue("");
      c12.setCellStyle(totalStyle);
      Cell c13 = totalRow.createCell(col++);
      c13.setCellValue(totalTiLeHuy);
      c13.setCellStyle(percentStyle);

      // Auto-size columns
      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
        // Tăng width một chút để không bị cắt
        sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
      }

      // Tạo tên file với ngày giờ
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
      String fileName = "sale1Report_" + monthLabel + "_" + dateFormat.format(new java.util.Date()) + ".xlsx";

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

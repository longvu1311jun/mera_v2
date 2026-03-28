package mera.mera_v2.model;

import java.util.List;

public class SaleReportCacheEntry {

  private String range;
  private long fetchedAtEpochMs;
  private List<SaleSummaryRow> rows;

  public SaleReportCacheEntry() {}

  public SaleReportCacheEntry(String range, long fetchedAtEpochMs, List<SaleSummaryRow> rows) {
    this.range = range;
    this.fetchedAtEpochMs = fetchedAtEpochMs;
    this.rows = rows;
  }

  public String getRange() {
    return range;
  }

  public void setRange(String range) {
    this.range = range;
  }

  public long getFetchedAtEpochMs() {
    return fetchedAtEpochMs;
  }

  public void setFetchedAtEpochMs(long fetchedAtEpochMs) {
    this.fetchedAtEpochMs = fetchedAtEpochMs;
  }

  public List<SaleSummaryRow> getRows() {
    return rows;
  }

  public void setRows(List<SaleSummaryRow> rows) {
    this.rows = rows;
  }
}


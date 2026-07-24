package mera.mera_v2.ads.model;

import java.util.List;

public class AdsReportResponse {

    public static class Range {
        private String from;
        private String to;

        public Range() {}
        public Range(String from, String to) { this.from = from; this.to = to; }

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }

        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
    }

    public static class Meta {
        private long ordersPulled;
        private long rows;

        public Meta() {}
        public Meta(long ordersPulled, long rows) { this.ordersPulled = ordersPulled; this.rows = rows; }

        public long getOrdersPulled() { return ordersPulled; }
        public void setOrdersPulled(long ordersPulled) { this.ordersPulled = ordersPulled; }

        public long getRows() { return rows; }
        public void setRows(long rows) { this.rows = rows; }
    }

    private Range range;
    private Meta meta;
    private List<AdsReportRow> rows;

    public AdsReportResponse() {}

    public AdsReportResponse(Range range, Meta meta, List<AdsReportRow> rows) {
        this.range = range;
        this.meta = meta;
        this.rows = rows;
    }

    public Range getRange() { return range; }
    public void setRange(Range range) { this.range = range; }

    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }

    public List<AdsReportRow> getRows() { return rows; }
    public void setRows(List<AdsReportRow> rows) { this.rows = rows; }
}

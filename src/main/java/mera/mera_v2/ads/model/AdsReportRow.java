package mera.mera_v2.ads.model;

public class AdsReportRow {
    private String postId;
    private String adsName;

    private long totalOrders;
    private long doneOrders;
    private long returnOrders;
    private long totalCod;
    private double conversionRate;

    public AdsReportRow() {}

    public AdsReportRow(String postId, String adsName, long totalOrders, long doneOrders, long returnOrders, long totalCod) {
        this.postId = postId;
        this.adsName = adsName;
        this.totalOrders = totalOrders;
        this.doneOrders = doneOrders;
        this.returnOrders = returnOrders;
        this.totalCod = totalCod;
        this.conversionRate = (totalOrders == 0) ? 0.0 : (doneOrders * 1.0 / totalOrders);
    }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getAdsName() { return adsName; }
    public void setAdsName(String adsName) { this.adsName = adsName; }

    public long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(long totalOrders) { this.totalOrders = totalOrders; }

    public long getDoneOrders() { return doneOrders; }
    public void setDoneOrders(long doneOrders) { this.doneOrders = doneOrders; }

    public long getReturnOrders() { return returnOrders; }
    public void setReturnOrders(long returnOrders) { this.returnOrders = returnOrders; }

    public long getTotalCod() { return totalCod; }
    public void setTotalCod(long totalCod) { this.totalCod = totalCod; }

    public double getConversionRate() { return conversionRate; }
    public void setConversionRate(double conversionRate) { this.conversionRate = conversionRate; }
}

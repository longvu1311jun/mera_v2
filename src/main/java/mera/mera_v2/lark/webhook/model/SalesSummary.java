package mera.mera_v2.lark.webhook.model;

import lombok.Data;

@Data
public class SalesSummary {

  private String staffName;

  private long nhuCau;
  private long trung;
  private long rac;
  private long khongTuongTac;
  private long chotNong;
  private long chotCu;
  private long donHuy;

  private long tongMes;
  private long tongDon;

  private double donMesNhuCau;
  private double donMesTong;
  private double tiLeHuy;
}

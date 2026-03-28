package mera.mera_v2.lark.webhook.model;

import lombok.Data;

import java.time.LocalDate;


@Data
public class CustomerRecord {

  private LocalDate createdDate;
  private String status;

  public CustomerRecord() {}

  public CustomerRecord(LocalDate createdDate, String status) {
    this.createdDate = createdDate;
    this.status = status;
  }
}

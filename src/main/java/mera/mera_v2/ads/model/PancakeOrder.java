package mera.mera_v2.ads.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PancakeOrder {

    @JsonProperty("id")
    private String id;

    @JsonProperty("status")
    private Integer status;

    @JsonProperty("post_id")
    private String postId;

    @JsonProperty("cod")
    private Long cod;

    @JsonProperty("money_to_collect")
    private Long moneyToCollect;

    @JsonProperty("marketer")
    private Marketer marketer;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Marketer {
        @JsonProperty("name")
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public Long getCod() { return cod; }
    public void setCod(Long cod) { this.cod = cod; }

    public Long getMoneyToCollect() { return moneyToCollect; }
    public void setMoneyToCollect(Long moneyToCollect) { this.moneyToCollect = moneyToCollect; }

    public Marketer getMarketer() { return marketer; }
    public void setMarketer(Marketer marketer) { this.marketer = marketer; }

    public String marketerNameSafe() {
        if (marketer == null) return null;
        return marketer.getName();
    }
}

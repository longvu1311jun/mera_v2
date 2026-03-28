package mera.mera_v2.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PosCustomerNote {
    @JsonProperty("created_at")
    private Long createdAt;

    @JsonProperty("created_by")
    private PosNoteCreator createdBy;

    @JsonProperty("edit_history")
    private List<PosNoteEdit> editHistory;

    @JsonProperty("id")
    private String id;

    @JsonProperty("images")
    private List<String> images;

    @JsonProperty("links")
    private List<String> links;

    @JsonProperty("message")
    private String message;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("removed_at")
    private Long removedAt;

    @JsonProperty("updated_at")
    private Long updatedAt;

    // Getters and setters
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public PosNoteCreator getCreatedBy() { return createdBy; }
    public void setCreatedBy(PosNoteCreator createdBy) { this.createdBy = createdBy; }

    public List<PosNoteEdit> getEditHistory() { return editHistory; }
    public void setEditHistory(List<PosNoteEdit> editHistory) { this.editHistory = editHistory; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    public List<String> getLinks() { return links; }
    public void setLinks(List<String> links) { this.links = links; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Long getRemovedAt() { return removedAt; }
    public void setRemovedAt(Long removedAt) { this.removedAt = removedAt; }

    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}


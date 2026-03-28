package mera.mera_v2.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PosNoteEdit {
    @JsonProperty("created_at")
    private Long createdAt;

    @JsonProperty("created_by")
    private PosNoteCreator createdBy;

    @JsonProperty("images")
    private List<String> images;

    @JsonProperty("message")
    private String message;

    // Getters and setters
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public PosNoteCreator getCreatedBy() { return createdBy; }
    public void setCreatedBy(PosNoteCreator createdBy) { this.createdBy = createdBy; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}


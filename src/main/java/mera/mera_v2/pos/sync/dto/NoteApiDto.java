package mera.mera_v2.pos.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class NoteApiDto {

    private String id;

    private String message;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("created_at")
    private Long createdAt;

    @JsonProperty("updated_at")
    private Long updatedAt;

    @JsonProperty("removed_at")
    private Long removedAt;

    private CreatedByApiDto createdBy;

    private List<ImageApiDto> images = new ArrayList<>();

    private List<LinkApiDto> links = new ArrayList<>();

    private List<NoteEditHistoryApiDto> editHistory = new ArrayList<>();

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreatedByApiDto {
        private String uid;
        @JsonProperty("fb_id")
        private String fbId;
        @JsonProperty("fb_name")
        private String fbName;
        @JsonProperty("pancake_id")
        private String pancakeId;
        @JsonProperty("token_for_business")
        private String tokenForBusiness;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageApiDto {
        private String id;
        private String url;
        @JsonProperty("thumb_url")
        private String thumbUrl;
        private String name;
        private Long size;
        private String type;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinkApiDto {
        private String url;
        private String title;
        private String description;
        private String image;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NoteEditHistoryApiDto {
        private String id;
        @JsonProperty("created_at")
        private Long createdAt;
        private String message;
        private List<ImageApiDto> images = new ArrayList<>();
        private CreatedByApiDto createdBy;
    }
}
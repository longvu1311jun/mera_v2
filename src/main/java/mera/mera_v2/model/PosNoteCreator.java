package mera.mera_v2.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PosNoteCreator {
    @JsonProperty("application")
    private Integer application;

    @JsonProperty("fb_id")
    private String fbId;

    @JsonProperty("fb_name")
    private String fbName;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("token_for_business")
    private String tokenForBusiness;

    @JsonProperty("uid")
    private String uid;

    // Getters and setters
    public Integer getApplication() { return application; }
    public void setApplication(Integer application) { this.application = application; }

    public String getFbId() { return fbId; }
    public void setFbId(String fbId) { this.fbId = fbId; }

    public String getFbName() { return fbName; }
    public void setFbName(String fbName) { this.fbName = fbName; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTokenForBusiness() { return tokenForBusiness; }
    public void setTokenForBusiness(String tokenForBusiness) { this.tokenForBusiness = tokenForBusiness; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
}


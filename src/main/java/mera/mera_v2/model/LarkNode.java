package mera.mera_v2.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LarkNode {
  @JsonProperty("node_token")
  private String nodeToken;
  
  @JsonProperty("obj_token")
  private String objToken;
  
  @JsonProperty("obj_type")
  private String objType;
  
  @JsonProperty("parent_node_token")
  private String parentNodeToken;
  
  @JsonProperty("space_id")
  private String spaceId;
  
  @JsonProperty("title")
  private String title;
  
  @JsonProperty("obj_create_time")
  private String objCreateTime;
  
  @JsonProperty("obj_edit_time")
  private String objEditTime;
  
  @JsonProperty("node_create_time")
  private String nodeCreateTime;
  
  // Child nodes (not from API, added programmatically)
  private java.util.List<LarkNode> childNodes;

  // Full content for matching (title + wiki page body combined)
  private String bodyContent;
  
  public String getNodeToken() {
    return nodeToken;
  }
  
  public void setNodeToken(String nodeToken) {
    this.nodeToken = nodeToken;
  }
  
  public String getObjToken() {
    return objToken;
  }
  
  public void setObjToken(String objToken) {
    this.objToken = objToken;
  }
  
  public String getObjType() {
    return objType;
  }
  
  public void setObjType(String objType) {
    this.objType = objType;
  }
  
  public String getParentNodeToken() {
    return parentNodeToken;
  }
  
  public void setParentNodeToken(String parentNodeToken) {
    this.parentNodeToken = parentNodeToken;
  }
  
  public String getSpaceId() {
    return spaceId;
  }
  
  public void setSpaceId(String spaceId) {
    this.spaceId = spaceId;
  }
  
  public String getTitle() {
    return title;
  }
  
  public void setTitle(String title) {
    this.title = title;
  }
  
  public String getObjCreateTime() {
    return objCreateTime;
  }
  
  public void setObjCreateTime(String objCreateTime) {
    this.objCreateTime = objCreateTime;
  }
  
  public String getObjEditTime() {
    return objEditTime;
  }
  
  public void setObjEditTime(String objEditTime) {
    this.objEditTime = objEditTime;
  }
  
  public String getNodeCreateTime() {
    return nodeCreateTime;
  }
  
  public void setNodeCreateTime(String nodeCreateTime) {
    this.nodeCreateTime = nodeCreateTime;
  }
  
  public java.util.List<LarkNode> getChildNodes() {
    return childNodes;
  }
  
  public void setChildNodes(java.util.List<LarkNode> childNodes) {
    this.childNodes = childNodes;
  }

  public String getBodyContent() {
    return bodyContent;
  }

  public void setBodyContent(String bodyContent) {
    this.bodyContent = bodyContent;
  }
}



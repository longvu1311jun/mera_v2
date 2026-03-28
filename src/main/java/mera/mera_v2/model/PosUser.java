package mera.mera_v2.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PosUser {
  @JsonProperty("id")
  private String id;
  
  @JsonProperty("user_id")
  private String userId;
  
  @JsonProperty("department")
  private Department department;
  
  @JsonProperty("user")
  private User user;
  
  // Getter for user name (from nested user object)
  public String getName() {
    return user != null ? user.getName() : null;
  }
  
  public String getId() {
    return id;
  }
  
  public void setId(String id) {
    this.id = id;
  }
  
  public String getUserId() {
    return userId;
  }
  
  public void setUserId(String userId) {
    this.userId = userId;
  }
  
  public Department getDepartment() {
    return department;
  }
  
  public void setDepartment(Department department) {
    this.department = department;
  }
  
  public User getUser() {
    return user;
  }
  
  public void setUser(User user) {
    this.user = user;
  }
  
  // Inner class for Department
  public static class Department {
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("name")
    private String name;
    
    public Long getId() {
      return id;
    }
    
    public void setId(Long id) {
      this.id = id;
    }
    
    public String getName() {
      return name;
    }
    
    public void setName(String name) {
      this.name = name;
    }
  }
  
  // Inner class for User
  public static class User {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("phone_number")
    private String phoneNumber;
    
    @JsonProperty("avatar_url")
    private String avatarUrl;
    
    public String getId() {
      return id;
    }
    
    public void setId(String id) {
      this.id = id;
    }
    
    public String getName() {
      return name;
    }
    
    public void setName(String name) {
      this.name = name;
    }
    
    public String getEmail() {
      return email;
    }
    
    public void setEmail(String email) {
      this.email = email;
    }
    
    public String getPhoneNumber() {
      return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
      this.phoneNumber = phoneNumber;
    }
    
    public String getAvatarUrl() {
      return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
      this.avatarUrl = avatarUrl;
    }
  }
}

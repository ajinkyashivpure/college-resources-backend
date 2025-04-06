package com.CollegeResources.dto;

import com.CollegeResources.model.Role;

public class LoginResponse {
    private String token;
    private Role role;
    private String userGroup;
    private String batchYear;
    private String department;

    public LoginResponse(String token, Role role) {
        this.token = token;
        this.role = role;
    }

    public LoginResponse(String token, Role role, String userGroup, String batchYear, String department) {
        this.token = token;
        this.role = role;
        this.userGroup = userGroup;
        this.batchYear = batchYear;
        this.department = department;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getUserGroup() {
        return userGroup;
    }

    public void setUserGroup(String userGroup) {
        this.userGroup = userGroup;
    }

    public String getBatchYear() {
        return batchYear;
    }

    public void setBatchYear(String batchYear) {
        this.batchYear = batchYear;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
}

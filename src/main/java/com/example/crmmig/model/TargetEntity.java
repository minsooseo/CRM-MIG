package com.kt.yaap.mig_batch.model;

import java.util.Date;

/**
 * 타겟 테이블 엔티티
 * 실제 타겟 테이블 구조에 맞게 수정 필요
 */
public class TargetEntity {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String address;
    private Date createdAt;
    private Date updatedAt;
    private String status;

    public TargetEntity() {
    }

    public TargetEntity(Long id, String name, String email, String phone, String address, 
                       Date createdAt, Date updatedAt, String status) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = status;
    }

    // Getters and Setters
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public static TargetEntityBuilder builder() {
        return new TargetEntityBuilder();
    }

    public static class TargetEntityBuilder {
        private Long id;
        private String name;
        private String email;
        private String phone;
        private String address;
        private Date createdAt;
        private Date updatedAt;
        private String status;

        public TargetEntityBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public TargetEntityBuilder name(String name) {
            this.name = name;
            return this;
        }

        public TargetEntityBuilder email(String email) {
            this.email = email;
            return this;
        }

        public TargetEntityBuilder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public TargetEntityBuilder address(String address) {
            this.address = address;
            return this;
        }

        public TargetEntityBuilder createdAt(Date createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public TargetEntityBuilder updatedAt(Date updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public TargetEntityBuilder status(String status) {
            this.status = status;
            return this;
        }

        public TargetEntity build() {
            return new TargetEntity(id, name, email, phone, address, createdAt, updatedAt, status);
        }
    }
}


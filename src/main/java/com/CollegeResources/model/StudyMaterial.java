package com.CollegeResources.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "studyMaterials")
public class StudyMaterial {

    @Id
    private String id;

    private String title;
    private String description;
    private String fileName;
    private String fileType;
    private String filePath;
    private String courseId;
    private String uploadedBy;
    private LocalDateTime uploadDate;

    public StudyMaterial() {
    }

    public StudyMaterial(String title, String description, String fileName,
                         String fileType, String filePath, String courseId,
                         String uploadedBy) {
        this.title = title;
        this.description = description;
        this.fileName = fileName;
        this.fileType = fileType;
        this.filePath = filePath;
        this.courseId = courseId;
        this.uploadedBy = uploadedBy;
        this.uploadDate = LocalDateTime.now();
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public LocalDateTime getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(LocalDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }
}
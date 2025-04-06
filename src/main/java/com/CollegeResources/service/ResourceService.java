package com.CollegeResources.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ResourceService {

    // Base directory for storing uploaded resources
    private final Path rootLocation = Paths.get("uploads");

    // Map to track resources by user group
    private final Map<String, Map<String, List<ResourceInfo>>> resourcesByGroup = new HashMap<>();

    public ResourceService() {
        // Create the upload directory if it doesn't exist
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage location", e);
        }

        // Initialize with some dummy data for testing
        initializeDummyData();
    }

    private void initializeDummyData() {
        // Create sample resources for different user groups
        addDummyResource("bt23cseds", "DSA", "Introduction to Algorithms", "lecture_notes.pdf");
        addDummyResource("bt23cseds", "DSA", "Sorting Algorithms", "sorting.pdf");
        addDummyResource("bt23cseds", "OS", "Process Management", "processes.pdf");

        addDummyResource("bt24cseds", "PF", "C++ Basics", "cpp_basics.pdf");
        addDummyResource("bt24cseds", "DM", "Set Theory", "sets.pdf");

        addDummyResource("bt23cseai", "AI", "Search Algorithms", "search.pdf");
        addDummyResource("bt23cseai", "ML", "Regression Models", "regression.pdf");
    }

    private void addDummyResource(String userGroup, String courseCode, String title, String filename) {
        resourcesByGroup.computeIfAbsent(userGroup, k -> new HashMap<>())
                .computeIfAbsent(courseCode, k -> new ArrayList<>())
                .add(new ResourceInfo(UUID.randomUUID().toString(), title, filename));
    }

    // Store a new resource file
    public ResourceInfo storeFile(MultipartFile file, String userGroup, String courseCode, String title) {
        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Failed to store empty file");
            }

            String originalFilename = file.getOriginalFilename();
            String fileId = UUID.randomUUID().toString();
            String storedFilename = fileId + "_" + originalFilename;

            // Create user group directory if it doesn't exist
            Path groupDir = rootLocation.resolve(userGroup);
            Path courseDir = groupDir.resolve(courseCode);
            Files.createDirectories(courseDir);

            // Copy file to the target location
            Path destinationFile = courseDir.resolve(storedFilename);
            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            // Create and store resource metadata
            ResourceInfo resourceInfo = new ResourceInfo(fileId, title, originalFilename);

            // Add to the in-memory map
            resourcesByGroup.computeIfAbsent(userGroup, k -> new HashMap<>())
                    .computeIfAbsent(courseCode, k -> new ArrayList<>())
                    .add(resourceInfo);

            return resourceInfo;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    // Get resources for a specific user group and course
    public List<ResourceInfo> getResourcesForCourse(String userGroup, String courseCode) {
        return resourcesByGroup
                .getOrDefault(userGroup, new HashMap<>())
                .getOrDefault(courseCode, new ArrayList<>());
    }

    // Get all resources for a user group
    public Map<String, List<ResourceInfo>> getAllResourcesForGroup(String userGroup) {
        return resourcesByGroup.getOrDefault(userGroup, new HashMap<>());
    }

    // Resource information class
    public static class ResourceInfo {
        private final String id;
        private final String title;
        private final String filename;

        public ResourceInfo(String id, String title, String filename) {
            this.id = id;
            this.title = title;
            this.filename = filename;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getFilename() {
            return filename;
        }
    }
}
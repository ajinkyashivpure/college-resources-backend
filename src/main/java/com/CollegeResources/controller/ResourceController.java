package com.CollegeResources.controller;

import com.CollegeResources.model.User;
import com.CollegeResources.repository.UserRepository;
import com.CollegeResources.service.ResourceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService resourceService;
    private final UserRepository userRepository;

    public ResourceController(ResourceService resourceService, UserRepository userRepository) {
        this.resourceService = resourceService;
        this.userRepository = userRepository;
    }

    // Students can get resources for their user group
    @GetMapping
    public ResponseEntity<?> getResourcesForCurrentUser() {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        User user = userRepository.findByEmail(email);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // For admin, allow access to all resources
        if (user.getRole().name().equals("ADMIN")) {
            // In a real implementation, you might want to provide a way to browse all resources
            return ResponseEntity.ok("Access to all resources granted for admin");
        }

        // For regular users, return resources for their specific group
        String userGroup = user.getUserGroup();
        if (userGroup == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User group not found");
        }

        Map<String, List<ResourceService.ResourceInfo>> resources = resourceService.getAllResourcesForGroup(userGroup);
        return ResponseEntity.ok(resources);
    }

    // Get resources for a specific course (for the current user's group)
    @GetMapping("/course/{courseCode}")
    public ResponseEntity<?> getResourcesForCourse(@PathVariable String courseCode) {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        User user = userRepository.findByEmail(email);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String userGroup = user.getUserGroup();
        if (userGroup == null && !user.getRole().name().equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User group not found");
        }

        // If admin, they need to specify the user group
        if (user.getRole().name().equals("ADMIN")) {
            return ResponseEntity.badRequest().body("Admin users must specify a user group");
        }

        List<ResourceService.ResourceInfo> resources = resourceService.getResourcesForCourse(userGroup, courseCode);
        return ResponseEntity.ok(resources);
    }

    // Admin endpoint to upload materials for a specific group and course
    @PostMapping("/admin/upload")
    public ResponseEntity<?> uploadResourceForGroup(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userGroup") String userGroup,
            @RequestParam("courseCode") String courseCode,
            @RequestParam("title") String title) {

        // Get current authenticated user and check if admin
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        User user = userRepository.findByEmail(email);

        if (user == null || !user.getRole().name().equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        try {
            ResourceService.ResourceInfo resourceInfo = resourceService.storeFile(file, userGroup, courseCode, title);
            return ResponseEntity.ok(resourceInfo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload file: " + e.getMessage());
        }
    }
}
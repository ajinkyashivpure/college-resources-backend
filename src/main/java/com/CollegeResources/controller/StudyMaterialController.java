package com.CollegeResources.controller;

import com.CollegeResources.model.Course;
import com.CollegeResources.model.Role;
import com.CollegeResources.model.StudyMaterial;
import com.CollegeResources.model.User;
import com.CollegeResources.repository.UserRepository;
import com.CollegeResources.service.CourseService;
import com.CollegeResources.service.StudyMaterialService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/materials")
public class StudyMaterialController {

    private final StudyMaterialService materialService;
    private final CourseService courseService;
    private final UserRepository userRepository;
    private final String uploadDir;

    public StudyMaterialController(StudyMaterialService materialService,
                                   CourseService courseService,
                                   UserRepository userRepository) {
        this.materialService = materialService;
        this.courseService = courseService;
        this.userRepository = userRepository;
        this.uploadDir = "uploads"; // Should match the value in application.properties
    }

    // Student endpoint to get materials for a course
    @GetMapping("/course/{courseId}")
    public ResponseEntity<?> getMaterialsForCourse(@PathVariable String courseId) {
        // Validate user has access to this course
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        // If admin, allow access
        if (user.getRole() != Role.ADMIN) {
            // For students, check if the course belongs to their department and semester
            Optional<Course> courseOpt = courseService.getCourseById(courseId);
            if (!courseOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Course course = courseOpt.get();
            if (!course.getDepartment().equals(user.getDepartment()) ||
                    !course.getBatchYear().equals(user.getBatchYear())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("You don't have access to this course");
            }
        }

        List<StudyMaterial> materials = materialService.getMaterialsByCourse(courseId);
        return ResponseEntity.ok(materials);
    }

    // Download a material file
    @GetMapping("/download/{materialId}")
    public ResponseEntity<?> downloadMaterial(@PathVariable String materialId) {
        System.out.println("Download request for material ID: " + materialId);

        Optional<StudyMaterial> materialOpt = materialService.getMaterialById(materialId);
        if (!materialOpt.isPresent()) {
            System.out.println("Material not found with ID: " + materialId);

            return ResponseEntity.notFound().build();
        }

        StudyMaterial material = materialOpt.get();
        System.out.println("Found material: " + material.getTitle());
        System.out.println("Original filename: " + material.getFileName());
        System.out.println("Stored path: " + material.getFilePath());

        // Check if user has access to this material
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        // If not admin, check access
        if (user.getRole() != Role.ADMIN) {
            Optional<Course> courseOpt = courseService.getCourseById(material.getCourseId());
            if (!courseOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Course course = courseOpt.get();
            if (!course.getDepartment().equals(user.getDepartment()) ||
                    !course.getBatchYear().equals(user.getBatchYear())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("You don't have access to this material");
            }
        }

        // Serve the file
        try {
            Path uploadDirectory = Paths.get(uploadDir);
            System.out.println("Upload directory absolute path: " + uploadDirectory.toAbsolutePath());

            Path filePath = uploadDirectory.resolve(material.getFilePath()).normalize();
            System.out.println("Trying to resolve file at: " + filePath.toAbsolutePath());

            if (Files.exists(filePath)) {
                System.out.println("File exists on disk: " + filePath);
                System.out.println("File size: " + Files.size(filePath) + " bytes");
            } else {
                System.out.println("FILE DOES NOT EXIST at path: " + filePath);
                // Check if the upload directory exists
                if (!Files.exists(uploadDirectory)) {
                    System.out.println("Upload directory does not exist!");
                }
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            String contentType = determineContentType(material.getFileType());
            System.out.println("Serving with content type: " + contentType);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + material.getFileName() + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, contentType);
            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            headers.add(HttpHeaders.PRAGMA, "no-cache");
            headers.add(HttpHeaders.EXPIRES, "0");

            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .body(resource);

        } catch (Exception e) {
            System.err.println("Error during file download: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    // Admin endpoint to upload a material
    @PostMapping(value="/admin/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadMaterial(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("courseId") String courseId) {

        // Validate current user is admin
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null || user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        // Validate the course exists
        Optional<Course> courseOpt = courseService.getCourseById(courseId);
        if (!courseOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Course not found with ID: " + courseId);
        }

        try {
            StudyMaterial material = materialService.uploadMaterial(
                    file, title, description, courseId, user.getEmail());

            return ResponseEntity.status(HttpStatus.CREATED).body(material);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload material: " + e.getMessage());
        }
    }

    // Admin endpoint to update material details
    @PutMapping("/admin/{id}")
    public ResponseEntity<?> updateMaterialDetails(
            @PathVariable String id,
            @RequestParam("title") String title,
            @RequestParam("description") String description) {

        // Validate current user is admin
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null || user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        Optional<StudyMaterial> materialOpt = materialService.getMaterialById(id);
        if (!materialOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        StudyMaterial updatedMaterial = materialService.updateMaterialDetails(id, title, description);
        return ResponseEntity.ok(updatedMaterial);
    }

    // Admin endpoint to delete a material
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<?> deleteMaterial(@PathVariable String id) {
        // Validate current user is admin
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null || user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        Optional<StudyMaterial> materialOpt = materialService.getMaterialById(id);
        if (!materialOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        try {
            materialService.deleteMaterial(id);
            return ResponseEntity.ok("Material deleted successfully");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting material: " + e.getMessage());
        }
    }

    // Admin endpoint to get all materials
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllMaterials() {
        // Validate current user is admin
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null || user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        List<StudyMaterial> materials = materialService.getAllMaterials();
        return ResponseEntity.ok(materials);
    }

    @GetMapping("/direct-download/{filename}")
    public void directDownload(@PathVariable String filename, HttpServletResponse response) {
        System.out.println("Direct download request for file: " + filename);

        Path filePath = Paths.get(uploadDir, filename);
        File file = filePath.toFile();

        System.out.println("Looking for file at: " + filePath.toAbsolutePath());

        try {
            if (!file.exists()) {
                System.out.println("ERROR: File does not exist at path: " + filePath.toAbsolutePath());
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Check file size
            if (file.length() == 0) {
                System.out.println("WARNING: File exists but has zero length: " + filePath.toAbsolutePath());
            } else {
                System.out.println("File found with size: " + file.length() + " bytes");
            }

            // Get content type from file extension
            String fileExtension = filename.substring(filename.lastIndexOf('.') + 1);
            String contentType = determineContentType(fileExtension);
            System.out.println("Using content type: " + contentType);

            // Set response headers
            response.setContentType(contentType);
            response.setContentLength((int) file.length());
            response.setHeader("Content-Disposition", "attachment; filename=\"" +
                    (filename.contains("-") ? filename.substring(filename.indexOf("-") + 1) : filename) + "\"");
            response.setHeader("X-Frame-Options", "ALLOWALL");

            // Stream the file directly to the response
            try (InputStream in = new FileInputStream(file);
                 OutputStream out = response.getOutputStream()) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesRead = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    try {
                        out.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                        out.flush(); // Flush after each write for larger files
                    } catch (IOException e) {
                        // Client disconnected - handle gracefully
                        System.out.println("Client disconnected during download: " + e.getMessage());
                        return; // Exit the method early
                    }
                }

                System.out.println("Sent " + totalBytesRead + " bytes to client");
            }
        } catch (IOException e) {
            System.err.println("Error processing download: " + e.getMessage());
            if (!(e instanceof ClientAbortException)) {
                // Only print stack trace for non-client abort exceptions
                e.printStackTrace();
            }

            try {
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } catch (IOException ignored) {
                // If we can't send the error, there's nothing we can do
            }
        }
    }


    @GetMapping("/view/{filename}")
    public ResponseEntity<?> viewMaterial(@PathVariable String filename) {
        Path filePath = Paths.get(uploadDir, filename);

        try {
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            // Get content type based on file extension
            String fileExtension = filename.substring(filename.lastIndexOf('.') + 1);
            String contentType = determineContentType(fileExtension);

            // For viewing in browser, use "inline" instead of "attachment"
            return ResponseEntity.ok()
                    .header("X-Frame-Options", "ALLOWALL")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    // Helper method to determine content type
    private String determineContentType(String fileExtension) {
        if (fileExtension == null) {
            return "application/octet-stream";
        }

        switch (fileExtension.toLowerCase()) {
            case "pdf":
                return "application/pdf";
            case "doc":
            case "docx":
                return "application/msword";
            case "xls":
            case "xlsx":
                return "application/vnd.ms-excel";
            case "ppt":
            case "pptx":
                return "application/vnd.ms-powerpoint";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "txt":
                return "text/plain";
            default:
                return "application/octet-stream";
        }
    }
}
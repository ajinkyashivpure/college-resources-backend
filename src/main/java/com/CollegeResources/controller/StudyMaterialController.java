package com.CollegeResources.controller;

import com.CollegeResources.model.Course;
import com.CollegeResources.model.Role;
import com.CollegeResources.model.StudyMaterial;
import com.CollegeResources.model.User;
import com.CollegeResources.repository.StudyMaterialRepository;
import com.CollegeResources.repository.UserRepository;
import com.CollegeResources.service.CourseService;
import com.CollegeResources.service.StudyMaterialService;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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
    private final StudyMaterialRepository studyMaterialRepository;

    @Value("${s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;


    public StudyMaterialController(StudyMaterialService materialService,
                                   CourseService courseService,
                                   UserRepository userRepository, StudyMaterialRepository studyMaterialRepository) {
        this.materialService = materialService;
        this.courseService = courseService;
        this.userRepository = userRepository;
        this.studyMaterialRepository = studyMaterialRepository;
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

        // Create S3 client
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(accessKey, secretKey)))
                .withRegion(region)
                .build();

        try {
            // Check if file exists in S3
            boolean fileExists = s3Client.doesObjectExist(bucketName, filename);

            if (!fileExists) {
                System.out.println("ERROR: File does not exist in S3 bucket: " + bucketName + ", key: " + filename);
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Get object metadata to check file size and content type
            ObjectMetadata metadata = s3Client.getObjectMetadata(bucketName, filename);
            long fileSize = metadata.getContentLength();

            // Check file size
            if (fileSize == 0) {
                System.out.println("WARNING: File exists but has zero length: " + filename);
            } else {
                System.out.println("File found with size: " + fileSize + " bytes");
            }

            // Get content type from file extension if not provided by S3
            String contentType = metadata.getContentType();
            if (contentType == null || contentType.equals("application/octet-stream")) {
                String fileExtension = filename.substring(filename.lastIndexOf('.') + 1);
                contentType = determineContentType(fileExtension);
            }
            System.out.println("Using content type: " + contentType);

            // Set response headers
            response.setContentType(contentType);
            response.setContentLengthLong(fileSize);
            response.setHeader("Content-Disposition", "attachment; filename=\"" +
                    (filename.contains("-") ? filename.substring(filename.indexOf("-") + 1) : filename) + "\"");
            response.setHeader("X-Frame-Options", "ALLOWALL");

            // Get the S3 object and stream it to the response
            S3Object s3Object = s3Client.getObject(bucketName, filename);
            try (InputStream in = s3Object.getObjectContent();
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
            } finally {
                // Important: Always close S3Object to release resources
                s3Object.close();
            }
        } catch (AmazonS3Exception e) {
            System.err.println("S3 Error: " + e.getMessage());
            try {
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } catch (IOException ignored) {
                // If we can't send the error, there's nothing we can do
            }
        } catch (IOException e) {
            System.err.println("Error processing download: " + e.getMessage());
            try {
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } catch (IOException ignored) {
                // If we can't send the error, there's nothing we can do
            }
        }
    }

    @GetMapping("/view/{id}")
    public ResponseEntity<?> viewMaterial(@PathVariable String id) {
        Optional<StudyMaterial> materialOpt = studyMaterialRepository.findById(id);

        if (materialOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        StudyMaterial material = materialOpt.get();
        String fileUrl = material.getFileUrl();

        // Ensure the URL is properly formatted for S3
        if (!fileUrl.startsWith("https://")) {
            // If for some reason the URL isn't complete, construct it
            // This is just a safeguard in case your storage logic changes
            fileUrl = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + fileUrl;
        }

        // Validate URL
        try {
            URI uri = new URI(fileUrl);
            if (!uri.isAbsolute()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Invalid file URL: not absolute");
            }
        }
        catch (URISyntaxException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Invalid file URL: " + e.getMessage());
        }

        // Get file metadata (optional) to check if file exists
        // Comment out if you don't want this additional check
    /*
    try {
        AmazonS3 s3Client = getS3Client(); // You would need to implement this method
        String objectKey = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        s3Client.getObjectMetadata(bucketName, objectKey);
    } catch (AmazonS3Exception e) {
        if (e.getStatusCode() == 404) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("File not found in S3 bucket");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error accessing file: " + e.getMessage());
    }
    */

        // Redirect to the S3 URL
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(fileUrl))
                .build();
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
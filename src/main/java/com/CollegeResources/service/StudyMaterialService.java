package com.CollegeResources.service;

import com.CollegeResources.model.StudyMaterial;
import com.CollegeResources.repository.StudyMaterialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StudyMaterialService {

    private final StudyMaterialRepository studyMaterialRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public StudyMaterialService(StudyMaterialRepository studyMaterialRepository) {
        this.studyMaterialRepository = studyMaterialRepository;
    }

    /**
     * Uploads a new study material
     */
    public StudyMaterial uploadMaterial(MultipartFile file, String title, String description,
                                        String courseId, String uploadedBy) throws IOException {
        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            System.out.println("Created upload directory: " + uploadPath);
        }

        System.out.println("Upload directory: " + uploadPath);
        System.out.println("File to upload: " + file.getOriginalFilename() + ", size: " + file.getSize() + " bytes");

        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file");
        }

        // Generate unique file name
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + "." + fileExtension;

        // Save file to disk with rigorous error checking
        Path filePath = uploadPath.resolve(uniqueFilename);
        System.out.println("Saving file to: " + filePath);

        try (InputStream inputStream = file.getInputStream()) {
            // Read the content of the file into memory to verify it's not empty
            byte[] bytes = inputStream.readAllBytes();
            System.out.println("Read " + bytes.length + " bytes from input stream");

            if (bytes.length == 0) {
                throw new IOException("File content is empty");
            }

            // Write the file content to disk
            Files.write(filePath, bytes);

            // Verify the file was saved correctly
            if (Files.exists(filePath)) {
                long fileSize = Files.size(filePath);
                System.out.println("File saved successfully. Size on disk: " + fileSize + " bytes");
                if (fileSize == 0) {
                    throw new IOException("File was saved but is empty on disk");
                }
            } else {
                throw new IOException("File was not saved to disk");
            }
        }

        // Create and save study material
        StudyMaterial material = new StudyMaterial(
                title,
                description,
                originalFilename,
                fileExtension,
                uniqueFilename,  // Store just the filename, not the full path
                courseId,
                uploadedBy
        );

        return studyMaterialRepository.save(material);
    }
    /**
     * Gets all study materials
     */
    public List<StudyMaterial> getAllMaterials() {
        return studyMaterialRepository.findAll();
    }

    /**
     * Gets study materials for a course
     */
    public List<StudyMaterial> getMaterialsByCourse(String courseId) {
        return studyMaterialRepository.findByCourseId(courseId);
    }

    /**
     * Gets a material by ID
     */
    public Optional<StudyMaterial> getMaterialById(String id) {
        return studyMaterialRepository.findById(id);
    }

    /**
     * Deletes a study material
     */
    public void deleteMaterial(String id) throws IOException {
        Optional<StudyMaterial> materialOpt = studyMaterialRepository.findById(id);
        if (materialOpt.isPresent()) {
            StudyMaterial material = materialOpt.get();

            // Delete file from disk
            Path filePath = Paths.get(uploadDir).resolve(material.getFilePath());
            Files.deleteIfExists(filePath);

            // Delete from database
            studyMaterialRepository.deleteById(id);
        }
    }

    /**
     * Updates material details (not the file itself)
     */
    public StudyMaterial updateMaterialDetails(String id, String title, String description) {
        Optional<StudyMaterial> materialOpt = studyMaterialRepository.findById(id);
        if (materialOpt.isPresent()) {
            StudyMaterial material = materialOpt.get();
            material.setTitle(title);
            material.setDescription(description);
            return studyMaterialRepository.save(material);
        }
        return null;
    }

    /**
     * Search for materials by title
     */
    public List<StudyMaterial> searchMaterialsByTitle(String searchTerm) {
        return studyMaterialRepository.findByTitleContainingIgnoreCase(searchTerm);
    }

    /**
     * Helper method to extract file extension
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return filename.substring(dotIndex + 1);
    }
}
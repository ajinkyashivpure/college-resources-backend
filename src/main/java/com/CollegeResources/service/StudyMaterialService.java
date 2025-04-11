package com.CollegeResources.service;

import com.CollegeResources.model.StudyMaterial;
import com.CollegeResources.repository.StudyMaterialRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class StudyMaterialService {

    private final StudyMaterialRepository studyMaterialRepository;
    private final Cloudinary cloudinary;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public StudyMaterialService(StudyMaterialRepository studyMaterialRepository, Cloudinary cloudinary) {
        this.studyMaterialRepository = studyMaterialRepository;
        this.cloudinary = cloudinary;
    }

    /**
     * Uploads a new study material
     */
    public StudyMaterial uploadMaterial(MultipartFile file, String title, String description,
                                        String courseId, String uploadedBy) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file");
        }

        Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
                ObjectUtils.asMap("resource_type", "raw"));

        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String cloudinaryUrl = uploadResult.get("secure_url").toString();

        // Create and save study material
        StudyMaterial material = new StudyMaterial(
                title,
                description,
                originalFilename,
                fileExtension,
                cloudinaryUrl,  // Store just the filename, not the full path
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

            String fileUrl = material.getFileUrl();
            String publicId = extractPublicIdFromUrl(fileUrl);

            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());

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

    private String extractPublicIdFromUrl(String fileUrl) {
        // Example URL: https://res.cloudinary.com/your-cloud-name/raw/upload/v1234567890/my-folder/my-file.pdf
        // Public ID: my-folder/my-file (without extension)

        try {
            String[] parts = fileUrl.split("/");
            String filenameWithExt = parts[parts.length - 1]; // e.g., my-file.pdf
            String filename = filenameWithExt.substring(0, filenameWithExt.lastIndexOf(".")); // my-file

            // Get folder structure from the URL
            StringBuilder publicIdBuilder = new StringBuilder();
            for (int i = parts.length - 2; i >= 0; i--) {
                if (parts[i].equals("upload")) break;
                publicIdBuilder.insert(0, parts[i] + "/");
            }
            publicIdBuilder.append(filename);

            return publicIdBuilder.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract publicId from Cloudinary URL: " + fileUrl);
        }
    }

}
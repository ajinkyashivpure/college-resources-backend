package com.CollegeResources.service;

import com.CollegeResources.model.StudyMaterial;
import com.CollegeResources.repository.StudyMaterialRepository;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

@Service
public class StudyMaterialService {

    private final StudyMaterialRepository studyMaterialRepository;


    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${s3.bucket.name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    public StudyMaterialService(StudyMaterialRepository studyMaterialRepository) {
        this.studyMaterialRepository = studyMaterialRepository;
    }

    /**
     * Uploads a new study material
     */
    public StudyMaterial uploadMaterial(MultipartFile file, String title, String description,
                                        String courseId, String uploadedBy) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file");
        }


        // Create S3 client
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(accessKey, secretKey)))
                .withRegion(region)
                .build();

        //unique file name
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFileName = UUID.randomUUID().toString() + "." + fileExtension;

        // Upload the file to S3
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            s3Client.putObject(new PutObjectRequest(
                    bucketName,
                    uniqueFileName,
                    file.getInputStream(),
                    metadata)
                    .withCannedAcl(CannedAccessControlList.PublicRead)); // Makes the file publicly readable

            // S3 URL
            String s3Url = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + uniqueFileName;

            // save
            StudyMaterial material = new StudyMaterial(
                    title,
                    description,
                    originalFilename,
                    fileExtension,
                    s3Url,
                    courseId,
                    uploadedBy
            );

            return studyMaterialRepository.save(material);

        } catch (AmazonServiceException e) {
            throw new IOException("Failed to upload file to Amazon S3: " + e.getMessage());
        } catch (SdkClientException e) {
            throw new IOException("Error communicating with Amazon S3: " + e.getMessage());
        }
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
//        Optional<StudyMaterial> materialOpt = studyMaterialRepository.findById(id);
//        if (materialOpt.isPresent()) {
//            StudyMaterial material = materialOpt.get();
//
//            String fileUrl = material.getFileUrl();
//            String publicId = extractPublicIdFromUrl(fileUrl);
//
//            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
//
//            // Delete from database
//            studyMaterialRepository.deleteById(id);
//        }
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
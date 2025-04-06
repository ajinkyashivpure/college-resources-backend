package com.CollegeResources.repository;

import com.CollegeResources.model.StudyMaterial;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyMaterialRepository extends MongoRepository<StudyMaterial, String> {

    List<StudyMaterial> findByCourseId(String courseId);

    List<StudyMaterial> findByTitleContainingIgnoreCase(String searchTerm);

}
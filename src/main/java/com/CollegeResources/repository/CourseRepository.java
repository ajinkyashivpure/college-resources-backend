package com.CollegeResources.repository;

import com.CollegeResources.model.Course;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends MongoRepository<Course, String> {

    List<Course> findByDepartmentAndSemesterAndBatchYear(String department, int semester, String batchYear);

}
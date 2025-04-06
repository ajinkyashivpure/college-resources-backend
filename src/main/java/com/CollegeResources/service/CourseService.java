package com.CollegeResources.service;

import com.CollegeResources.model.Course;
import com.CollegeResources.repository.CourseRepository;
import com.CollegeResources.utils.SemesterCalculator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CourseService {

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    /**
     * Creates a new course
     */
    public Course createCourse(Course course) {
        return courseRepository.save(course);
    }

    /**
     * Updates an existing course
     */
    public Course updateCourse(String id, Course courseDetails) {
        Optional<Course> courseOptional = courseRepository.findById(id);
        if (courseOptional.isPresent()) {
            Course course = courseOptional.get();
            course.setCourseCode(courseDetails.getCourseCode());
            course.setCourseName(courseDetails.getCourseName());
            course.setInstructor(courseDetails.getInstructor());
            course.setDescription(courseDetails.getDescription());
            course.setDepartment(courseDetails.getDepartment());
            course.setSemester(courseDetails.getSemester());
            course.setBatchYear(courseDetails.getBatchYear());
            return courseRepository.save(course);
        }
        return null;
    }

    /**
     * Gets all courses
     */
    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    /**
     * Gets a course by ID
     */
    public Optional<Course> getCourseById(String id) {
        return courseRepository.findById(id);
    }

    /**
     * Deletes a course
     */
    public void deleteCourse(String id) {
        courseRepository.deleteById(id);
    }

    /**
     * Gets courses for a student based on their department, batch year, and current semester
     */
    public List<Course> getCoursesForStudent(String department, String batchYear) {
        // Calculate current semester based on batch year
        int currentSemester = SemesterCalculator.calculateCurrentSemester(batchYear);

        // Get courses for the department, semester, and batch
        return courseRepository.findByDepartmentAndSemesterAndBatchYear(
                department, currentSemester, batchYear);
    }

    /**
     * Gets courses for a specific semester
     */
    public List<Course> getCoursesBySemester(String department, int semester, String batchYear) {
        return courseRepository.findByDepartmentAndSemesterAndBatchYear(
                department, semester, batchYear);
    }

    /**
     * Gets the current semester information for a batch
     */
    public String getCurrentSemesterInfo(String batchYear) {
        int semester = SemesterCalculator.calculateCurrentSemester(batchYear);
        return SemesterCalculator.getSemesterDateRange(batchYear, semester);
    }
}
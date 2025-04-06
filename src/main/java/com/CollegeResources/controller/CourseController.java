package com.CollegeResources.controller;

import com.CollegeResources.model.Course;
import com.CollegeResources.model.Role;
import com.CollegeResources.model.User;
import com.CollegeResources.repository.UserRepository;
import com.CollegeResources.service.CourseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final UserRepository userRepository;

    public CourseController(CourseService courseService, UserRepository userRepository) {
        this.courseService = courseService;
        this.userRepository = userRepository;
    }

    // Student endpoint to get their current courses
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentCourses() {
        // Get current user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        if (user.getRole() == Role.ADMIN) {
            return ResponseEntity.badRequest().body("Admin users don't have courses");
        }

        List<Course> courses = courseService.getCoursesForStudent(user.getDepartment(), user.getBatchYear());

        Map<String, Object> response = new HashMap<>();
        response.put("courses", courses);
        response.put("semester", user.getCurrentSemester());
        response.put("semesterDateRange", user.getSemesterDateRange());

        return ResponseEntity.ok(response);
    }

    // Student endpoint to get courses for a specific semester
    @GetMapping("/semester/{semester}")
    public ResponseEntity<?> getCoursesBySemester(@PathVariable int semester) {
        // Get current user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        if (user.getRole() == Role.ADMIN) {
            return ResponseEntity.badRequest()
                    .body("Admin users must specify department and batch year");
        }

        if (semester < 1 || semester > 8) {
            return ResponseEntity.badRequest().body("Invalid semester number");
        }

        List<Course> courses = courseService.getCoursesBySemester(
                user.getDepartment(), semester, user.getBatchYear());

        return ResponseEntity.ok(courses);
    }

    // Admin endpoint to create a course
    @PostMapping("/admin")
    public ResponseEntity<?> createCourse(@RequestBody Course course) {
        // Validate current user is admin
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null || user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        // Validate course data
        if (course.getCourseCode() == null || course.getCourseName() == null ||
                course.getDepartment() == null || course.getBatchYear() == null) {
            return ResponseEntity.badRequest().body("Required fields are missing");
        }

        if (course.getSemester() < 1 || course.getSemester() > 8) {
            return ResponseEntity.badRequest().body("Invalid semester number");
        }

        Course newCourse = courseService.createCourse(course);
        return ResponseEntity.status(HttpStatus.CREATED).body(newCourse);
    }

    // Admin endpoint to update a course
    @PutMapping("/admin/{id}")
    public ResponseEntity<?> updateCourse(@PathVariable String id, @RequestBody Course course) {
        // Validate current user is admin
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null || user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        Course updatedCourse = courseService.updateCourse(id, course);
        if (updatedCourse == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(updatedCourse);
    }

    // Admin endpoint to delete a course
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable String id) {
        // Validate current user is admin
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null || user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        Optional<Course> course = courseService.getCourseById(id);
        if (!course.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        courseService.deleteCourse(id);
        return ResponseEntity.ok("Course deleted successfully");
    }

    // Admin endpoint to get all courses
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllCourses() {
        // Validate current user is admin
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null || user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        List<Course> courses = courseService.getAllCourses();
        return ResponseEntity.ok(courses);
    }

    // Admin endpoint to get courses by department and semester
    @GetMapping("/admin/department/{department}/semester/{semester}/batch/{batchYear}")
    public ResponseEntity<?> getCoursesByDepartmentAndSemester(
            @PathVariable String department,
            @PathVariable int semester,
            @PathVariable String batchYear) {

        // Validate current user is admin
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null || user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        if (semester < 1 || semester > 8) {
            return ResponseEntity.badRequest().body("Invalid semester number");
        }

        List<Course> courses = courseService.getCoursesBySemester(department, semester, batchYear);
        return ResponseEntity.ok(courses);
    }

    // Admin endpoint to get course by ID
    @GetMapping("/admin/{id}")
    public ResponseEntity<?> getCourseById(@PathVariable String id) {
        // Validate current user is admin
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());

        if (user == null || user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        Optional<Course> course = courseService.getCourseById(id);
        if (!course.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(course.get());
    }
}
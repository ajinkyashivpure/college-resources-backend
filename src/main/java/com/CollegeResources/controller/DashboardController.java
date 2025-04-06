package com.CollegeResources.controller;

import com.CollegeResources.model.Role;
import com.CollegeResources.model.User;
import com.CollegeResources.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final UserRepository userRepository;

    public DashboardController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> getDashboardData() {
        // Get currently authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        User user = userRepository.findByEmail(email);

        if (user == null) {
            return ResponseEntity.badRequest().body("User not found");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("name", user.getName());
        response.put("email", user.getEmail());

        // Admin dashboard
        if (user.getRole() == Role.ADMIN) {
            response.put("dashboardType", "admin");
            response.put("message", "Welcome to the Admin Dashboard");
            // Add admin-specific data here
            return ResponseEntity.ok(response);
        }

        // Student dashboard
        response.put("dashboardType", "student");
        response.put("userGroup", user.getUserGroup());
        response.put("batchYear", user.getBatchYear());
        response.put("department", user.getDepartment());
        response.put("currentSemester", user.getCurrentSemester());
        response.put("semesterDateRange", user.getSemesterDateRange());

        // Add courses and materials based on batch year and department
        Map<String, Object> coursesData = generateCoursesData(user.getBatchYear(), user.getDepartment());
        response.put("courses", coursesData);

        return ResponseEntity.ok(response);
    }

    // Helper method to generate appropriate courses based on batch year and department
    private Map<String, Object> generateCoursesData(String batchYear, String department) {
        Map<String, Object> courses = new HashMap<>();

        // Base courses for demonstration (in production, this would come from a database)
        if ("cseds".equalsIgnoreCase(department)) {
            if ("23".equals(batchYear)) {  // 2023 batch
                courses.put("DSA", createCourseInfo("Data Structures and Algorithms", "Prof. Smith"));
                courses.put("OS", createCourseInfo("Operating Systems", "Prof. Johnson"));
                courses.put("DBMS", createCourseInfo("Database Management Systems", "Prof. Davis"));
            } else if ("24".equals(batchYear)) {  // 2024 batch
                courses.put("PF", createCourseInfo("Programming Fundamentals", "Prof. Wilson"));
                courses.put("DM", createCourseInfo("Discrete Mathematics", "Prof. Brown"));
            }
        } else if ("cseai".equalsIgnoreCase(department)) {
            if ("23".equals(batchYear)) {
                courses.put("AI", createCourseInfo("Artificial Intelligence", "Prof. Roberts"));
                courses.put("ML", createCourseInfo("Machine Learning", "Prof. Lewis"));
            } else if ("24".equals(batchYear)) {
                courses.put("PF", createCourseInfo("Programming Fundamentals", "Prof. Wilson"));
                courses.put("DL", createCourseInfo("Deep Learning Basics", "Prof. Garcia"));
            }
        } else if ("mech".equalsIgnoreCase(department)) {
            if ("23".equals(batchYear)) {
                courses.put("TF", createCourseInfo("Thermodynamics", "Prof. Anderson"));
                courses.put("FM", createCourseInfo("Fluid Mechanics", "Prof. Martin"));
            } else if ("24".equals(batchYear)) {
                courses.put("EM", createCourseInfo("Engineering Mechanics", "Prof. Thompson"));
                courses.put("MS", createCourseInfo("Material Science", "Prof. White"));
            }
        }

        return courses;
    }

    private Map<String, String> createCourseInfo(String courseName, String instructor) {
        Map<String, String> courseInfo = new HashMap<>();
        courseInfo.put("name", courseName);
        courseInfo.put("instructor", instructor);
        return courseInfo;
    }
}
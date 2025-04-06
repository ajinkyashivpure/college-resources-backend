package com.CollegeResources.controller;

import com.CollegeResources.model.User;
import com.CollegeResources.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Get all users
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        List<User> users = userRepository.findAll();

        // Map to simplified user info without sensitive data
        List<Map<String, Object>> userInfo = users.stream()
                .map(user -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("id", user.getId());
                    info.put("name", user.getName());
                    info.put("email", user.getEmail());
                    info.put("role", user.getRole());
                    info.put("currentSemester", user.getCurrentSemester());

                    if (user.getUserGroup() != null) {
                        info.put("userGroup", user.getUserGroup());
                        info.put("batchYear", user.getBatchYear());
                        info.put("department", user.getDepartment());
                    }

                    return info;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(userInfo);
    }

    // Get users by department
    @GetMapping("/users/department/{department}")
    public ResponseEntity<?> getUsersByDepartment(@PathVariable String department) {
        List<User> users = userRepository.findAll().stream()
                .filter(user -> department.equals(user.getDepartment()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    // Get users by batch year
    @GetMapping("/users/batch/{year}")
    public ResponseEntity<?> getUsersByBatchYear(@PathVariable String year) {
        List<User> users = userRepository.findAll().stream()
                .filter(user -> year.equals(user.getBatchYear()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

}
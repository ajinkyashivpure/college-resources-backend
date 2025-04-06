package com.CollegeResources.controller;

import com.CollegeResources.config.EmailService;
import com.CollegeResources.config.JwtTokenProvider;
import com.CollegeResources.dto.LoginRequest;
import com.CollegeResources.dto.SignupRequest;
import com.CollegeResources.model.Role;
import com.CollegeResources.model.User;
import com.CollegeResources.repository.UserRepository;
import com.CollegeResources.utils.EmailValidator;
import com.CollegeResources.utils.SemesterCalculator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("api/user")
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    private final String ADMIN_EMAIL = "ajinkyashivpure@gmail.com";
    private final HashMap<String, String> otpStore = new HashMap<>();

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, JwtTokenProvider jwtTokenProvider,
                          PasswordEncoder passwordEncoder, EmailService emailService) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest signupRequest) {
        // Check if email already exists
        if (userRepository.findByEmail(signupRequest.getEmail()) != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email already exists");
        }

        // Validate if it's a PEC email or the admin email
        String email = signupRequest.getEmail();
        if (!email.equals(ADMIN_EMAIL) && !EmailValidator.isPecEmail(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Only PEC email addresses (@pec.edu.in) are allowed to register");
        }

        // Validate the department from the email (if it's a student email)
        if (!email.equals(ADMIN_EMAIL)) {
            String department = EmailValidator.extractDepartment(email);
            if (department == null || !isValidDepartment(department)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Invalid department in email address. Valid departments are: " +
                                "cse, cseds, cseai, vlsi, ele, ece, mech, civil, meta, prod");
            }
        }

        // Generate and send OTP
        String otp = String.valueOf((int) (Math.random() * 9000) + 1000);
        otpStore.put(email, otp);
        emailService.sendOtpEmail(email, otp);

        // Store signup data temporarily
        otpStore.put(email + "_data",
                signupRequest.getName() + "," + passwordEncoder.encode(signupRequest.getPassword()));

        return ResponseEntity.ok("OTP sent to " + email);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestParam String email, @RequestParam String otp) {
        String storedOtp = otpStore.get(email);
        if (storedOtp != null && storedOtp.equals(otp)) {
            otpStore.remove(email);

            String[] userData = otpStore.get(email + "_data").split(",");
            String name = userData[0];
            String encodedPassword = userData[1];

            // Determine user role (ADMIN or USER)
            Role role = email.equals(ADMIN_EMAIL) ? Role.ADMIN : Role.USER;

            User user = new User();
            user.setName(name);
            user.setEmail(email);
            user.setPassword(encodedPassword);
            user.setRole(role);

            // Extract batch and department for student accounts
            if (role == Role.USER) {
                String userGroup = EmailValidator.extractBatchAndDepartment(email);
                String batchYear = EmailValidator.extractBatchYear(email);
                String department = EmailValidator.extractDepartment(email);

                user.setUserGroup(userGroup);
                user.setBatchYear(batchYear);
                user.setDepartment(department);

                // Calculate current semester based on batch year
                int currentSemester = SemesterCalculator.calculateCurrentSemester(batchYear);
                user.setCurrentSemester(currentSemester);

                // Set semester date range
                String semesterDateRange = SemesterCalculator.getSemesterDateRange(batchYear, currentSemester);
                user.setSemesterDateRange(semesterDateRange);
            }

            userRepository.save(user);
            otpStore.remove(email + "_data");

            return ResponseEntity.ok("Signup complete. Redirecting to dashboard...");
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired OTP.");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.getEmail());
        if (user == null || !passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid email or password");
        }

        // If it's a student user, update the current semester information
        if (user.getRole() == Role.USER) {
            int currentSemester = SemesterCalculator.calculateCurrentSemester(user.getBatchYear());
            String semesterDateRange = SemesterCalculator.getSemesterDateRange(user.getBatchYear(), currentSemester);

            user.setCurrentSemester(currentSemester);
            user.setSemesterDateRange(semesterDateRange);
            userRepository.save(user);
        }

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(user.getRole().name())
                .build();

        String token = jwtTokenProvider.generateToken(userDetails);

        // Return user information with JWT token for frontend routing
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("role", user.getRole().name());

        // Add group information for student users
        if (user.getRole() == Role.USER) {
            response.put("userGroup", user.getUserGroup());
            response.put("batchYear", user.getBatchYear());
            response.put("department", user.getDepartment());
            response.put("currentSemester", user.getCurrentSemester());
            response.put("semesterDateRange", user.getSemesterDateRange());
        }

        return ResponseEntity.ok(response);
    }

    // Helper method to validate department
    private boolean isValidDepartment(String department) {
        if (department == null) {
            return false;
        }

        String[] validDepartments = {"cse", "cseds", "cseai", "vlsi", "ele", "ece", "mech", "civil", "meta", "prod"};

        for (String valid : validDepartments) {
            if (valid.equalsIgnoreCase(department)) {
                return true;
            }
        }

        return false;
    }
}
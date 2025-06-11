package com.CollegeResources.controller;

import com.CollegeResources.config.EmailService;
import com.CollegeResources.model.PasswordResetRequest;
import com.CollegeResources.model.PasswordResetToken;
import com.CollegeResources.model.User;
import com.CollegeResources.repository.PasswordResetTokenRepository;
import com.CollegeResources.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/password")
public class PasswordResetController {


    private final UserRepository userRepository;


    private final  PasswordResetTokenRepository tokenRepository;


    private final  EmailService emailService;

    private final  PasswordEncoder passwordEncoder;

    public PasswordResetController(UserRepository userRepository, PasswordResetTokenRepository tokenRepository, EmailService emailService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    // Step 1: Request password reset
    @PostMapping("/forgot")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");

        // Find user by email
        User user = userRepository.findByEmail(email);
        if (user==null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found with this email"));
        }

        // Generate token
        String token = UUID.randomUUID().toString();

        // Save token in database
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUserEmail(email);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(15)); // Token valid for 15 minutes
        tokenRepository.save(resetToken);

        // Send email with token
        emailService.sendPasswordResetToken(email, token);

        // Return success response
        return ResponseEntity.ok(Map.of(
                "message", "Password reset link sent to your email",
                "email", email
        ));
    }

    // Step 2: Verify token and reset password
    @PostMapping("/reset")
    public ResponseEntity<?> resetPassword(@RequestBody PasswordResetRequest request) {
        String token = request.getToken();
        String newPassword = request.getNewPassword();

        // Find token in database
        Optional<PasswordResetToken> tokenOptional = tokenRepository.findByToken(token);
        if (tokenOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid or expired token"));
        }

        PasswordResetToken resetToken = tokenOptional.get();

        // Check if token is expired
        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(resetToken);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Token has expired"));
        }

        // Find user and update password
        User user = userRepository.findByEmail(resetToken.getUserEmail());
        if (user==null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Delete used token
        tokenRepository.delete(resetToken);

        // Return success response
        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully"));
    }
}
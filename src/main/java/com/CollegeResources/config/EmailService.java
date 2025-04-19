package com.CollegeResources.config;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service

public class EmailService {

    private final JavaMailSender javaMailSender;

    public EmailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    // Send OTP email
    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("your-email@gmail.com");
        message.setTo(toEmail);
        message.setSubject("📚 Welcome to PEC-Portal - Email Verification");

        String emailBody = "Hi there,\n\n" +
                "Thank you for registering on *PEC-Portal* – your one-stop destination for organized, exam-focused study material.\n\n" +
                "To complete your email verification, please use the OTP below:\n\n" +
                "🔐 Your OTP: " + otp + "\n\n" +
                "This OTP is valid for the next 10 minutes.\n" +
                "Please do not share it with anyone for your account's security.\n\n" +
                "If you didn’t request this verification, you can safely ignore this email.\n\n" +
                "Best wishes,\n" +
                "Team PEC-Portal\n" +
                "📘 Empowering Students, One Resource at a Time";

        message.setText(emailBody);
        javaMailSender.send(message);
        System.out.println("OTP sent to " + toEmail);
    }

}
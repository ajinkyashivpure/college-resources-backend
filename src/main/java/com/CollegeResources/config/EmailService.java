package com.CollegeResources.config;

import com.mailgun.api.v3.MailgunMessagesApi;
import com.mailgun.client.MailgunClient;
import com.mailgun.model.message.Message;
import com.mailgun.model.message.MessageResponse;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final MailgunMessagesApi mailgunMessagesApi;
    private final String domain;
    private final String fromEmail;

    public EmailService(
            @Value("${mailgun.api.key}") String apiKey,
            @Value("${mailgun.domain}") String domain,
            @Value("${mailgun.from.email}") String fromEmail) {
        this.mailgunMessagesApi = MailgunClient.config(apiKey)
                .createApi(MailgunMessagesApi.class);
        this.domain = domain;
        this.fromEmail = fromEmail;
    }

    // Send OTP email with HTML formatting
    public void sendOtpEmail(String toEmail, String otp) {
        String htmlBody = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "  <style>" +
                "    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                "    .container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                "    .header { text-align: center; margin-bottom: 20px; }" +
                "    .otp-box { background-color: #f5f5f5; padding: 15px; text-align: center; font-size: 24px; font-weight: bold; letter-spacing: 5px; margin: 20px 0; border-radius: 5px; }" +
                "    .footer { margin-top: 30px; font-size: 12px; color: #777; text-align: center; }" +
                "    .highlight { font-weight: bold; color: #4a86e8; }" +
                "  </style>" +
                "</head>" +
                "<body>" +
                "  <div class='container'>" +
                "    <div class='header'>" +
                "      <h2>📚 Welcome to <span class='highlight'>PEC-Portal</span></h2>" +
                "    </div>" +
                "    <p>Hi there,</p>" +
                "    <p>Thank you for registering on <strong>PEC-Portal</strong> – your one-stop destination for organized, exam-focused study material.</p>" +
                "    <p>To complete your email verification, please use the OTP below:</p>" +
                "    <div class='otp-box'>🔐 " + otp + "</div>" +
                "    <p><strong>This OTP is valid for the next 10 minutes.</strong><br>" +
                "    Please do not share it with anyone for your account's security.</p>" +
                "    <p>If you didn't request this verification, you can safely ignore this email.</p>" +
                "    <p>Best wishes,<br>" +
                "    Team PEC-Portal<br>" +
                "    <span class='highlight'>📘 Empowering Students, One Resource at a Time</span></p>" +
                "    <div class='footer'>" +
                "      <p>This is an automated message, please do not reply to this email.</p>" +
                "    </div>" +
                "  </div>" +
                "</body>" +
                "</html>";

        Message message = Message.builder()
                .from(fromEmail)
                .to(toEmail)
                .subject("📚 Welcome to PEC-Portal - Email Verification")
                .html(htmlBody)  // Using HTML instead of text
                .build();

        try {
            MessageResponse response = mailgunMessagesApi.sendMessage(domain, message);
//            System.out.println("OTP sent to " + toEmail + ", Mailgun ID: " + response.getId());
        } catch (Exception e) {
//            System.err.println("Failed to send email: " + e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }

    public void sendPasswordResetToken(String toEmail, String token) {
        String htmlBody = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "  <style>" +
                "    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                "    .container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                "    .header { text-align: center; margin-bottom: 20px; }" +
                "    .token-box { background-color: #f5f5f5; padding: 15px; text-align: center; " +
                "                font-size: 24px; font-weight: bold; letter-spacing: 5px; " +
                "                margin: 20px 0; border-radius: 5px; }" +
                "    .footer { margin-top: 30px; font-size: 12px; color: #777; text-align: center; }" +
                "    .highlight { font-weight: bold; color: #4a86e8; }" +
                "  </style>" +
                "</head>" +
                "<body>" +
                "  <div class='container'>" +
                "    <div class='header'>" +
                "      <h2>📚 PEC-Portal Password Reset</h2>" +
                "    </div>" +
                "    <p>Hi there,</p>" +
                "    <p>We received a request to reset your password for your PEC-Portal account.</p>" +
                "    <p>Use the following code to reset your password:</p>" +
                "    <div class='token-box'>" + token + "</div>" +
                "    <p><strong>This code is valid for 15 minutes.</strong></p>" +
                "    <p>If you didn't request this password reset, you can safely ignore this email.</p>" +
                "    <p>Best wishes,<br>" +
                "    Team PEC-Portal<br>" +
                "    <span class='highlight'>📘 Empowering Students, One Resource at a Time</span></p>" +
                "    <div class='footer'>" +
                "      <p>This is an automated message, please do not reply to this email.</p>" +
                "    </div>" +
                "  </div>" +
                "</body>" +
                "</html>";

        Message message = Message.builder()
                .from(fromEmail)
                .to(toEmail)
                .subject("📚 PEC-Portal - Password Reset Code")
                .html(htmlBody)
                .build();

        try {
            MessageResponse response = mailgunMessagesApi.sendMessage(domain, message);
            System.out.println("Password reset token sent to " + toEmail + ", Mailgun ID: " + response.getId());
        } catch (Exception e) {
            System.err.println("Failed to send password reset token: " + e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }


    public void sendResetOtpEmail(String toEmail, String otp) {
        String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f4f4f4; }" +
                ".container { max-width: 600px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 10px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }" +
                ".header { text-align: center; color: #333; }" +
                ".otp-box { background-color: #f8f9fa; padding: 20px; text-align: center; border-radius: 5px; margin: 20px 0; }" +
                ".otp-code { font-size: 32px; font-weight: bold; color: #007bff; letter-spacing: 5px; }" +
                ".warning { color: #dc3545; font-size: 14px; margin-top: 20px; }" +
                ".footer { text-align: center; color: #666; font-size: 12px; margin-top: 30px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<h1 class='header'>Password Reset Request</h1>" +
                "<p>Hello,</p>" +
                "<p>You have requested to reset your password. Please use the following OTP to verify your identity:</p>" +
                "<div class='otp-box'>" +
                "<div class='otp-code'>" + otp + "</div>" +
                "</div>" +
                "<p>This OTP is valid for 10 minutes and can only be used once.</p>" +
                "<div class='warning'>" +
                "<strong>Security Notice:</strong> If you did not request this password reset, please ignore this email and your password will remain unchanged." +
                "</div>" +
                "<div class='footer'>" +
                "<p>This is an automated email. Please do not reply.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";

        Message message = Message.builder()
                .from(fromEmail)
                .to(toEmail)
                .subject("📚 PEC-Portal - Password Reset Code")
                .html(htmlContent)
                .build();

        try {
            MessageResponse response = mailgunMessagesApi.sendMessage(domain, message);
            System.out.println("Password reset token sent to " + toEmail + ", Mailgun ID: " + response.getId());
        } catch (Exception e) {
            System.err.println("Failed to send password reset token: " + e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }


    }
}

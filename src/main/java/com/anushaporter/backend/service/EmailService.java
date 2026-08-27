package com.anushaporter.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender emailSender;

    public void sendOtpEmail(String to, String otp) {
        if (emailSender == null) {
            return;
        }
        try {
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom("Anusha Porter Support <anushaporter@gmail.com>");
            helper.setReplyTo("anushaporter@gmail.com");
            helper.setTo(to);
            helper.setSubject("Anusha Porter - Your Verification Code");
            
            String htmlMsg = "<div style='font-family: Arial, sans-serif; padding: 20px; color: #333;'>"
                    + "<h2 style='color: #f97316;'>Welcome to Anusha Porter!</h2>"
                    + "<p>Your OTP for account verification is: <strong style='font-size: 24px;'>" + otp + "</strong></p>"
                    + "<p>This code will expire in 10 minutes.</p>"
                    + "<br/><p>Thank you,<br/><strong>The Anusha Porter Team</strong></p>"
                    + "</div>";
            helper.setText(htmlMsg, true);
            
            emailSender.send(message);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    public void sendPasswordResetEmail(String to, String otp) {
        if (emailSender == null) {
            return;
        }
        try {
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom("Anusha Porter Support <anushaporter@gmail.com>");
            helper.setReplyTo("anushaporter@gmail.com");
            helper.setTo(to);
            helper.setSubject("Anusha Porter - Password Reset Code");
            
            String htmlMsg = "<div style='font-family: Arial, sans-serif; padding: 20px; color: #333;'>"
                    + "<h2 style='color: #f97316;'>Password Reset Request</h2>"
                    + "<p>We received a request to reset your password. Your reset code is:</p>"
                    + "<p><strong style='font-size: 24px; color: #000;'>" + otp + "</strong></p>"
                    + "<p>This code will expire in 10 minutes.</p>"
                    + "<br/><p>If you didn't request this, please ignore this email.</p>"
                    + "<br/><p>Thank you,<br/><strong>The Anusha Porter Team</strong></p>"
                    + "</div>";
            helper.setText(htmlMsg, true);
            
            emailSender.send(message);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}

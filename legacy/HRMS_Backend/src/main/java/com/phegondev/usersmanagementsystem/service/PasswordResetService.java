package com.phegondev.usersmanagementsystem.service;


import com.phegondev.usersmanagementsystem.dto.ResetPasswordRequest;
import com.phegondev.usersmanagementsystem.entity.PasswordResetToken;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.repository.PasswordResetTokenRepository;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;


import java.time.LocalDateTime;


@Service
public class PasswordResetService {

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UsersRepo userRepository;

    @Autowired
    private JavaMailSender mailSender;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public void sendResetLink(String email) {
        OurUsers user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No account found with this email!"));

        //  Check if new password is the same as the current one
        if (passwordEncoder.matches("NewPasswordFromFrontend", user.getPassword())) { // 👈 You'll need to pass this dynamically
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password cannot be the same as the old password!");
        }

        //  Delete any existing reset token before creating a new one
        tokenRepository.deleteByUser(user);

        //  Generate a new reset token
        PasswordResetToken resetToken = new PasswordResetToken(user);
        tokenRepository.save(resetToken);

        sendResetEmail(user.getEmail(), resetToken.getToken());
    }


    // Send an email containing the reset link
    private void sendResetEmail(String email, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Password Reset Request");
        message.setText(
                "Hello,\n\n"
                        + "You requested to reset your password. Click the link below:\n"
                        + "🔗 http://localhost:5173/reset-password?token=" + token + "\n\n"
                        + "If you didn't request this, please ignore this email.\n\n"
                        + "Best regards,\n"
                        + "HRMS Team"
        );
        mailSender.send(message);
    }



    // Reset the user's password
    public ResponseEntity<String> resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(resetToken);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Reset token has expired! Request a new one.");
        }

        OurUsers user = resetToken.getUser();

        //  Check if new password matches old password
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("New password cannot be the same as the old password!");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        tokenRepository.delete(resetToken); //  Delete token after successful reset

        return ResponseEntity.ok("Password reset successful!");
    }




    /**
     *  Strong Password Validation
     */
    private boolean isValidPassword(String password) {
        return password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,16}$");
    }

}

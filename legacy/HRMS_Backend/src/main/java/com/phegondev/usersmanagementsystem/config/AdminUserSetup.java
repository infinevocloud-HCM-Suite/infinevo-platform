package com.phegondev.usersmanagementsystem.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.phegondev.usersmanagementsystem.service.useraccess.UserService;



@Component
@Order(1)
public class AdminUserSetup  implements CommandLineRunner
{

	private final UserService userService;

    @Autowired
    public AdminUserSetup(UserService userService) {
      
        this.userService = userService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Starting admin setup...");
        userService.setupAdminUser();
        System.out.println("Admin setup completed.");
    }

}


package com.anushaporter.backend;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.repository.AppUserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.anushaporter.backend.repository.VehicleRepository;
import java.util.Optional;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private com.anushaporter.backend.repository.PricingVehicleRepository vehicleRepository;



	@org.springframework.beans.factory.annotation.Value("${admin.email:}")
	private String adminEmail;

	@org.springframework.beans.factory.annotation.Value("${admin.password:}")
	private String adminPassword;

	@org.springframework.beans.factory.annotation.Value("${admin.name:Super Admin}")
	private String adminName;

	@Bean
	public CommandLineRunner initDatabase() {
		return args -> {
			// All default dummy data seeding has been disabled.
			// The database will now remain empty if you delete records.
			System.out.println("Default data seeding is disabled. Database will remain empty.");

			// Seed Admin User from Environment Variables
			if (adminEmail != null && !adminEmail.trim().isEmpty() && adminPassword != null && !adminPassword.trim().isEmpty()) {
				System.out.println("Checking for admin user from environment variables: " + adminEmail);
				Optional<AppUser> adminOpt = userRepository.findByEmail(adminEmail);
				AppUser adminUser = adminOpt.orElse(new AppUser());
				adminUser.setName(adminName);
				adminUser.setEmail(adminEmail);
				adminUser.setPhone("0000000000"); // Default phone, can be updated via UI
				adminUser.setRole("Super Admin");
				adminUser.setStatus("Active");
				adminUser.setPassword(BCrypt.hashpw(adminPassword, BCrypt.gensalt()));
				userRepository.save(adminUser);
				System.out.println("Admin user seeded/updated successfully from environment variables.");
			}
		};
	}


}

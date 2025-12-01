package com.example.BajajHealthAssignment;

import com.example.BajajHealthAssignment.service.AssignmentService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BajajHealthAssignmentApplication {

	public static void main(String[] args) {

		SpringApplication.run(BajajHealthAssignmentApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(AssignmentService assignmentService) {
		return args -> assignmentService.execute();
	}

}

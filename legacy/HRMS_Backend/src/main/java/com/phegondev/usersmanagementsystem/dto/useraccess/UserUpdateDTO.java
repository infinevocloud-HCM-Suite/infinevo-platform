package com.phegondev.usersmanagementsystem.dto.useraccess;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class UserUpdateDTO {
	
	    @NotBlank(message = "Username is required")
	    private String username;


	    @NotBlank(message = "First name is required")
	    private String firstName;

	    @NotBlank(message = "Last name is required")
	    private String lastName;

	    @Email(message = "Email should be valid")
	    private String email;
	    
	    private String phone;
	    
	    private String department;

		public String getUsername() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public String getFirstName() {
			return firstName;
		}

		public void setFirstName(String firstName) {
			this.firstName = firstName;
		}

		public String getLastName() {
			return lastName;
		}

		public void setLastName(String lastName) {
			this.lastName = lastName;
		}

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}

		public String getPhone() {
			return phone;
		}

		public void setPhone(String phone) {
			this.phone = phone;
		}

		public String getDepartment() {
			return department;
		}

		public void setDepartment(String department) {
			this.department = department;
		}

		public UserUpdateDTO() {
			super();
			// TODO Auto-generated constructor stub
		}

		public UserUpdateDTO(@NotBlank(message = "Username is required") String username,
				@NotBlank(message = "First name is required") String firstName,
				@NotBlank(message = "Last name is required") String lastName,
				@Email(message = "Email should be valid") String email, String phone, String department) {
			super();
			this.username = username;
			this.firstName = firstName;
			this.lastName = lastName;
			this.email = email;
			this.phone = phone;
			this.department = department;
		}

		@Override
		public String toString() {
			return "UserUpdateDTO [username=" + username + ", firstName=" + firstName + ", lastName=" + lastName
					+ ", email=" + email + ", phone=" + phone + ", department=" + department + "]";
		}
	    
	    

}

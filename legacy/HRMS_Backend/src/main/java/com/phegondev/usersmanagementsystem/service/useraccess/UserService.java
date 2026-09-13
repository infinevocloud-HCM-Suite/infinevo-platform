package com.phegondev.usersmanagementsystem.service.useraccess;

import java.util.List;

import com.phegondev.usersmanagementsystem.dto.useraccess.MapUserRequestDto;
import com.phegondev.usersmanagementsystem.dto.useraccess.UserActionDTO;


public interface UserService {
	
	 List<UserActionDTO> getUserActions(Integer id);
	 boolean mapActionsToUser(MapUserRequestDto request);
	 void setupAdminUser();

}

package com.itsdev.payroll.serviceimpl.organization;

import com.itsdev.payroll.dto.organization.OrganizationRoleDTO;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.organization.OrganizationRole;
import com.itsdev.payroll.mapper.organization.OrganizationRoleMapper;
import com.itsdev.payroll.repository.OrganizationUserRoleMappingRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.organization.OrganizationRoleRepository;
import com.itsdev.payroll.service.keycloak.KeycloakUserService;
import com.itsdev.payroll.service.organization.OrganizationRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class OrganizationRoleServiceImpl implements OrganizationRoleService {

    private final OrganizationRoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationUserRoleMappingRepository organizationUserRoleMappingRepository;

    public OrganizationRoleServiceImpl(OrganizationRoleRepository roleRepository,
                                       OrganizationRepository organizationRepository,
                                       OrganizationUserRoleMappingRepository organizationUserRoleMappingRepository) {
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.organizationUserRoleMappingRepository = organizationUserRoleMappingRepository;
    }

    private String generateUnique10DigitRoleId() {
        Random random = new Random();
        String candidate;
        int tries = 0;
        do {
            long number = 1_000_000_000L + (Math.abs(random.nextLong()) % 9_000_000_000L);
            candidate = String.valueOf(number);
            tries++;
            if (tries > 50) {
                candidate = String.valueOf(System.currentTimeMillis()).substring(0, 10);
                break;
            }
        } while (roleRepository.existsByRoleId(candidate));
        return candidate;
    }

    @Override
    @Transactional
    public OrganizationRoleDTO createRole(String organizationId, OrganizationRoleDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        // check duplicate role name in this organization
        if (roleRepository.existsByRoleNameAndOrganizationAndIsDeletedFalse(dto.getRoleName(), org)) {
            throw new RuntimeException(
                    "Role with name [" + dto.getRoleName() + "] already exists for this organization"
            );
        }

        String generatedRoleId = generateUnique10DigitRoleId();

        OrganizationRole role = OrganizationRoleMapper.toEntity(dto, org);
        role.setRoleId(generatedRoleId);

        OrganizationRole saved = roleRepository.save(role);
        return OrganizationRoleMapper.toDto(saved);
    }

    @Override
    @Transactional
    public OrganizationRoleDTO updateRole(String organizationId, String roleId, OrganizationRoleDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        OrganizationRole role = roleRepository.findByRoleIdAndIsDeletedFalse(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));

        if (!role.getOrganization().getOrganizationId().equals(org.getOrganizationId())) {
            throw new RuntimeException("Role does not belong to this organization");
        }

        if (!role.getRoleName().equalsIgnoreCase(dto.getRoleName()) &&
                roleRepository.existsByRoleNameAndOrganizationAndIsDeletedFalse(dto.getRoleName(), org)) {
            throw new RuntimeException(
                    "Role with name [" + dto.getRoleName() + "] already exists for this organization"
            );
        }

        role.setRoleName(dto.getRoleName());
        role.setAccessType(dto.getAccessType());
        role.setUserActionRequired(dto.getUserActionRequired());
        role.setIsDefault(dto.getIsDefault());
        role.setRoleDescription(dto.getRoleDescription());
        role.setStatus(dto.getStatus());

        OrganizationRole updated = roleRepository.save(role);

        //Sync roleName in OrganizationUserRoleMapping for auto update everywhere
        organizationUserRoleMappingRepository.updateRoleNameForMappings(roleId, dto.getRoleName());

        return OrganizationRoleMapper.toDto(updated);
    }

    @Override
    public OrganizationRoleDTO getRole(String organizationId, String roleId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        OrganizationRole role = roleRepository.findByRoleIdAndIsDeletedFalse(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));

        if (!role.getOrganization().getOrganizationId().equals(org.getOrganizationId())) {
            throw new RuntimeException("Role does not belong to this organization");
        }

        return OrganizationRoleMapper.toDto(role);
    }

    @Override
    public List<OrganizationRoleDTO> getAllRoles(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        List<OrganizationRole> roles = roleRepository.findAllByOrganizationAndIsDeletedFalse(org);
        return roles.stream().map(OrganizationRoleMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteRole(String organizationId, String roleId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        OrganizationRole role = roleRepository.findByRoleIdAndIsDeletedFalse(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));

        if (!role.getOrganization().getOrganizationId().equals(org.getOrganizationId())) {
            throw new RuntimeException("Role does not belong to this organization");
        }

        role.setIsDeleted(true);
        roleRepository.save(role);
    }


}

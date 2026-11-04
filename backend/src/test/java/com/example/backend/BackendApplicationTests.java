package com.example.backend;

import com.example.backend.model.UserEntity;
import com.example.backend.repository.ModuleRepository;
import com.example.backend.repository.PrivilegeRepository;
import com.example.backend.repository.RolePrivilegeRepository;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BackendApplicationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ModuleRepository moduleRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;

    @Autowired
    private RolePrivilegeRepository rolePrivilegeRepository;

    @Test
    void testOracleDatabaseConnectionAndQueries() {
        // 1. Test Roles from Oracle
        long roleCount = roleRepository.count();
        System.out.println(">>> ORACLE TEST: Role count = " + roleCount);
        assertTrue(roleCount > 0, "Role count should be greater than 0");

        // 2. Test Modules from Oracle
        long moduleCount = moduleRepository.count();
        System.out.println(">>> ORACLE TEST: Module count = " + moduleCount);
        assertTrue(moduleCount > 0, "Module count should be greater than 0");

        // 3. Test Privileges from Oracle
        long privilegeCount = privilegeRepository.count();
        System.out.println(">>> ORACLE TEST: Privilege count = " + privilegeCount);
        assertTrue(privilegeCount > 0, "Privilege count should be greater than 0");

        // 4. Test Users from Oracle
        long userCount = userRepository.count();
        System.out.println(">>> ORACLE TEST: User count = " + userCount);
        assertTrue(userCount > 0, "User count should be greater than 0");

        // 5. Test specific SIMRS user lookup (dr.agus)
        Optional<UserEntity> drAgus = userRepository.findByUsername("dr.agus");
        if (drAgus.isEmpty()) {
            drAgus = userRepository.findByUsername("dr. agus");
        }
        System.out.println(">>> ORACLE TEST: dr. agus present? " + drAgus.isPresent());
        assertTrue(drAgus.isPresent(), "User 'dr.agus' should be found in Oracle DB");
        System.out.println(">>> ORACLE TEST: dr. agus Full Name = " + drAgus.get().getFullName() + ", Role = " + drAgus.get().getRole().getRoleName());
        assertEquals("Dokter", drAgus.get().getRole().getRoleName());

        // 6. Test specific user (anggun - kasir)
        Optional<UserEntity> anggun = userRepository.findByUsername("anggun");
        assertTrue(anggun.isPresent(), "User 'anggun' should be found in Oracle DB");
        System.out.println(">>> ORACLE TEST: anggun Full Name = " + anggun.get().getFullName() + ", Role = " + anggun.get().getRole().getRoleName());

        // 8. Test Layer 1 Endpoint: GET /api/roles/modules
        var moduleResponse = roleController.getAllModules();
        assertNotNull(moduleResponse.getBody());
        assertEquals(20, moduleResponse.getBody().getData().size());
        System.out.println(">>> LAYER 1 ENDPOINT: /api/roles/modules returned " + moduleResponse.getBody().getData().size() + " SIMRS modules");

        // 9. Test Layer 1 Endpoint: GET /api/roles/users?roleId=101 (Only Doctors)
        var doctorUsers = roleController.getUsers(101);
        assertNotNull(doctorUsers.getBody());
        assertFalse(doctorUsers.getBody().getData().isEmpty());
        System.out.println(">>> LAYER 1 ENDPOINT: /api/roles/users?roleId=101 returned " + doctorUsers.getBody().getData().size() + " active doctors");
        assertTrue(doctorUsers.getBody().getData().stream().allMatch(u -> u.getRoleId().equals(101)));

        // 10. Test Layer 1 Endpoint: GET /api/roles/users?roleId=221 (Only IGD Nurses)
        var igdUsers = roleController.getUsers(221);
        assertNotNull(igdUsers.getBody());
        assertFalse(igdUsers.getBody().getData().isEmpty());
        System.out.println(">>> LAYER 1 ENDPOINT: /api/roles/users?roleId=221 returned " + igdUsers.getBody().getData().size() + " active IGD staff");
        assertTrue(igdUsers.getBody().getData().stream().allMatch(u -> u.getRoleId().equals(221)));

        // 11. Test Layer 1 Endpoint: GET /api/roles/101/privileges (Doctor privileges)
        var doctorPrivs = roleController.getPrivilegesByRole(101);
        assertNotNull(doctorPrivs.getBody());
        assertEquals(18, doctorPrivs.getBody().getData().size());
        System.out.println(">>> LAYER 1 ENDPOINT: /api/roles/101/privileges returned " + doctorPrivs.getBody().getData().size() + " doctor privileges");
    }

    @Autowired
    private com.example.backend.controller.RoleController roleController;

    @Autowired
    private com.example.backend.service.AuthService authService;

}

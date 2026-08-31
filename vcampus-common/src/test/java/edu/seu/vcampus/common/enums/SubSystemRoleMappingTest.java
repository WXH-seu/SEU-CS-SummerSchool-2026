package edu.seu.vcampus.common.enums;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

/** Verifies the three-tier role exposed to sub-systems by the user module. */
public class SubSystemRoleMappingTest {

    @Test
    public void superAdminIsManagerEverywhere() {
        assertEquals(SubSystemRole.ADMIN, SubSystems.effectiveRole(
                Role.SUPER_ADMIN, Collections.<String>emptySet(), SubSystem.STUDENT));
    }

    @Test
    public void grantedSubsystemAdminIsManager() {
        assertEquals(SubSystemRole.ADMIN, SubSystems.effectiveRole(
                Role.SUBSYSADMIN, Collections.singleton("library"), SubSystem.LIBRARY));
    }

    @Test
    public void ungrantedSubsystemAdminIsTeacher() {
        assertEquals(SubSystemRole.TEACHER, SubSystems.effectiveRole(
                Role.SUBSYSADMIN, Collections.singleton("library"), SubSystem.STUDENT));
        assertEquals(SubSystemRole.TEACHER, SubSystems.effectiveRole(
                Role.SUBSYSADMIN, Collections.<String>emptySet(), SubSystem.COURSE));
    }

    @Test
    public void teacherAndStudentMapDirectly() {
        assertEquals(SubSystemRole.TEACHER, SubSystems.effectiveRole(
                Role.TEACHER, Collections.<String>emptySet(), SubSystem.STORE));
        assertEquals(SubSystemRole.STUDENT, SubSystems.effectiveRole(
                Role.STUDENT, Collections.<String>emptySet(), SubSystem.COURSE));
    }

    @Test
    public void mapsEveryAcademicOperationToStudentSubsystem() {
        Operation[] operations = {
                Operation.STUDENT_QUERY, Operation.STUDENT_SAVE, Operation.STUDENT_DELETE,
                Operation.TEACHER_QUERY, Operation.TEACHER_SAVE, Operation.TEACHER_DELETE,
                Operation.DEPARTMENT_QUERY, Operation.DEPARTMENT_SAVE,
                Operation.DEPARTMENT_DELETE, Operation.CLASS_QUERY,
                Operation.CLASS_SAVE, Operation.CLASS_DELETE
        };
        for (Operation operation : operations) {
            assertEquals(SubSystem.STUDENT, SubSystems.of(operation));
        }
    }
}

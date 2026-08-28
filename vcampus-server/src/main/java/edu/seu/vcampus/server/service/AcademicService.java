package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.AcademicQueryRequest;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.SchoolClassDto;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.TeacherDto;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.enums.SubSystems;
import edu.seu.vcampus.server.dao.AcademicRepository;
import edu.seu.vcampus.server.dao.UserAccount;
import edu.seu.vcampus.server.dao.UserRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Business rules and permission checks for academic information. */
public final class AcademicService {
    private static final Set<String> STUDENT_STATUSES = new HashSet<String>(
            Arrays.asList("在读", "休学", "毕业", "退学"));

    private final AcademicRepository repository;
    private final UserRepository userRepository;

    public AcademicService(AcademicRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public ArrayList<StudentDto> queryStudents(UserAccount actor, AcademicQueryRequest query)
            throws SQLException, BusinessException {
        requireActor(actor);
        if (effectiveRole(actor) == SubSystemRole.STUDENT) {
            StudentDto ownRecord = repository.findStudentByUserId(actor.getUserId());
            ArrayList<StudentDto> result = new ArrayList<StudentDto>();
            if (ownRecord != null) {
                result.add(ownRecord);
            }
            return result;
        }
        return new ArrayList<StudentDto>(repository.findStudents(query));
    }

    public ArrayList<TeacherDto> queryTeachers(UserAccount actor, AcademicQueryRequest query)
            throws SQLException, BusinessException {
        requireStaff(actor);
        return new ArrayList<TeacherDto>(repository.findTeachers(query));
    }

    public ArrayList<DepartmentDto> queryDepartments(UserAccount actor, boolean activeOnly)
            throws SQLException, BusinessException {
        requireActor(actor);
        return new ArrayList<DepartmentDto>(repository.findDepartments(activeOnly));
    }

    public ArrayList<SchoolClassDto> queryClasses(UserAccount actor, AcademicQueryRequest query)
            throws SQLException, BusinessException {
        requireActor(actor);
        List<SchoolClassDto> classes = repository.findClasses(query);
        return new ArrayList<SchoolClassDto>(classes);
    }

    public void saveStudent(UserAccount actor, StudentDto student)
            throws SQLException, BusinessException {
        requireAdmin(actor);
        validateStudent(student);
        validateLinkedUser(student.getUserId(), Role.STUDENT);
        repository.saveStudent(student);
    }

    public void saveTeacher(UserAccount actor, TeacherDto teacher)
            throws SQLException, BusinessException {
        requireAdmin(actor);
        validateTeacher(teacher);
        validateLinkedUser(teacher.getUserId(), Role.TEACHER);
        repository.saveTeacher(teacher);
    }

    public void saveDepartment(UserAccount actor, DepartmentDto department)
            throws SQLException, BusinessException {
        requireAdmin(actor);
        if (department == null || isBlank(department.getDepartmentId())
                || isBlank(department.getDepartmentName())) {
            throw invalid("院系编号和名称不能为空");
        }
        repository.saveDepartment(department);
    }

    public void saveClass(UserAccount actor, SchoolClassDto schoolClass)
            throws SQLException, BusinessException {
        requireAdmin(actor);
        if (schoolClass == null || isBlank(schoolClass.getClassId())
                || isBlank(schoolClass.getClassName())
                || isBlank(schoolClass.getDepartmentId())) {
            throw invalid("班级编号、名称和院系不能为空");
        }
        validateYear(schoolClass.getGradeYear(), "年级");
        if (!repository.departmentExists(schoolClass.getDepartmentId())) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "所属院系不存在");
        }
        repository.saveClass(schoolClass);
    }

    public void deleteStudent(UserAccount actor, String studentId)
            throws SQLException, BusinessException {
        requireAdmin(actor);
        requireId(studentId);
        if (!repository.deleteStudent(studentId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "学生记录不存在");
        }
    }

    public void deleteTeacher(UserAccount actor, String teacherId)
            throws SQLException, BusinessException {
        requireAdmin(actor);
        requireId(teacherId);
        if (!repository.deleteTeacher(teacherId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "教师记录不存在");
        }
    }

    public void deleteDepartment(UserAccount actor, String departmentId)
            throws SQLException, BusinessException {
        requireAdmin(actor);
        requireId(departmentId);
        if (repository.departmentIsReferenced(departmentId)) {
            throw new BusinessException(ResponseCode.CONFLICT, "院系仍被班级或人员引用，请先停用");
        }
        if (!repository.deleteDepartment(departmentId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "院系记录不存在");
        }
    }

    public void deleteClass(UserAccount actor, String classId)
            throws SQLException, BusinessException {
        requireAdmin(actor);
        requireId(classId);
        if (repository.classIsReferenced(classId)) {
            throw new BusinessException(ResponseCode.CONFLICT, "班级仍有学生，请先停用");
        }
        if (!repository.deleteClass(classId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "班级记录不存在");
        }
    }

    private void validateStudent(StudentDto student) throws SQLException, BusinessException {
        if (student == null || isBlank(student.getStudentId()) || isBlank(student.getFullName())
                || isBlank(student.getGender()) || isBlank(student.getDepartmentId())
                || isBlank(student.getClassId()) || isBlank(student.getStatus())) {
            throw invalid("学号、姓名、性别、院系、班级和学籍状态不能为空");
        }
        validateYear(student.getEnrollmentYear(), "入学年份");
        if (!STUDENT_STATUSES.contains(student.getStatus().trim())) {
            throw invalid("学籍状态必须为在读、休学、毕业或退学");
        }
        if (!isBlank(student.getBirthDate())) {
            try {
                LocalDate.parse(student.getBirthDate().trim());
            } catch (DateTimeParseException e) {
                throw invalid("出生日期必须使用 yyyy-MM-dd 格式");
            }
        }
        validateEmail(student.getEmail());
        if (!repository.departmentExists(student.getDepartmentId())) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "所属院系不存在");
        }
        if (!repository.classExists(student.getClassId())) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "所属班级不存在");
        }
        if (!repository.classBelongsToDepartment(
                student.getClassId(), student.getDepartmentId())) {
            throw new BusinessException(ResponseCode.CONFLICT, "班级与院系不一致");
        }
    }

    private void validateTeacher(TeacherDto teacher) throws SQLException, BusinessException {
        if (teacher == null || isBlank(teacher.getTeacherId())
                || isBlank(teacher.getFullName()) || isBlank(teacher.getDepartmentId())) {
            throw invalid("工号、姓名和院系不能为空");
        }
        validateEmail(teacher.getEmail());
        if (!repository.departmentExists(teacher.getDepartmentId())) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "所属院系不存在");
        }
    }

    private void validateLinkedUser(String userId, Role expectedRole)
            throws SQLException, BusinessException {
        if (isBlank(userId)) {
            return;
        }
        UserAccount account = userRepository.findById(userId.trim());
        if (account == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "关联登录账号不存在");
        }
        if (account.getRole() != expectedRole) {
            throw new BusinessException(ResponseCode.CONFLICT, "关联账号角色与学籍类型不一致");
        }
    }

    private void requireActor(UserAccount actor) throws BusinessException {
        if (actor == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "请先登录");
        }
    }

    private void requireStaff(UserAccount actor) throws BusinessException {
        requireActor(actor);
        if (effectiveRole(actor) == SubSystemRole.STUDENT) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "学生无权查询教师信息");
        }
    }

    private void requireAdmin(UserAccount actor) throws BusinessException {
        requireActor(actor);
        if (effectiveRole(actor) != SubSystemRole.ADMIN) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "仅管理员可以修改学籍信息");
        }
    }

    /**
     * Resolves the three-tier role the actor is granted within the academic
     * (student) sub-system, so scoped authority (an administrator granted only
     * the library sub-system becomes a teacher here) is honoured consistently
     * with the user module.
     */
    private SubSystemRole effectiveRole(UserAccount actor) {
        return SubSystems.effectiveRole(actor.getRole(), actor.getAdminScopes(), SubSystem.STUDENT);
    }

    private void validateEmail(String email) throws BusinessException {
        if (!isBlank(email) && (!email.contains("@") || email.startsWith("@")
                || email.endsWith("@"))) {
            throw invalid("邮箱格式不正确");
        }
    }

    private void validateYear(int year, String fieldName) throws BusinessException {
        if (year < 1900 || year > 2100) {
            throw invalid(fieldName + "必须在 1900 到 2100 之间");
        }
    }

    private void requireId(String id) throws BusinessException {
        if (isBlank(id)) {
            throw invalid("记录编号不能为空");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ResponseCode.INVALID_REQUEST, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

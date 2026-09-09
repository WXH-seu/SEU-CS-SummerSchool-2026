package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.AcademicQueryRequest;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.SchoolClassDto;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.StudentImportFailure;
import edu.seu.vcampus.common.dto.StudentImportRequest;
import edu.seu.vcampus.common.dto.StudentImportResponse;
import edu.seu.vcampus.common.dto.StudentProfileUpdateRequest;
import edu.seu.vcampus.common.dto.TeacherDto;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.server.dao.AcademicRepository;
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
    private static final Set<String> GENDERS = new HashSet<String>(
            Arrays.asList("男", "女", "其他"));

    private final AcademicRepository repository;
    private final UserRepository userRepository;

    public AcademicService(AcademicRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public ArrayList<StudentDto> queryStudents(String actorUserId, SubSystemRole actorRole,
                                                AcademicQueryRequest query)
            throws SQLException, BusinessException {
        requireActor(actorUserId, actorRole);
        if (actorRole == SubSystemRole.STUDENT) {
            StudentDto ownRecord = repository.findStudentByUserId(actorUserId);
            ArrayList<StudentDto> result = new ArrayList<StudentDto>();
            if (ownRecord != null) {
                result.add(ownRecord);
            }
            return result;
        }
        return new ArrayList<StudentDto>(repository.findStudents(query));
    }

    public ArrayList<TeacherDto> queryTeachers(String actorUserId, SubSystemRole actorRole,
                                                AcademicQueryRequest query)
            throws SQLException, BusinessException {
        requireStaff(actorUserId, actorRole);
        return new ArrayList<TeacherDto>(repository.findTeachers(query));
    }

    public ArrayList<DepartmentDto> queryDepartments(String actorUserId, SubSystemRole actorRole,
                                                      boolean activeOnly)
            throws SQLException, BusinessException {
        return queryDepartments(actorUserId, actorRole,
                new AcademicQueryRequest(null, null, null, activeOnly));
    }

    public ArrayList<DepartmentDto> queryDepartments(String actorUserId, SubSystemRole actorRole,
                                                      AcademicQueryRequest query)
            throws SQLException, BusinessException {
        requireActor(actorUserId, actorRole);
        return new ArrayList<DepartmentDto>(repository.findDepartments(query));
    }

    public ArrayList<SchoolClassDto> queryClasses(String actorUserId, SubSystemRole actorRole,
                                                  AcademicQueryRequest query)
            throws SQLException, BusinessException {
        requireActor(actorUserId, actorRole);
        List<SchoolClassDto> classes = repository.findClasses(query);
        return new ArrayList<SchoolClassDto>(classes);
    }

    public synchronized void saveStudent(String actorUserId, SubSystemRole actorRole,
                                         StudentDto student)
            throws SQLException, BusinessException {
        requireAdmin(actorUserId, actorRole);
        StudentDto prepared = assignClassWhenNeeded(student);
        validateStudent(prepared);
        validateLinkedUser(prepared.getUserId());
        repository.saveStudent(prepared);
    }

    public synchronized StudentImportResponse importStudents(
            String actorUserId, SubSystemRole actorRole, StudentImportRequest request)
            throws BusinessException {
        requireAdmin(actorUserId, actorRole);
        if (request == null || request.getStudents().isEmpty()) {
            throw invalid("导入文件中没有学生记录");
        }
        int imported = 0;
        List<StudentImportFailure> failures = new ArrayList<StudentImportFailure>();
        Set<String> batchIds = new HashSet<String>();
        List<StudentDto> students = request.getStudents();
        for (int index = 0; index < students.size(); index++) {
            StudentDto student = students.get(index);
            String studentId = student == null ? null : student.getStudentId();
            try {
                if (isBlank(studentId) || !batchIds.add(studentId.trim())) {
                    throw invalid("学号为空或在本次文件中重复");
                }
                if (repository.studentExists(studentId.trim())) {
                    throw new BusinessException(ResponseCode.CONFLICT, "学号已存在");
                }
                StudentDto prepared = assignClassWhenNeeded(student);
                validateStudent(prepared);
                validateLinkedUser(prepared.getUserId());
                repository.saveStudent(prepared);
                imported++;
            } catch (BusinessException e) {
                failures.add(new StudentImportFailure(
                        index + 1, studentId, e.getMessage()));
            } catch (SQLException e) {
                failures.add(new StudentImportFailure(
                        index + 1, studentId, "数据库写入失败：" + e.getMessage()));
            }
        }
        return new StudentImportResponse(imported, failures);
    }

    public synchronized StudentDto updateOwnProfile(
            String actorUserId, SubSystemRole actorRole, StudentProfileUpdateRequest profile)
            throws SQLException, BusinessException {
        requireActor(actorUserId, actorRole);
        if (actorRole != SubSystemRole.STUDENT) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "仅学生可以修改本人学籍资料");
        }
        if (profile == null) {
            throw invalid("个人资料不能为空");
        }
        StudentDto current = repository.findStudentByUserId(actorUserId);
        if (current == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "未找到当前账号对应的学籍");
        }
        StudentDto updated = new StudentDto(current.getStudentId(), current.getUserId(),
                current.getFullName(), profile.getGender(), profile.getBirthDate(),
                current.getDepartmentId(), current.getClassId(), current.getEnrollmentYear(),
                current.getStatus(), profile.getPhone(), profile.getEmail());
        validateStudent(updated);
        repository.saveStudent(updated);
        return updated;
    }

    public void saveTeacher(String actorUserId, SubSystemRole actorRole, TeacherDto teacher)
            throws SQLException, BusinessException {
        requireAdmin(actorUserId, actorRole);
        validateTeacher(teacher);
        validateLinkedUser(teacher.getUserId());
        repository.saveTeacher(teacher);
    }

    public void saveDepartment(String actorUserId, SubSystemRole actorRole,
                               DepartmentDto department)
            throws SQLException, BusinessException {
        requireAdmin(actorUserId, actorRole);
        if (department == null || isBlank(department.getDepartmentId())
                || isBlank(department.getDepartmentName())) {
            throw invalid("院系编号和名称不能为空");
        }
        repository.saveDepartment(department);
    }

    public void saveClass(String actorUserId, SubSystemRole actorRole,
                          SchoolClassDto schoolClass)
            throws SQLException, BusinessException {
        requireAdmin(actorUserId, actorRole);
        if (schoolClass == null || isBlank(schoolClass.getClassId())
                || isBlank(schoolClass.getClassName())
                || isBlank(schoolClass.getDepartmentId())) {
            throw invalid("班级编号、名称和院系不能为空");
        }
        validateYear(schoolClass.getGradeYear(), "年级");
        if (schoolClass.getCapacity() <= 0) {
            throw invalid("班级容量必须大于 0");
        }
        if (!repository.departmentExists(schoolClass.getDepartmentId())) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "所属院系不存在");
        }
        repository.saveClass(schoolClass);
    }

    public void deleteStudent(String actorUserId, SubSystemRole actorRole, String studentId)
            throws SQLException, BusinessException {
        requireAdmin(actorUserId, actorRole);
        requireId(studentId);
        if (repository.studentIsReferenced(studentId)) {
            throw new BusinessException(ResponseCode.CONFLICT,
                    "学生仍有选课记录，请先处理关联数据");
        }
        if (!repository.deleteStudent(studentId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "学生记录不存在");
        }
    }

    public void deleteTeacher(String actorUserId, SubSystemRole actorRole, String teacherId)
            throws SQLException, BusinessException {
        requireAdmin(actorUserId, actorRole);
        requireId(teacherId);
        if (repository.teacherIsReferenced(teacherId)) {
            throw new BusinessException(ResponseCode.CONFLICT,
                    "教师仍有授课课程，请先处理关联数据");
        }
        if (!repository.deleteTeacher(teacherId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "教师记录不存在");
        }
    }

    public void deleteDepartment(String actorUserId, SubSystemRole actorRole, String departmentId)
            throws SQLException, BusinessException {
        requireAdmin(actorUserId, actorRole);
        requireId(departmentId);
        if (repository.departmentIsReferenced(departmentId)) {
            throw new BusinessException(ResponseCode.CONFLICT,
                    "院系仍被班级、人员、专业或课程引用，请先停用");
        }
        if (!repository.deleteDepartment(departmentId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "院系记录不存在");
        }
    }

    public void deleteClass(String actorUserId, SubSystemRole actorRole, String classId)
            throws SQLException, BusinessException {
        requireAdmin(actorUserId, actorRole);
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
        if (!GENDERS.contains(student.getGender().trim())) {
            throw invalid("性别必须为男、女或其他");
        }
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

    private void validateLinkedUser(String userId)
            throws SQLException, BusinessException {
        if (isBlank(userId)) {
            return;
        }
        if (userRepository.findById(userId.trim()) == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "关联登录账号不存在");
        }
    }

    private StudentDto assignClassWhenNeeded(StudentDto student)
            throws SQLException, BusinessException {
        if (student == null || !isBlank(student.getClassId())) {
            return student;
        }
        if (isBlank(student.getDepartmentId())) {
            throw invalid("自动分班前必须选择学院");
        }
        validateYear(student.getEnrollmentYear(), "入学年份");
        String classId = repository.findAvailableClassId(
                student.getDepartmentId().trim(), student.getEnrollmentYear());
        if (classId == null) {
            throw new BusinessException(ResponseCode.CONFLICT,
                    "该学院和入学年份没有可用班级，请管理员先新建班级或增加容量");
        }
        return new StudentDto(student.getStudentId(), student.getUserId(),
                student.getFullName(), student.getGender(), student.getBirthDate(),
                student.getDepartmentId(), classId, student.getEnrollmentYear(),
                student.getStatus(), student.getPhone(), student.getEmail());
    }

    private void requireActor(String actorUserId, SubSystemRole actorRole)
            throws BusinessException {
        if (isBlank(actorUserId) || actorRole == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "请先登录");
        }
    }

    private void requireStaff(String actorUserId, SubSystemRole actorRole)
            throws BusinessException {
        requireActor(actorUserId, actorRole);
        if (actorRole == SubSystemRole.STUDENT) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "学生无权查询教师信息");
        }
    }

    private void requireAdmin(String actorUserId, SubSystemRole actorRole)
            throws BusinessException {
        requireActor(actorUserId, actorRole);
        if (actorRole != SubSystemRole.ADMIN) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "仅管理员可以修改学籍信息");
        }
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

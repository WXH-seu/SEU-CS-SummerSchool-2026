package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.CatalogCourseDto;
import edu.seu.vcampus.common.dto.CatalogQueryRequest;
import edu.seu.vcampus.common.dto.MajorDto;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.server.dao.CatalogCourse;
import edu.seu.vcampus.server.dao.CurriculumCatalogRepository;
import edu.seu.vcampus.server.dao.Major;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Read-only business facade for the reviewed SEU curriculum snapshot. */
public final class CurriculumCatalogService {
    private final CurriculumCatalogRepository repository;

    public CurriculumCatalogService(CurriculumCatalogRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository is required");
        }
        this.repository = repository;
    }

    public ArrayList<MajorDto> queryMajors(String actorUserId, SubSystemRole actorRole,
                                           CatalogQueryRequest query)
            throws SQLException, BusinessException {
        requireActor(actorUserId, actorRole);
        String departmentId = query == null ? null : query.getDepartmentId();
        List<Major> source = repository.findMajors(departmentId);
        ArrayList<MajorDto> result = new ArrayList<MajorDto>();
        for (Major major : source) {
            if (query != null && query.isActiveOnly() && !major.isActive()) {
                continue;
            }
            if (!matches(query == null ? null : query.getKeyword(),
                    major.getMajorId(), major.getMajorName())) {
                continue;
            }
            result.add(new MajorDto(major.getMajorId(), major.getDepartmentId(),
                    major.getMajorName(), major.getDegreeType(), major.getDurationYears(),
                    major.getSourceYear(), major.getSourceUrl(), major.isActive()));
        }
        return result;
    }

    public ArrayList<CatalogCourseDto> queryCourses(
            String actorUserId, SubSystemRole actorRole, CatalogQueryRequest query)
            throws SQLException, BusinessException {
        requireActor(actorUserId, actorRole);
        if (query == null || isBlank(query.getMajorId())) {
            throw new BusinessException(ResponseCode.INVALID_REQUEST, "查询培养方案课程时必须填写专业代码");
        }
        List<CatalogCourse> source = repository.findCoursesForMajor(query.getMajorId().trim());
        ArrayList<CatalogCourseDto> result = new ArrayList<CatalogCourseDto>();
        for (CatalogCourse course : source) {
            if (!isBlank(query.getDepartmentId())
                    && !query.getDepartmentId().trim().equalsIgnoreCase(
                    course.getDepartmentId())) {
                continue;
            }
            if (query.isActiveOnly() && !course.isActive()) {
                continue;
            }
            if (!matches(query.getKeyword(), course.getCourseId(), course.getCourseName())) {
                continue;
            }
            result.add(new CatalogCourseDto(course.getCourseId(), course.getDepartmentId(),
                    course.getCourseName(), course.getCredits(), course.getLectureHours(),
                    course.getPracticeHours(), course.getCourseType(),
                    course.getRecommendedYear(), course.getRecommendedSemester(),
                    course.isRequired(), course.getSourceYear(), course.getSourceUrl(),
                    course.isActive()));
        }
        return result;
    }

    private void requireActor(String userId, SubSystemRole role) throws BusinessException {
        if (isBlank(userId) || role == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "请先登录");
        }
    }

    private boolean matches(String keyword, String id, String name) {
        if (isBlank(keyword)) {
            return true;
        }
        String value = keyword.trim().toLowerCase();
        return (id != null && id.toLowerCase().contains(value))
                || (name != null && name.toLowerCase().contains(value));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

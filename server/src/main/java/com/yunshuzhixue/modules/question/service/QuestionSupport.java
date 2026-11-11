package com.yunshuzhixue.modules.question.service;

import com.yunshuzhixue.common.api.FieldViolation;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.web.ClientIps;
import com.yunshuzhixue.modules.audit.api.OperationLogCommand;
import com.yunshuzhixue.modules.audit.api.OperationLogRecorder;
import com.yunshuzhixue.modules.content.api.CourseDirectory;
import com.yunshuzhixue.modules.content.api.CourseDirectory.CourseRef;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 两个题库共用：课程校验、按名称匹配课程、操作日志 */
@Component
public class QuestionSupport {

    private final CourseDirectory courses;
    private final OperationLogRecorder recorder;

    public QuestionSupport(CourseDirectory courses, OperationLogRecorder recorder) {
        this.courses = courses;
        this.recorder = recorder;
    }

    public CourseDirectory courses() {
        return courses;
    }

    CourseRef requireCourse(Long courseId) {
        CourseRef course = courses.find(List.of(courseId)).get(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在",
                    List.of(new FieldViolation("course_id", "所选课程不存在，请刷新后重试")));
        }
        return course;
    }

    void log(CurrentAdmin operator, String module, String action, String targetType, Long targetId, String label) {
        String text = label == null ? null : label.replaceAll("\\s+", " ").strip();
        if (text != null && text.length() > 60) {
            text = text.substring(0, 60) + "…";
        }
        recorder.record(new OperationLogCommand(operator.id(), operator.name(), operator.role(), module, action,
                targetType, targetId, text, ClientIps.current()));
    }

    /**
     * 导入时匹配课程：填数字按课程 ID；否则按名称匹配（忽略空白和大小写），同名课程有多门时要求改填课程 ID。
     */
    final class CourseMatcher {
        private final Map<Long, CourseRef> byId = new HashMap<>();
        private final Map<String, List<CourseRef>> byName = new HashMap<>();

        CourseMatcher() {
            for (CourseRef course : courses.all()) {
                byId.put(course.id(), course);
                byName.computeIfAbsent(key(course.title()), k -> new ArrayList<>()).add(course);
            }
        }

        /** 返回课程；找不到或有歧义时返回 null 并把原因写入 reason[0] */
        CourseRef match(String text, String[] reason) {
            if (text.matches("\\d{1,18}")) {
                CourseRef course = byId.get(Long.parseLong(text));
                if (course == null) {
                    reason[0] = "课程 ID 不存在：" + text;
                }
                return course;
            }
            List<CourseRef> found = byName.getOrDefault(key(text), List.of());
            if (found.isEmpty()) {
                reason[0] = "课程不存在：" + text;
                return null;
            }
            if (found.size() > 1) {
                reason[0] = "有 " + found.size() + " 门同名课程，请改填课程 ID：" + text;
                return null;
            }
            return found.get(0);
        }

        private String key(String name) {
            return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase();
        }
    }

    CourseMatcher matcher() {
        return new CourseMatcher();
    }
}

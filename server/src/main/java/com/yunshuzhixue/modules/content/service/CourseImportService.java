package com.yunshuzhixue.modules.content.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.infra.excel.ExcelSheets;
import com.yunshuzhixue.modules.content.domain.VideoUrls;
import com.yunshuzhixue.modules.content.entity.Course;
import com.yunshuzhixue.modules.content.entity.Subject;
import com.yunshuzhixue.modules.content.mapper.CourseMapper;
import com.yunshuzhixue.modules.content.mapper.SubjectMapper;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.sysconfig.api.VideoPlatformCatalog;
import com.yunshuzhixue.modules.sysconfig.api.VideoPlatformCatalog.PlatformInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 批量导入课程（需求 6.2，A-CNT-12）。
 * <ul>
 *   <li>按第 1 行表头识别列，列顺序不限，允许常见别名（如"来源"即视频平台、"播放链接"即视频链接），其他列忽略；</li>
 *   <li>学科、视频平台按名称匹配，忽略大小写和空白；一格可填多个学科，用逗号或顿号分隔；整格匹配不到时再尝试按下划线拆分；</li>
 *   <li>模板不含排序号：导入的课程按文件顺序排在现有课程之后（最大排序号依次加 1）；</li>
 *   <li>部分行失败不影响成功的行。</li>
 * </ul>
 */
@Service
public class CourseImportService {

    /** 导入模板的表头，也是各列的标准名称 */
    public static final List<String> HEADERS =
            List.of("课程名称（必填）", "所属学科（必填）", "视频平台（必填）", "视频链接（必填）", "简介", "时长（分钟）");

    private enum Column {
        TITLE("课程名称", true, "课程名称", "名称", "课程"),
        SUBJECTS("所属学科", true, "所属学科", "学科"),
        PLATFORM("视频平台", true, "视频平台", "平台", "来源"),
        URL("视频链接", true, "视频链接", "播放链接", "链接", "视频地址"),
        INTRO("简介", false, "简介", "课程简介"),
        DURATION("时长", false, "时长", "时长分钟");

        final String label;
        final boolean required;
        final List<String> aliases;

        Column(String label, boolean required, String... aliases) {
            this.label = label;
            this.required = required;
            this.aliases = List.of(aliases);
        }
    }

    public record ImportResult(int successCount, int failureCount, List<Map<String, Object>> failures) {
    }

    private final CourseMapper courseMapper;
    private final SubjectMapper subjectMapper;
    private final VideoPlatformCatalog platforms;
    private final CourseService courseService;

    public CourseImportService(CourseMapper courseMapper, SubjectMapper subjectMapper, VideoPlatformCatalog platforms,
                               CourseService courseService) {
        this.courseMapper = courseMapper;
        this.subjectMapper = subjectMapper;
        this.platforms = platforms;
        this.courseService = courseService;
    }

    @Transactional
    public ImportResult importCourses(CurrentAdmin operator, InputStream in) {
        ExcelSheets.Table table;
        try {
            table = ExcelSheets.readTable(in);
        } catch (ExcelSheets.TooManyRowsException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "单次最多导入 " + ExcelSheets.MAX_IMPORT_ROWS + " 行");
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "无法读取文件，请使用 xlsx 格式的导入模板");
        }
        Map<Column, Integer> columns = locateColumns(table.headers());

        Map<String, Subject> subjects = CourseService.byNameKey(subjectMapper.selectList(null), Subject::getName);
        Map<String, PlatformInfo> platformByName = CourseService.byNameKey(platforms.all(), PlatformInfo::name);
        int nextSort = maxSortNo();

        List<Map<String, Object>> failures = new ArrayList<>();
        int success = 0;
        for (ExcelSheets.SheetRow row : table.rows()) {
            String title = cell(row, columns, Column.TITLE);
            String subjectText = cell(row, columns, Column.SUBJECTS);
            String platformName = cell(row, columns, Column.PLATFORM);
            String url = cell(row, columns, Column.URL);
            String intro = cell(row, columns, Column.INTRO);
            String duration = cell(row, columns, Column.DURATION);
            PlatformInfo platform = platformByName.get(CourseService.nameKey(platformName));
            List<Long> subjectIds = new ArrayList<>();
            String reason = null;
            Integer minutes = null;
            if (title.isEmpty() || subjectText.isEmpty() || platformName.isEmpty() || url.isEmpty()) {
                reason = "必填项为空：" + String.join("、", missing(title, subjectText, platformName, url));
            } else if (title.length() > 100) {
                reason = "课程名称超过 100 个字符";
            } else if ((reason = matchSubjects(subjectText, subjects, subjectIds)) != null) {
                // reason 已给出
            } else if (platform == null) {
                reason = "视频平台不存在：" + platformName;
            } else if (!platform.enabled()) {
                reason = "视频平台已停用：" + platformName;
            } else if (url.length() > 1000) {
                reason = "视频链接超过 1000 个字符";
            } else if (!VideoUrls.belongsTo(url, platform.domains())) {
                reason = "视频链接域名不属于「" + platform.name() + "」";
            } else if (intro.length() > 1000) {
                reason = "简介超过 1000 个字符";
            } else if (!duration.isEmpty()) {
                minutes = parseMinutes(duration);
                if (minutes == null) {
                    reason = "时长需为 0~100000 的整数（分钟）";
                }
            }
            if (reason != null) {
                failures.add(Map.of("row_no", row.rowNo(), "reason", reason));
                continue;
            }
            nextSort += 1;
            Course course = new Course();
            course.setTitle(title);
            course.setVideoPlatformId(platform.id());
            course.setVideoUrl(url);
            course.setIntro(intro.isEmpty() ? null : intro);
            course.setDurationMinutes(minutes);
            course.setSortNo(nextSort);
            course.setStatus(CourseService.ON_SHELF);
            courseMapper.insert(course);
            courseService.saveSubjects(course.getId(), subjectIds);
            success++;
        }
        if (success > 0) {
            courseService.log(operator, "导入课程", null, "成功 " + success + " 门，失败 " + failures.size() + " 行");
        }
        return new ImportResult(success, failures.size(), failures);
    }

    /** 按表头找到各列；缺少必填列时整个文件不能导入 */
    private static Map<Column, Integer> locateColumns(List<String> headers) {
        Map<Column, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String key = headerKey(headers.get(i));
            for (Column column : Column.values()) {
                if (!result.containsKey(column) && column.aliases.stream().anyMatch(a -> headerKey(a).equals(key))) {
                    result.put(column, i);
                    break;
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (Column column : Column.values()) {
            if (column.required && !result.containsKey(column)) {
                missing.add(column.label);
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "第 1 行表头缺少：" + String.join("、", missing) + "，请使用课程导入模板");
        }
        return result;
    }

    /** 表头比较时忽略空白、括号和"必填"标注，如"时长（分钟）"与"时长(分钟)"都识别为时长 */
    private static String headerKey(String header) {
        return header.replaceAll("[\\s（）()\\[\\]【】*＊]", "").replace("必填", "").toLowerCase();
    }

    private static String cell(ExcelSheets.SheetRow row, Map<Column, Integer> columns, Column column) {
        Integer index = columns.get(column);
        return index == null ? "" : row.cell(index);
    }

    /**
     * 解析学科：先整格匹配；再按逗号、顿号拆分；某一项匹配不到且含下划线时，按下划线拆分后全部匹配才算数
     * （避免误拆本身带下划线的学科名）。匹配成功返回 null 并填充 subjectIds，否则返回失败原因。
     */
    static String matchSubjects(String text, Map<String, Subject> subjects, List<Long> subjectIds) {
        Subject whole = subjects.get(CourseService.nameKey(text));
        if (whole != null) {
            subjectIds.add(whole.getId());
            return null;
        }
        Set<Long> ids = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String part : text.split("[,，、;；]")) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            Subject subject = subjects.get(CourseService.nameKey(name));
            if (subject != null) {
                ids.add(subject.getId());
                continue;
            }
            List<Subject> pieces = new ArrayList<>();
            if (name.contains("_")) {
                for (String piece : name.split("_")) {
                    Subject s = subjects.get(CourseService.nameKey(piece));
                    if (s == null) {
                        pieces = null;
                        break;
                    }
                    pieces.add(s);
                }
            }
            if (pieces != null && !pieces.isEmpty()) {
                pieces.forEach(s -> ids.add(s.getId()));
            } else {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            return "学科不存在：" + String.join("、", unknown);
        }
        if (ids.isEmpty()) {
            return "必填项为空：所属学科";
        }
        subjectIds.addAll(ids);
        return null;
    }

    private int maxSortNo() {
        List<Object> max = courseMapper.selectObjs(new QueryWrapper<Course>().select("COALESCE(MAX(sort_no), 0)"));
        return max.isEmpty() || max.get(0) == null ? 0 : ((Number) max.get(0)).intValue();
    }

    private static List<String> missing(String title, String subject, String platform, String url) {
        List<String> names = new ArrayList<>();
        if (title.isEmpty()) {
            names.add("课程名称");
        }
        if (subject.isEmpty()) {
            names.add("所属学科");
        }
        if (platform.isEmpty()) {
            names.add("视频平台");
        }
        if (url.isEmpty()) {
            names.add("视频链接");
        }
        return names;
    }

    private static Integer parseMinutes(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            return value >= 0 && value <= 100_000 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

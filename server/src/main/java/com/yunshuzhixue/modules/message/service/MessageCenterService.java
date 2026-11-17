package com.yunshuzhixue.modules.message.service;

import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 消息中心（M16，S-MSG-01~04）：站内信列表、未读数与已读标记 */
@Service
public class MessageCenterService {

    private final JdbcTemplate jdbc;

    public MessageCenterService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** S-MSG-01：按发送时间倒序，可按已读筛选 */
    public PageResult<MessageItem> list(long studentId, PageQuery page, Boolean isRead) {
        StringBuilder where = new StringBuilder(" WHERE student_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(studentId);
        if (isRead != null) {
            where.append(" AND is_read = ?");
            args.add(isRead ? 1 : 0);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM student_message" + where, Long.class,
                args.toArray());
        List<Object> rowsArgs = new ArrayList<>(args);
        rowsArgs.add(page.pageSize());
        rowsArgs.add(page.offset());
        List<MessageItem> items = jdbc.queryForList("SELECT id, message_type, title, content, business_id,"
                        + " is_read, read_at, created_at FROM student_message" + where
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", rowsArgs.toArray())
                .stream().map(row -> new MessageItem(
                        ((Number) row.get("id")).longValue(), String.valueOf(row.get("message_type")),
                        String.valueOf(row.get("title")), String.valueOf(row.get("content")),
                        row.get("business_id") == null ? null : ((Number) row.get("business_id")).longValue(),
                        flag(row.get("is_read")), toInstant(row.get("read_at")), toInstant(row.get("created_at"))))
                .toList();
        return new PageResult<>(items, total, page.page(), page.pageSize());
    }

    /** S-MSG-02：前端轮询的未读数 */
    public UnreadCount unreadCount(long studentId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_message WHERE student_id = ? AND is_read = 0",
                Long.class, studentId);
        return new UnreadCount(count == null ? 0 : count);
    }

    /** S-MSG-03：标记单条已读，重复调用结果一致 */
    @Transactional
    public void markRead(long studentId, long messageId) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_message WHERE id = ? AND student_id = ?",
                Integer.class, messageId, studentId);
        if (exists == null || exists == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        jdbc.update("UPDATE student_message SET is_read = 1, read_at = COALESCE(read_at, NOW(3))"
                + " WHERE id = ? AND student_id = ?", messageId, studentId);
    }

    /** S-MSG-04：全部已读 */
    @Transactional
    public void readAll(long studentId) {
        jdbc.update("UPDATE student_message SET is_read = 1, read_at = COALESCE(read_at, NOW(3))"
                + " WHERE student_id = ? AND is_read = 0", studentId);
    }

    private static boolean flag(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value instanceof Number n && n.intValue() == 1;
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().toInstant(ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.toInstant(ZoneOffset.UTC);
        }
        return null;
    }

    public record MessageItem(Long id, String messageType, String title, String content, Long businessId,
                              boolean isRead, Instant readAt, Instant createdAt) {
    }

    public record UnreadCount(long unreadCount) {
    }
}

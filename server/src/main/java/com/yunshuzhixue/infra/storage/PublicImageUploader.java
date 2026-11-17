package com.yunshuzhixue.infra.storage;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import java.io.IOException;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 公开图片上传（后台图片、学员头像等共用）。
 *
 * <p>只接受 JPG、PNG，按文件头判断格式，不信任文件名与客户端声明的类型；文件名随机，
 * 存放在 {@code public/{folder}/yyyy-MM/} 下，由对象存储对该前缀开放匿名只读。</p>
 */
@Component
public class PublicImageUploader {

    static final long MAX_BYTES = 2L * 1024 * 1024;

    private final ObjectStorage storage;

    public PublicImageUploader(ObjectStorage storage) {
        this.storage = storage;
    }

    /** folder 只用于归档分类，如 images、avatars */
    public String upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "请上传不超过 2MB 的 JPG 或 PNG 图片");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "无法读取文件");
        }
        String ext = detect(bytes);
        if (ext == null) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "请上传不超过 2MB 的 JPG 或 PNG 图片");
        }
        String key = "public/" + folder + "/" + YearMonth.now(ZoneOffset.UTC) + "/" + UUID.randomUUID() + "." + ext;
        return storage.putPublic(key, bytes, "png".equals(ext) ? "image/png" : "image/jpeg");
    }

    /** 按文件头判断格式 */
    static String detect(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        return null;
    }
}

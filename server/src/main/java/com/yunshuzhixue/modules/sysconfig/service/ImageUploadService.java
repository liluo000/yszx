package com.yunshuzhixue.modules.sysconfig.service;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.infra.storage.ObjectStorage;
import java.io.IOException;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 后台上传图片（客服二维码，A-CFG-18）：JPG、PNG，不超过 2MB，文件名随机 */
@Service
public class ImageUploadService {

    static final long MAX_BYTES = 2L * 1024 * 1024;

    private final ObjectStorage storage;

    public ImageUploadService(ObjectStorage storage) {
        this.storage = storage;
    }

    public String upload(MultipartFile file) {
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
        String key = "public/images/" + YearMonth.now(ZoneOffset.UTC) + "/" + UUID.randomUUID() + "." + ext;
        return storage.putPublic(key, bytes, "png".equals(ext) ? "image/png" : "image/jpeg");
    }

    /** 按文件头判断格式，不信任文件名和客户端声明的类型 */
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

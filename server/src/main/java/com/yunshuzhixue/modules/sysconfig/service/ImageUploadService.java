package com.yunshuzhixue.modules.sysconfig.service;

import com.yunshuzhixue.infra.storage.PublicImageUploader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 后台上传图片（客服二维码，A-CFG-18）：JPG、PNG，不超过 2MB，文件名随机 */
@Service
public class ImageUploadService {

    private final PublicImageUploader uploader;

    public ImageUploadService(PublicImageUploader uploader) {
        this.uploader = uploader;
    }

    public String upload(MultipartFile file) {
        return uploader.upload(file, "images");
    }
}

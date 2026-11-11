package com.yunshuzhixue.infra.storage;

/**
 * 对象存储（线上为云对象存储，本地为 SeaweedFS，都通过 S3 接口访问）。
 * 公开文件放在 public/ 下，存储桶对该前缀开放匿名只读，文件名随机生成。
 */
public interface ObjectStorage {

    /** 上传公开可读的文件，返回访问地址 */
    String putPublic(String key, byte[] content, String contentType);

    /** 删除文件，不存在时忽略 */
    void delete(String key);
}

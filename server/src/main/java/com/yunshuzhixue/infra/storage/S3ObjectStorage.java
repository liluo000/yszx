package com.yunshuzhixue.infra.storage;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@EnableConfigurationProperties(StorageProperties.class)
class S3ObjectStorage implements ObjectStorage {

    private final StorageProperties properties;
    private volatile S3Client client;

    S3ObjectStorage(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String putPublic(String key, byte[] content, String contentType) {
        client().putObject(PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromBytes(content));
        String base = properties.publicBaseUrl();
        return (base.endsWith("/") ? base : base + "/") + key;
    }

    @Override
    public void delete(String key) {
        client().deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }

    private S3Client client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = S3Client.builder()
                            .endpointOverride(URI.create(properties.endpoint()))
                            .region(Region.of(properties.region()))
                            .credentialsProvider(StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                            .serviceConfiguration(S3Configuration.builder()
                                    .pathStyleAccessEnabled(properties.pathStyle()).build())
                            .build();
                }
            }
        }
        return client;
    }
}

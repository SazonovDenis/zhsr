package kz.zhsr.app.service.storage;

import io.minio.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

import java.io.*;

/**
 * Хранилище файлов S3 (MinIO): один бакет s3.bucket, файл - по ключу. Бакет создаёт install/storage-reset. При старте
 * хранилище проверяется: недоступно или бакета нет - приложение не стартует.
 */
@Service
public class FileStorage {

    /**
     * Файл из хранилища: содержимое и тип (Content-Type, с которым файл положен).
     */
    public record StoredFile(InputStream content, String contentType) {
    }

    private final MinioClient client;
    private final String endpoint;
    private final String bucket;

    public FileStorage(
            @Value("${s3.endpoint}") String endpoint,
            @Value("${s3.access-key}") String accessKey,
            @Value("${s3.secret-key}") String secretKey,
            @Value("${s3.bucket}") String bucket
    ) throws Exception {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.endpoint = endpoint;
        this.bucket = bucket;
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            throw new IllegalStateException("В хранилище файлов " + endpoint + " нет бакета " + bucket);
        }
    }

    /**
     * Адрес хранилища - для лога при старте.
     */
    public String endpoint() {
        return endpoint;
    }

    /**
     * Бакет - для лога при старте.
     */
    public String bucket() {
        return bucket;
    }

    /**
     * Положить файл.
     *
     * @param size        размер, байт
     * @param contentType тип файла; null - application/octet-stream
     */
    public void put(String key, InputStream content, long size, String contentType) throws Exception {
        client.putObject(PutObjectArgs.builder()
                                      .bucket(bucket)
                                      .object(key)
                                      .stream(content, size, -1)
                                      .contentType(contentType == null ? "application/octet-stream" : contentType)
                                      .build());
    }

    /**
     * Файл по ключу; поток закрывает вызывающий.
     */
    public StoredFile get(String key) throws Exception {
        GetObjectResponse res = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        return new StoredFile(res, res.headers().get("Content-Type"));
    }

    /**
     * Удалить файл; файла нет - ничего не делает.
     */
    public void remove(String key) throws Exception {
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
    }

}

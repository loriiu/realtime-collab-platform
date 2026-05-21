package com.collab.platform.file.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketLifecycleArgs;
import io.minio.messages.Expiration;
import io.minio.messages.LifecycleConfiguration;
import io.minio.messages.LifecycleRule;
import io.minio.messages.RuleFilter;
import io.minio.messages.Status;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MinIO client configuration with connection pooling, region, and auto-creation
 * of the default bucket with lifecycle rules.
 *
 * <p>Addresses:
 * <ul>
 *   <li>P46: Custom OkHttpClient with 50-connection pool, 30s connect timeout,
 *       60s read/write timeout</li>
 *   <li>P47: Explicit region set to us-east-1</li>
 *   <li>P41: Auto-create bucket on startup if absent</li>
 *   <li>P48: Set 90-day lifecycle expiration rule on the bucket</li>
 * </ul></p>
 */
@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.region:us-east-1}")
    private String region;

    /**
     * Build MinioClient with a custom OkHttpClient connection pool.
     *
     * <p>P46 fix: connection pool of 50, 30s connect timeout, 60s read/write timeout.</p>
     *
     * @return MinioClient bean
     */
    @Bean
    public MinioClient minioClient() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region(region)
                .httpClient(httpClient)
                .build();
    }

    /**
     * P41 + P48: Auto-create bucket on startup if not present, and set
     * a 90-day lifecycle expiration rule.
     */
    @PostConstruct
    public void ensureBucketExists() {
        try {
            MinioClient client = minioClient();

            boolean found = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).region(region).build());
            if (!found) {
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).region(region).build());
                log.info("Created MinIO bucket: {}", bucket);
            }

            // P48: Set 90-day lifecycle expiration
            LifecycleRule rule = new LifecycleRule(
                    Status.ENABLED,
                    null,  // no abort-incomplete-multipart-upload
                    new Expiration((java.time.ZonedDateTime) null, 90, null),
                    new RuleFilter(""),
                    "expire-after-90-days",
                    null,  // noncurrent-version-expiration
                    null,  // noncurrent-version-transition
                    null   // transition
            );

            client.setBucketLifecycle(
                    SetBucketLifecycleArgs.builder()
                            .bucket(bucket)
                            .config(new LifecycleConfiguration(List.of(rule)))
                            .build());
            log.info("Set lifecycle rule (90-day expiration) on bucket: {}", bucket);

        } catch (Exception e) {
            log.error("Failed to ensure MinIO bucket '{}' exists", bucket, e);
        }
    }
}

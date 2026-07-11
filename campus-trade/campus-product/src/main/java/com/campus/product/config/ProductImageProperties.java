package com.campus.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "campus.product-images")
public class ProductImageProperties {
    private String directory = "./data/product-images";
    private long maxFileSize = 8 * 1024 * 1024L;
    private int maxImagesPerUser = 50;
    private long minFreeSpaceBytes = 1024L * 1024 * 1024;
}

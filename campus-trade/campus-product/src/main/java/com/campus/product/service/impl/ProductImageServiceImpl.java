package com.campus.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BizException;
import com.campus.common.result.ResultCode;
import com.campus.product.config.ProductImageProperties;
import com.campus.product.entity.ProductImageEntity;
import com.campus.product.mapper.ProductImageMapper;
import com.campus.product.service.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
public class ProductImageServiceImpl implements ProductImageService {

    private static final String PUBLIC_PREFIX = "/api/product/image/";
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );
    /** 单张送入视觉模型的最大原始字节，超出则截断读取提示由调用方控制张数 */
    private static final int MAX_VISION_BYTES = 2 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 10_000_000L;
    private static final int MAX_IMAGE_DIMENSION = 8192;
    private static final Semaphore IMAGE_DECODE_SLOTS = new Semaphore(2, true);

    private final ProductImageProperties properties;
    private final ProductImageMapper productImageMapper;

    @Override
    @Transactional
    public List<String> store(List<MultipartFile> files, Long uploaderId) {
        if (uploaderId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (files == null || files.isEmpty() || files.size() > 5) {
            throw new BizException(400, "请上传1到5张商品图片");
        }
        Path root = rootDirectory();
        List<Path> storedPaths = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                validate(file);
            }
            Files.createDirectories(root);
            Long currentCount = productImageMapper.selectCount(new LambdaQueryWrapper<ProductImageEntity>()
                    .eq(ProductImageEntity::getUploaderId, uploaderId));
            long existing = currentCount == null ? 0 : currentCount;
            if (existing + files.size() > properties.getMaxImagesPerUser()) {
                throw new BizException(400, "每位用户最多保留" + properties.getMaxImagesPerUser() + "张商品图片");
            }
            long incomingBytes = files.stream().mapToLong(MultipartFile::getSize).sum();
            long usableSpace = Files.getFileStore(root).getUsableSpace();
            if (usableSpace - incomingBytes < properties.getMinFreeSpaceBytes()) {
                throw new BizException(500, "图片存储空间不足，请联系管理员");
            }
            List<String> urls = new ArrayList<>();
            for (MultipartFile file : files) {
                String contentType = normalizeType(file.getContentType());
                String filename = UUID.randomUUID().toString().replace("-", "") + EXTENSIONS.get(contentType);
                Path destination = root.resolve(filename).normalize();
                if (!destination.getParent().equals(root)) {
                    throw new BizException(ResultCode.AI_IMAGE_INVALID);
                }
                try (InputStream input = file.getInputStream()) {
                    Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                storedPaths.add(destination);
                ProductImageEntity meta = new ProductImageEntity();
                meta.setFilename(filename);
                meta.setUploaderId(uploaderId);
                productImageMapper.insert(meta);
                urls.add(PUBLIC_PREFIX + filename);
            }
            return urls;
        } catch (BizException e) {
            cleanupFiles(storedPaths);
            throw e;
        } catch (IOException e) {
            cleanupFiles(storedPaths);
            throw new BizException(500, "商品图片保存失败");
        } catch (RuntimeException e) {
            cleanupFiles(storedPaths);
            throw e;
        }
    }

    @Override
    public Resource load(String filename) {
        Path path = resolveFilename(filename);
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BizException(ResultCode.AI_IMAGE_INVALID);
            }
            return resource;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.AI_IMAGE_INVALID);
        }
    }

    @Override
    public String contentType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        throw new BizException(ResultCode.AI_IMAGE_INVALID);
    }

    @Override
    public List<String> readAsDataUrls(List<String> imageUrls, Long requesterId) {
        if (requesterId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        List<String> result = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            String filename = extractFilename(imageUrl);
            requireOwner(filename, requesterId);
            Path path = resolveFilename(filename);
            try {
                byte[] bytes = Files.readAllBytes(path);
                if (bytes.length > MAX_VISION_BYTES) {
                    // 超大图仍送前缀字节会破坏格式；直接拒绝并提示压缩后重传
                    throw new BizException(400, "单张图片过大，请压缩到2MB以内后再生成草稿");
                }
                result.add("data:" + contentType(filename) + ";base64," + Base64.getEncoder().encodeToString(bytes));
            } catch (BizException e) {
                throw e;
            } catch (IOException e) {
                throw new BizException(ResultCode.AI_IMAGE_INVALID);
            }
        }
        return result;
    }

    @Override
    public void assertOwned(List<String> imageUrls, Long requesterId) {
        if (requesterId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (imageUrls == null) return;
        for (String imageUrl : imageUrls) {
            requireOwner(extractFilename(imageUrl), requesterId);
        }
    }

    @Override
    @Transactional
    public void deleteOwned(String imageUrl, Long uploaderId) {
        String filename = extractFilename(imageUrl);
        requireOwner(filename, uploaderId);
        Path path = resolveFilename(filename);
        productImageMapper.delete(new LambdaQueryWrapper<ProductImageEntity>()
                .eq(ProductImageEntity::getFilename, filename)
                .eq(ProductImageEntity::getUploaderId, uploaderId));
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new BizException(500, "商品图片删除失败");
        }
    }

    private void requireOwner(String filename, Long userId) {
        ProductImageEntity meta = productImageMapper.selectOne(new LambdaQueryWrapper<ProductImageEntity>()
                .eq(ProductImageEntity::getFilename, filename)
                .last("LIMIT 1"));
        if (meta == null || !userId.equals(meta.getUploaderId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    private void validate(MultipartFile file) {
        String contentType = normalizeType(file == null ? null : file.getContentType());
        if (file == null || file.isEmpty() || !ALLOWED_TYPES.contains(contentType)) {
            throw new BizException(400, "仅支持 JPG、PNG、WEBP 商品图片");
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new BizException(400, "单张商品图片不能超过8MB");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (!hasValidSignature(contentType, header)) {
                throw new BizException(400, "图片内容与文件格式不一致");
            }
        } catch (IOException e) {
            throw new BizException(400, "无法读取商品图片");
        }
        validateDecodableImage(file);
    }

    private boolean hasValidSignature(String contentType, byte[] header) {
        if ("image/jpeg".equals(contentType)) {
            return header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                    && (header[2] & 0xff) == 0xff;
        }
        if ("image/png".equals(contentType)) {
            return header.length >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 0x50
                    && header[2] == 0x4e && header[3] == 0x47 && header[4] == 0x0d
                    && header[5] == 0x0a && header[6] == 0x1a && header[7] == 0x0a;
        }
        if ("image/webp".equals(contentType)) {
            return header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
                    && header[3] == 'F' && header[8] == 'W' && header[9] == 'E'
                    && header[10] == 'B' && header[11] == 'P';
        }
        return false;
    }

    private String normalizeType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
    }

    private void validateDecodableImage(MultipartFile file) {
        if (!IMAGE_DECODE_SLOTS.tryAcquire()) {
            throw new BizException(429, "图片处理繁忙，请稍后重试");
        }
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw new BizException(400, "无法解析商品图片");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new BizException(400, "图片内容已损坏或格式不受支持");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION
                        || height > MAX_IMAGE_DIMENSION || pixels > MAX_IMAGE_PIXELS) {
                    throw new BizException(400, "图片尺寸过大");
                }
                if (reader.read(0) == null) {
                    throw new BizException(400, "图片内容已损坏");
                }
            } finally {
                reader.dispose();
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(400, "图片内容已损坏或格式不受支持");
        } finally {
            IMAGE_DECODE_SLOTS.release();
        }
    }

    private String extractFilename(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(PUBLIC_PREFIX)) {
            throw new BizException(400, "AI 草稿只接受刚上传的站内商品图片");
        }
        return imageUrl.substring(PUBLIC_PREFIX.length());
    }

    private Path resolveFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")) {
            throw new BizException(ResultCode.AI_IMAGE_INVALID);
        }
        Path root = rootDirectory();
        Path path = root.resolve(filename).normalize();
        if (!path.getParent().equals(root) || !Files.isRegularFile(path)) {
            throw new BizException(ResultCode.AI_IMAGE_INVALID);
        }
        return path;
    }

    private Path rootDirectory() {
        return Path.of(properties.getDirectory()).toAbsolutePath().normalize();
    }

    private void cleanupFiles(List<Path> paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Best effort; disk monitoring and orphan cleanup remain operational safeguards.
            }
        }
    }
}

package com.campus.product.service;

import com.campus.common.exception.BizException;
import com.campus.product.config.ProductImageProperties;
import com.campus.product.entity.ProductImageEntity;
import com.campus.product.mapper.ProductImageMapper;
import com.campus.product.service.impl.ProductImageServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductImageServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void storesImageAndReadsItForVisionModel() throws Exception {
        ProductImageProperties properties = new ProductImageProperties();
        properties.setDirectory(tempDir.toString());
        ProductImageMapper mapper = mock(ProductImageMapper.class);
        when(mapper.insert(any(ProductImageEntity.class))).thenReturn(1);
        ProductImageEntity owned = new ProductImageEntity();
        owned.setUploaderId(7L);
        when(mapper.selectOne(any())).thenReturn(owned);
        ProductImageService service = new ProductImageServiceImpl(properties, mapper);
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", output);
        byte[] jpeg = output.toByteArray();
        MockMultipartFile image = new MockMultipartFile("files", "book.jpg", "image/jpeg", jpeg);

        List<String> urls = service.store(List.of(image), 7L);
        List<String> dataUrls = service.readAsDataUrls(urls, 7L);

        assertEquals(1, urls.size());
        assertTrue(urls.get(0).startsWith("/api/product/image/"));
        assertTrue(dataUrls.get(0).startsWith("data:image/jpeg;base64,"));
        verify(mapper).insert(any(ProductImageEntity.class));
    }

    @Test
    void rejectsPathTraversal() {
        ProductImageProperties properties = new ProductImageProperties();
        properties.setDirectory(tempDir.toString());
        ProductImageService service = new ProductImageServiceImpl(properties, mock(ProductImageMapper.class));

        assertThrows(BizException.class, () -> service.load("../secret.jpg"));
    }

    @Test
    void rejectsFileWithOnlyImageMagicBytes() {
        ProductImageProperties properties = new ProductImageProperties();
        properties.setDirectory(tempDir.toString());
        ProductImageService service = new ProductImageServiceImpl(properties, mock(ProductImageMapper.class));
        byte[] fake = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};
        MockMultipartFile image = new MockMultipartFile("files", "fake.jpg", "image/jpeg", fake);

        assertThrows(BizException.class, () -> service.store(List.of(image), 7L));
    }

    @Test
    void rejectsForeignImageForDraft() {
        ProductImageProperties properties = new ProductImageProperties();
        properties.setDirectory(tempDir.toString());
        ProductImageMapper mapper = mock(ProductImageMapper.class);
        ProductImageEntity owned = new ProductImageEntity();
        owned.setUploaderId(1L);
        when(mapper.selectOne(any())).thenReturn(owned);
        ProductImageService service = new ProductImageServiceImpl(properties, mapper);

        assertThrows(BizException.class,
                () -> service.assertOwned(List.of("/api/product/image/a.jpg"), 2L));
    }
}

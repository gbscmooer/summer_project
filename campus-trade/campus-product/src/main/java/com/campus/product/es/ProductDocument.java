package com.campus.product.es;

import com.campus.product.entity.Product;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.ZoneId;

/**
 * 商品 ES 文档，索引名 product。
 * createTime 用 epoch 毫秒（Long）存储，便于排序并规避 ES 日期格式映射坑。
 * 中文分词说明：title/description 用默认 standard analyzer（按单字切分，可用但非最佳），
 * 后续可装 IK 插件并改 analyzer = "ik_max_word" 增强。
 */
@Data
@Document(indexName = "product")
public class ProductDocument {

    /** 与 MySQL 主键一致，保证幂等覆盖写 */
    @Id
    private Long id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Keyword)
    private String category;

    /** 首图，精确值不分词 */
    @Field(type = FieldType.Keyword)
    private String cover;

    @Field(type = FieldType.Long)
    private Long sellerId;

    @Field(type = FieldType.Integer)
    private Integer status;

    /** 库存；搜索仅展示 stock>0 的在售商品 */
    @Field(type = FieldType.Integer)
    private Integer stock;

    /** 创建时间，epoch 毫秒 */
    @Field(type = FieldType.Long)
    private Long createTime;

    /** 系统默认时区，与 application.yml 中 serverTimezone=Asia/Shanghai 保持一致 */
    private static final ZoneId ZONE = ZoneId.systemDefault();

    public static ProductDocument from(Product p) {
        ProductDocument doc = new ProductDocument();
        doc.setId(p.getId());
        doc.setTitle(p.getTitle());
        doc.setDescription(p.getDescription());
        doc.setPrice(p.getPrice() != null ? p.getPrice().doubleValue() : null);
        doc.setCategory(p.getCategory());
        doc.setCover(extractCover(p.getImages()));
        doc.setSellerId(p.getSellerId());
        doc.setStatus(p.getStatus());
        doc.setStock(p.getStock());
        if (p.getCreateTime() != null) {
            doc.setCreateTime(p.getCreateTime().atZone(ZONE).toInstant().toEpochMilli());
        }
        return doc;
    }

    /** 取 images（逗号分隔）的首图作为封面 */
    private static String extractCover(String images) {
        if (images == null || images.isBlank()) {
            return null;
        }
        return images.split(",")[0].trim();
    }
}

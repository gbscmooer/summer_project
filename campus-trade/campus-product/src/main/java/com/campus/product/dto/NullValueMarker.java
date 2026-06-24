package com.campus.product.dto;

import lombok.Data;

/**
 * 空值缓存标记。商品不存在时，向 Redis 写入此对象（短 TTL），
 * 后续请求命中标记即可直接判定"不存在"，避免缓存穿透反复打到数据库。
 * 单独建类而非缓存 null，是为了能与"真实未命中（key 不存在）"区分开。
 */
@Data
public class NullValueMarker {
    /** 占位字段，标识这是一个空值缓存 */
    private boolean empty = true;
}

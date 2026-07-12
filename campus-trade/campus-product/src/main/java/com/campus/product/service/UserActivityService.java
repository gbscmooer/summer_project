package com.campus.product.service;

import com.campus.product.dto.UserActivityHeatmapView;

public interface UserActivityService {

    UserActivityHeatmapView getHeatmap(Long userId, String filter);
}

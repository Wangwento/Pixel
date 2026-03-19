package com.wwt.pixel.user.domain;

import lombok.Data;

/**
 * 用户基础公开信息
 */
@Data
public class UserBasicInfo {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
}

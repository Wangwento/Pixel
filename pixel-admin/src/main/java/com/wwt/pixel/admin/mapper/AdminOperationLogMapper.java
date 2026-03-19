package com.wwt.pixel.admin.mapper;

import com.wwt.pixel.admin.domain.AdminOperationLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminOperationLogMapper {

    @Insert("INSERT INTO admin_operation_log (admin_id, admin_name, module, action, target_type, target_id, detail, ip) " +
            "VALUES (#{adminId}, #{adminName}, #{module}, #{action}, #{targetType}, #{targetId}, #{detail}, #{ip})")
    void insert(AdminOperationLog log);
}

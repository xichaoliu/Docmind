package com.docmind.gateway.system.handler;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@MappedTypes(Object.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class PostgresJsonbTypeHandler extends JacksonTypeHandler {

    /** 无参构造：包扫描 / register(Class) 实例化时使用 */
    public PostgresJsonbTypeHandler() {
        super(Object.class);
    }

    /** 单参构造：兼容部分注册路径 */
    public PostgresJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    /** 双参构造：MyBatis-Plus 处理 @TableField(typeHandler=...) 时使用，保留泛型 */
    public PostgresJsonbTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");
        pgObject.setValue(toJson(parameter));
        ps.setObject(i, pgObject);
    }
}
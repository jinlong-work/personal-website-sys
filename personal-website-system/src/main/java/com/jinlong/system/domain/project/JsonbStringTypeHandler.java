package com.jinlong.system.domain.project;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** 使用 PostgreSQL 的 jsonb 参数，避免将 JSON 字符串绑定为 varchar。 */
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {
    @Override public void setNonNullParameter(PreparedStatement statement, int index, String value, JdbcType type) throws SQLException {
        statement.setObject(index, value, Types.OTHER);
    }
    @Override public String getNullableResult(ResultSet result, String name) throws SQLException { return result.getString(name); }
    @Override public String getNullableResult(ResultSet result, int index) throws SQLException { return result.getString(index); }
    @Override public String getNullableResult(CallableStatement statement, int index) throws SQLException { return statement.getString(index); }
}

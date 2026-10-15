# 后端开发约定

用户要求：后续业务中能使用 MyBatis-Plus 的地方优先使用 MyBatis-Plus。

- 单表增删改查优先使用 `BaseMapper`；条件构造优先使用 `LambdaQueryWrapper` / `LambdaUpdateWrapper`，需要通用服务能力时使用 `IService` / `ServiceImpl`。
- 新增或迁移实体时，按真实表结构配置 `@TableName`、`@TableId` 和主键策略，非持久化字段使用 `@TableField(exist = false)`。若依共享实体含角色集合、部门等字段，确认完整映射后再接入通用 CRUD。
- 分页统一使用 `Page` / `IPage` 和已有全局 `MybatisPlusInterceptor`，不手写偏移量及 `LIMIT/OFFSET`，不引入第二套分页插件。接口层保留分页参数校验；现有插件单页上限100条。
- 复杂关联、行锁、PostgreSQL 特有语句可使用自定义 Mapper XML；自定义分页方法接收 `IPage` 参数，仍由插件分页。确有需要的复杂总数查询可通过插件的 `countId` 配置。
- 分页前确认关联基数，避免一对多关联导致记录拆页或总数重复；用户台账当前依赖005迁移的单角色唯一约束。保留服务端权限范围、字段白名单和事务边界。
- 后续增加其他内部拦截器时，分页拦截器放在最后。MyBatis-Plus 相关依赖版本在父工程统一管理。

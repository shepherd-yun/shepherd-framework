# shepherd-spring-boot-starter

后端工程地基：统一响应契约与自动包装、全局异常、TraceId（含 @Async 透传）、CORS、访问日志、防重复提交与限流、MyBatis-Plus 审计填充、JWT 认证链、OpenAPI 自动配置、对象存储（S3 协议 + 阿里云 OSS 原生）。

坐标：`com.shepherd:shepherd-spring-boot-starter:0.0.1`，基础包 `com.shepherd.boot`。

不含用户体系（登录/角色/租户等业务模型留在使用方项目里）；引入后写几行 `shepherd.*` 配置即具备基础能力，直接开发业务逻辑。

## 使用方式

先在仓库根目录安装到本地 Maven 仓库（首次使用，或每次改动 Starter 源码后都需要）：

```bash
mvn -DskipTests install -P home
```

然后在下游模块的 `pom.xml` 中加依赖：

```xml
<dependency>
    <groupId>com.shepherd</groupId>
    <artifactId>shepherd-spring-boot-starter</artifactId>
    <version>0.0.1</version>
</dependency>
```

Starter 内部把重型依赖标记为 `optional=true`，不会传递给下游；按需要的能力自行在下游 pom 中声明：

| 需要的能力 | 下游需自行声明的依赖 |
|---|---|
| Web 契约、统一异常处理、TraceId | `spring-boot-starter-webmvc` |
| JWT 鉴权（登录态、`SecurityFilterChain`） | `spring-boot-starter-security` + `spring-boot-starter-data-redis` + `jjwt-api` / `jjwt-impl` / `jjwt-jackson` |
| MyBatis-Plus 分页插件与审计字段自动填充 | `mybatis-plus-spring-boot4-starter` + `mybatis-plus-jsqlparser` |
| 对象存储（S3 协议：AWS/R2/MinIO/阿里云 OSS 的 S3 兼容端点） | `software.amazon.awssdk:s3` + `software.amazon.awssdk:apache-client` |
| 对象存储（阿里云 OSS 原生 SDK） | `com.aliyun.oss:aliyun-sdk-oss` |
| `@RepeatSubmit` / `@RateLimit` | `spring-boot-starter-aspectj` + `spring-boot-starter-data-redis`（**两者缺任一则注解静默无效**） |
| 单号生成（`SerialNoGenerator`） | `spring-boot-starter-data-redis` |
| OpenAPI（Swagger UI、`Result` schema 修正） | `springdoc-openapi-starter-webmvc-ui` |

版本均已在根 `pom.xml` 的 `dependencyManagement`（含 `software.amazon.awssdk:bom`）中收敛，下游无需再写 `<version>`。

## 配置项

### `shepherd.web.*`（需 `spring-boot-starter-webmvc` 在 classpath）

**所有 `List` 类型的绑定都是整体替换而非追加**——下游一旦在 yaml 中写了某一项，必须把需要保留的默认值一并抄进去（与 `permit-urls` 同理）。

| 属性 | 默认值 | 说明 |
|---|---|---|
| `shepherd.web.wrap-result.enabled` | `true` | controller 返回值自动包装成 `Result`，见下文「自动包装」 |
| `shepherd.web.wrap-result.base-packages` | 空 | 空 = 除硬排除外全部包装；非空则只包装这些包下的 controller |
| `shepherd.web.async-mdc.enabled` | `true` | 把 MDC（含 traceId）透传进 `@Async` 线程池与虚拟线程 |
| `shepherd.web.cors.enabled` | `false` | CORS 必须显式开启（前端 dev 走 vite proxy 同源、生产多为同域部署） |
| `shepherd.web.cors.allowed-origins` | 空 | 具体域名列表；与 `allow-credentials=true` 组合时不得含 `*`，否则**启动 fail-fast** |
| `shepherd.web.cors.allowed-methods` | `GET,POST,PUT,DELETE,PATCH,OPTIONS` | |
| `shepherd.web.cors.allowed-headers` | `*` | |
| `shepherd.web.cors.exposed-headers` | `X-Trace-Id` | |
| `shepherd.web.cors.allow-credentials` | `false` | |
| `shepherd.web.cors.max-age` | `1h` | 预检结果缓存时长 |
| `shepherd.web.access-log.enabled` | `false` | 访问日志：method · uri · 状态码 · 耗时 · 客户端 IP |
| `shepherd.web.access-log.slow-threshold` | `1s` | 超过则 WARN，否则 INFO |
| `shepherd.web.access-log.include-body` | `false` | **开启会把整个请求/响应体缓冲进内存**，大响应与文件下载接口慎开 |
| `shepherd.web.access-log.max-body-length` | `2048` | 同时是请求体缓存上限，超出截断 |
| `shepherd.web.access-log.sensitive-keys` | `password,oldPassword,newPassword,token,accessToken,refreshToken,secret,idCard,phone` | JSON 中按 key 就地替换为 `***` |
| `shepherd.web.access-log.exclude-paths` | `/actuator/**`、`/v3/api-docs/**`、`/swagger-ui/**` | Ant 风格 |

### `shepherd.openapi.*`（需 `springdoc-openapi-starter-webmvc-ui` 在 classpath）

| 属性 | 默认值 | 说明 |
|---|---|---|
| `shepherd.openapi.enabled` | `true` | 关闭则不注册 `OpenAPI` Bean 与 schema 修正 |
| `shepherd.openapi.title` | `API` | |
| `shepherd.openapi.version` | `v1` | |
| `shepherd.openapi.description` | 空 | |

内置 JWT `bearer` securityScheme 与全局 `SecurityRequirement`。下游自己声明 `OpenAPI` Bean 即完全接管。

### `shepherd.security.*`（需 `spring-boot-starter-security` + jjwt 在 classpath）

| 属性 | 默认值 | 说明 |
|---|---|---|
| `shepherd.security.enabled` | `true` | 关闭后不注册 Starter 的 JWT 安全链，也不再要求 Redis；**不等于关闭鉴权**——只要 classpath 上仍有 spring-security，Spring Boot 会回落到其默认的 basic/form 登录链并生成随机密码，若要完全放行所有请求需自行声明 `SecurityFilterChain` Bean |
| `shepherd.security.permit-urls` | `/swagger-ui/**`、`/swagger-ui.html`、`/v3/api-docs/**`、`/actuator/health` | 免认证路径列表。**List 绑定是整体替换，不是追加**——下游一旦在 yaml 中写了这项，必须把需要保留的默认项一并抄进去 |
| `shepherd.security.jwt.secret` | 无默认值 | 缺失或短于 32 字节，启动直接失败（Starter 不再向外分发弱默认密钥） |
| `shepherd.security.jwt.access-ttl-seconds` | `7200` | access token 有效期（秒） |
| `shepherd.security.jwt.refresh-ttl-seconds` | `1209600` | refresh token 有效期（秒） |

### `shepherd.mybatis.*`（需 `mybatis-plus-spring-boot4-starter` 在 classpath）

| 属性 | 默认值 | 说明 |
|---|---|---|
| `shepherd.mybatis.db-type` | `postgre_sql` | 分页插件方言，绑定到 MyBatis-Plus 的 `DbType`（宽松绑定，`postgre_sql`/`postgre-sql`/`POSTGRE_SQL` 均可） |

### `shepherd.storage.*`（需 `shepherd.storage.enabled=true` 才装配）

| 属性 | 默认值 | 说明 |
|---|---|---|
| `shepherd.storage.enabled` | `false` | 显式开启才装配存储 Bean |
| `shepherd.storage.provider` | `s3` | `s3`（S3 协议）\| `aliyun`（阿里云原生 SDK） |
| `shepherd.storage.preset` | `aws` | 仅 `provider=s3` 时生效：`aliyun` \| `r2` \| `minio` \| `aws` |
| `shepherd.storage.endpoint` | 无默认值，`enabled=true` 时必填 | |
| `shepherd.storage.region` | 无 | R2 填 `auto`；AWS 填真实 region |
| `shepherd.storage.bucket` | 无默认值，`enabled=true` 时必填 | |
| `shepherd.storage.access-key` | 无默认值，`enabled=true` 时必填 | |
| `shepherd.storage.secret-key` | 无默认值，`enabled=true` 时必填 | |
| `shepherd.storage.public-base-url` | 无 | 可选 CDN 域名；不填则 `publicUrl()` 回落到预签名 URL |
| `shepherd.storage.presign-default-ttl` | `15m` | |
| `shepherd.storage.path-style-access` / `chunked-encoding` / `checksum-compat-mode` | 无（取 preset 值） | 显式设置时覆盖 preset |

## 单号生成

注入 `SerialNoGenerator` 即可发号，单号形如 `BL202609110001` = 前缀 + 日期段 + 补零序号。
计数器在 Redis，自增与首次设过期在同一段 Lua 脚本内完成。

```java
// 建议存成常量，避免同一单号在多处调用时写出不一致的规则
private static final SerialNoRule ORDER = SerialNoRule.builder()
        .prefix("BL")
        .datePattern("yyyyMMdd")
        .seqLength(4)
        .build();

String no = serialNo.next(ORDER);                 // BL202609110001
List<String> nos = serialNo.nextBatch(ORDER, 3);  // 一次 Redis 往返取 3 个连续单号
```

简写：`SerialNoRule.of("BL", "yyyyMMdd", 4)`；无日期段 `SerialNoRule.of("BL", 4)`。

### 规则字段

| 字段 | 约束 | 说明 |
|---|---|---|
| `prefix` | 非空白 | 单号前缀，也是 Redis key 的一段 |
| `datePattern` | 可为 null；非 null 时必须能格式化 `LocalDateTime` | 日期段格式，**它同时决定序号的重置粒度** |
| `seqLength` | `1~18` | 序号补零位数 |

规则非法一律在构造期抛 `IllegalArgumentException`（程序员错误，不占错误码）。

### 日期段与重置粒度

Redis 计数器 key 就是 `前缀 + 格式化结果`，日期段一变即换 key，序号自然从 1 开始：

| `datePattern` | 单号示例 | 重置粒度 |
|---|---|---|
| `yyyyMMdd` | `BL202609110001` | 每日 |
| `yyMM` | `BL26090001` | 每月 |
| `yyyy` | `BL20260001` | 每年 |
| `null` | `BL0001` | 永不重置（key 常驻，不设过期） |

日期段按 **JVM 系统时区**计算（容器内时区问题请在部署侧设 `TZ`）。计数器 key 的 TTL 由 Starter
自动推导，只会偏长不会偏短——偏短会让 key 中途过期、序号回到 1。

### `shepherd.serial-no.*`

| 属性 | 默认值 | 说明 |
|---|---|---|
| `shepherd.serial-no.enabled` | `true` | 关闭则不装配 `SerialNoGenerator` |
| `shepherd.serial-no.key-prefix` | `shepherd:serialno:` | 多应用共享同一 Redis 时改它做隔离 |

Redis 不在 classpath 或没有 `StringRedisTemplate` Bean 时**静默不装配**；下游自己声明
`SerialNoGenerator` Bean 即完全接管。

> **⚠️ 唯一性前提**：唯一性以 **Redis 持久化**为前提（建议 AOF `everysec`）。Redis 丢掉当天计数器
> （重启无持久化 / `FLUSHDB` / 主从切换丢写）会让序号从 1 重数、发出**重复单号**。
> **业务表必须对单号建唯一索引**——这是最后一道防线。

> **⚠️ 序号连续 ≠ 无空洞**：`nextBatch` 没用完的号、以及「脚本已执行成功但响应丢失」后的重试，
> 都会留下空洞。不要把单号序号当成业务计数。

> **⚠️ 序号位数用尽会让单号变长**：`seqLength=4` 的第 10000 个单号是 `BL2026091110000`（自动加宽，
> 同时打一条 WARN）。唯一性不破、业务不中断，但定长解析单号的下游会不适用——按业务量把
> `seqLength` 给足。

## 错误码约定

编码规则：`<3 位 HTTP 状态><2 位序号>`，共 5 位；成功码恒为 `0`。

**HTTP 响应状态恒为 200**——码里的 `4xx`/`5xx` 只是语义前缀，不是真实响应状态。前端一律读 body 里的 `code`。

### 分段约定

每段内低两位 **`00~49` 归 Starter、`50~99` 归下游业务**。两侧各有一个值域守卫单测（Starter 的 `CommonResultCodeTest` / `StorageErrorCodeTest`，业务侧对称的 `ResultCodeTest`），合起来保证永不撞码。

`40100~40149` 是「凭证失效需登出」区间，前端据此清登录态并跳登录页。业务侧登录流程内的失败（如密码错误）必须落在 `40150+`，否则会被前端误判成登出。

### 码表

| 码 | 含义 | 归属 |
|---|---|---|
| `0` | 成功 | — |
| `40000` | 请求参数错误 | Starter |
| `40001` | 请求体格式错误 | Starter |
| `40002` | 请求参数类型不匹配 | Starter |
| `40003` | 对象存储 key 非法 | Starter |
| `40100` | 未认证或登录已失效 | Starter |
| `40101` | 登录凭证无效 | Starter |
| `40102` | 登录已过期 | Starter |
| `40300` | 无权限访问 | Starter |
| `40400` | 资源不存在 | Starter |
| `40401` | 对象不存在 | Starter |
| `40500` | 请求方法不支持 | Starter |
| `40900` | 请勿重复提交 | Starter |
| `41300` | 上传文件过大 | Starter |
| `42900` | 请求过于频繁 | Starter |
| `50000` | 系统繁忙，请稍后再试 | Starter |
| `50001~50004` | 存储上传 / 下载 / 删除 / 预签名失败 | Starter |
| 各段 `50~99` | 下游业务自定义 | 业务 |

## 自动包装

`shepherd.web.wrap-result.enabled=true`（默认）时，controller 直接返回 VO 即可，`ResultBodyAdvice` 自动套上 `Result` 信封：

```java
@GetMapping
public PageResult<UserVO> page(UserPageQuery query) {
    return userService.page(query);   // 响应仍是 {"code":0,"message":"ok","data":{...},"traceId":"..."}
}
```

放行规则（任一命中即不包装）：

- 返回类型已是 `Result`（`GlobalExceptionHandler` 的返回值走这条，不会二次包装）；
- 返回类型是 `byte[]` / `Resource` / `StreamingResponseBody`（文件下载）；
- 方法或其声明类标了 `@NoResultWrap`（显式逃生口，用于第三方回调、裸 JSON 导出等）；
- 声明类在 `org.springdoc.*` / `org.springframework.boot.actuate.*` 下（硬排除，不可配置——包装后 Swagger UI 与 actuator 端点都会失效）；
- `wrap-result.base-packages` 非空且声明类不在其中。

`String` 返回值会被手工序列化成 JSON 并把 `Content-Type` 改为 `application/json`（否则 `StringHttpMessageConverter` 拿到 `Result` 对象会 `ClassCastException`）。`null` / `void` 包成 `data` 为 `null` 的成功响应。

启用 springdoc 时，`ResultSchemaOperationCustomizer` 会把 2xx 响应的 schema 补回一层 `Result`，避免文档失真。

## 覆盖方式

Starter 的每个 Bean 均挂 `@ConditionalOnMissingBean`：下游声明同类型 Bean 即接管，Starter 自动退让，其余零件（`JwtTokenProvider`、`RestAuthEntryPoint` 等）仍可注入复用。

特别地，`SecurityFilterChain` 是**整体接管**——下游一旦声明自己的 `SecurityFilterChain` Bean，Starter 默认链完全不生效，`permit-urls` 等配置项对下游自定义链不再有任何作用（因为它们本来就只是 Starter 默认链读取的输入）。同理，`shepherd.security.permit-urls` 相对其默认值是**整体替换**，不是追加。

## 对象存储 preset 表

三个布尔开关无人记得住，故用预设承载；`shepherd.storage.path-style-access` / `chunked-encoding` / `checksum-compat-mode` 三项显式设置时覆盖预设。

| preset | path-style-access | chunked-encoding | checksum-compat-mode |
|---|---|---|---|
| `aliyun` | false | **false** | true（WHEN_REQUIRED） |
| `r2` | false | true | true（WHEN_REQUIRED） |
| `minio` | **true** | true | true（WHEN_REQUIRED） |
| `aws` | false | true | false（WHEN_SUPPORTED） |

依据（已核对官方文档与 SDK 源码）：

- **阿里云 OSS 不支持 chunked encoding**，而 AWS SDK 默认开启；官方对 Java SDK 2.x 明确要求 `chunkedEncodingEnabled(false)` + `pathStyleAccessEnabled(false)`。
- AWS SDK 的 `requestChecksumCalculation` / `responseChecksumValidation` 默认 `WHEN_SUPPORTED`，会附加 CRC32 校验头，**这是 R2 / MinIO / OSS 等 S3 兼容服务的经典报错来源**；非 AWS 端点一律降为 `WHEN_REQUIRED`。
- `software.amazon.awssdk:s3` **不传递任何同步 HTTP client**（`apache-client` 与 `url-connection-client` 在其 pom 中均为 test scope），必须显式引入 `apache-client`，否则运行时报 "Unable to load an HTTP implementation"。

`endpoint` 前缀与 `provider` 不匹配时启动即 fail fast：`provider=s3` 访问阿里云需要带 `s3.` 前缀的端点（如 `https://s3.oss-cn-hangzhou.aliyuncs.com`），`provider=aliyun` 原生 SDK 则不带前缀（如 `https://oss-cn-hangzhou.aliyuncs.com`）——填反后报出的是 `SignatureDoesNotMatch` 或连接失败，看不出真因，故启动时拦下。

## 阿里云运维前提（非代码可解决，需使用方知悉）

1. AccessKey 必须来自阿里云 RAM，与 AWS 凭证是完全独立的两套体系。`SignatureDoesNotMatch` 最常见的原因即用错凭证体系。
2. **自 2025-03-20 起，新开通 OSS 服务的账号，其中国内地 Bucket 无法通过默认外网域名调用数据操作类 API，必须绑定自定义域名（CNAME）。** 新开账号需先在控制台配置。
3. 服务器时间与标准时间偏差超过 15 分钟，签名请求会被全部拒绝。

## 已知取舍

- **不写容器化集成测试**：Starter 的 `S3ClientsTest`（5 个用例）断言均为 `isNotNull()`，只验证「`aliyun`/`r2`/`minio`/`aws` 四种 preset 都能成功构建 `S3Client`/`S3Presigner`」这条接线，**不验证真实连通性**（未起 MinIO/真实端点）。不要误以为 S3 实现有集成测试覆盖真实读写。
- **阿里云原生实现（`AliyunObjectStorage`）仅有 Mockito 单测覆盖**，无集成测试——阿里云 OSS 没有本地模拟器，真实凭证 IT 需要使用方按需自行验证。
- backend 保留了 3 个 Testcontainers `*IT`（随本次迁移更新到编译通过），但不纳入本 Starter 的验收门槛，由使用方按需手动 `mvn verify` 运行。
- **存储错误码按 HTTP 语义分散在 `400`/`404`/`500` 三段**，不再是模块连续段。这是有意的取舍：错误码首要服务于「前端该怎么处理」，模块归属由 message + traceId 在日志里追。
- **`ClientIps` 依赖的 `X-Forwarded-For` / `X-Real-IP` 是客户端可伪造的请求头**。没有可信反向代理覆写它们时，`IP` 维度限流只是「防误触」量级的措施，不具备对抗恶意流量的安全强度。
- **Starter 的 `TaskDecorator` 标了 `@ConditionalOnMissingBean(TaskDecorator.class)`**：下游一旦自定义 `TaskDecorator`，Starter 让位，traceId 透传需下游自行实现。这是必须的——Boot 用 `ObjectProvider` 取唯一 `TaskDecorator`，两个并存会被**静默跳过**，透传双双失效且无任何报错。
- **`@RepeatSubmit` / `@RateLimit` 需要 AOP 与 Redis 同时在场**（`spring-boot-starter-aspectj` + `spring-boot-starter-data-redis`）。缺任一则两个 Aspect 静默不装配——它们是可选增强能力，没有 fail-fast 的理由，但后果是注解写了却不生效，需自行确认依赖到位。
- 明确不做：分片上传（由预签名直传绕过）、桶管理、图片处理（厂商特有能力，进 SPI 会撑爆接口）；Starter 不提供上传 REST 接口——鉴权、限流、落库需求各项目不同，那是业务方的事。

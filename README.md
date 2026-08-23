# shepherd-spring-boot-starter

后端工程地基：统一响应契约、全局异常、TraceId、MyBatis-Plus 审计填充、JWT 认证链、对象存储（S3 协议 + 阿里云 OSS 原生）。

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

版本均已在根 `pom.xml` 的 `dependencyManagement`（含 `software.amazon.awssdk:bom`）中收敛，下游无需再写 `<version>`。

## 配置项

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

## 错误码约定

Starter 占用 `1xxxx` 段（其中 `10100~10199` 归对象存储），及两个历史保留码 `20001`（token 无效）、`20002`（token 过期）——这两个码之所以留在 Starter 而不是 `2xxxx`，是因为 `JwtTokenProvider` 直接抛出它们，而前端 `AUTH_ERROR_CODES = [10001, 20001, 20002]` 已硬依赖这些数值作登出判定，数值不可更改。

| 码 | 含义 |
|---|---|
| `0` | 成功 |
| `10000` | 请求参数错误 |
| `10001` | 未认证或登录已失效 |
| `10002` | 无权限访问 |
| `10003` | 资源不存在 |
| `10004` | 系统繁忙 |
| `10100~10199` | 对象存储错误（`10100` key 非法、`10101` 上传失败、`10102` 下载失败、`10103` 删除失败、`10104` 预签名失败、`10105` 对象不存在） |
| `20001` | 登录凭证无效（保留码） |
| `20002` | 登录已过期（保留码） |

下游业务码从 `2xxxx` 起，**须避开 `20001`/`20002` 这两个保留码**。

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
- 明确不做：分片上传（由预签名直传绕过）、桶管理、图片处理（厂商特有能力，进 SPI 会撑爆接口）；Starter 不提供上传 REST 接口——鉴权、限流、落库需求各项目不同，那是业务方的事。

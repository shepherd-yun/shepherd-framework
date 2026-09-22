# shepherd-framework

后端工程地基：统一响应契约与自动包装、全局异常、TraceId（含 @Async 透传）、CORS、访问日志、防重复提交与限流、MyBatis-Plus 审计填充、JWT 认证链、OpenAPI 自动配置、对象存储（S3 协议 + 阿里云 OSS 原生）、单号生成、Excel 导入导出。

**基线要求：Spring Boot 4.0.x / Java 21。** 105 个类是对着 Spring Framework 7 / Jackson 3（`tools.jackson.*`）编译的，低于此基线不保证可用（见下文「方式 B」的版本风险说明）。

多模块 Maven 工程，基础包 `com.shepherd.boot`，版本统一为 `0.2.0-SNAPSHOT`，发布在 [GitHub Packages](https://github.com/shepherd-yun/shepherd-framework/packages)：

| 制品 | 用途 |
|---|---|
| `shepherd-parent` | 业务项目的父 pom（推荐）：版本收敛 + 构建约定 |
| `shepherd-dependencies` | BOM，甲方已有自家 parent 时 import 它 |
| `shepherd-spring-boot-starter-web` | Web 地基（含限流/防重，硬依赖 Redis；**非 optional 硬带 springdoc + Swagger UI**，见下文「覆盖方式」） |
| `shepherd-spring-boot-starter-mybatis` | MyBatis-Plus 地基 |
| `shepherd-spring-boot-starter-security` | JWT 认证链（硬依赖 Redis） |
| `shepherd-spring-boot-starter-oss` | 对象存储（S3 兼容；阿里云原生 SDK 需额外声明一行） |
| `shepherd-spring-boot-starter-excel` | Excel 导入导出 |
| `shepherd-common` / `shepherd-common-mybatis` | 契约与公共类型，通常由 starter 传递带入，不需显式声明；**例外**：甲方用自己的 web 层、完全不接 autoconfigure，但仍想复用 `Result`/`ErrorCode`/`BizException` 这套前后端契约时，单独显式声明 `shepherd-common` |
| `shepherd-spring-boot-autoconfigure` | 自动装配实现，由 starter 带入，不直接引用 |

不含用户体系（登录/角色/租户等业务模型留在使用方项目里）；引入后写几行 `shepherd.*` 配置即具备基础能力，直接开发业务逻辑。

## 使用方式

### 1. 配置 GitHub Packages 凭证

GitHub Packages 的 Maven 仓库**读取也要求认证**（即便包是公开的）。在 `~/.m2/settings.xml` 里加一个 server（token 建议走环境变量，不要写死进文件）：

```xml
<servers>
  <server>
    <id>github</id>
    <username>你的 GitHub 用户名</username>
    <password>${env.GITHUB_TOKEN}</password>
  </server>
</servers>
```

token 需要 `read:packages` 权限（发布方另需 `write:packages`）。用 gh CLI 最省事：

```bash
gh auth refresh -h github.com -s read:packages   # 发布方再加 -s write:packages
export GITHUB_TOKEN=$(gh auth token)
```

### 2. 在下游项目声明仓库与依赖

不管选哪种接入方式，都要先声明仓库（读取 GitHub Packages 同样需要认证，见上一步）：

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/shepherd-yun/shepherd-framework</url>
    <releases><enabled>true</enabled></releases>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>
```

`<repository>` 的 `id` 必须与 settings.xml 里 server 的 `id` 一致（都叫 `github`），否则 Maven 拿不到凭证。

然后二选一：

**方式 A：继承 `shepherd-parent`（推荐）**——版本收敛、编译器/Lombok/MapStruct 注解处理器顺序、source jar 等构建约定一次性拿到：

```xml
<parent>
  <groupId>com.shepherd</groupId>
  <artifactId>shepherd-parent</artifactId>
  <version>0.2.0-SNAPSHOT</version>
  <relativePath/>
</parent>

<dependencies>
  <dependency>
    <groupId>com.shepherd</groupId>
    <artifactId>shepherd-spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>com.shepherd</groupId>
    <artifactId>shepherd-spring-boot-starter-mybatis</artifactId>
  </dependency>
  <dependency>
    <groupId>com.shepherd</groupId>
    <artifactId>shepherd-spring-boot-starter-security</artifactId>
  </dependency>
</dependencies>
```

**方式 B：import `shepherd-dependencies`（甲方已有自家 parent 时）**——只拿版本清单，不改变下游的 parent：

> **版本风险**：`shepherd-dependencies` **不** import `spring-boot-dependencies`。若甲方 parent 钉在 Spring Boot 3.x，甲方自己的 dependencyManagement 会把 starter 传递进来的 `spring-boot-starter-webmvc` 等强降到 3.x——**依赖解析不会报错，编译也可能通过，但运行期会炸 `NoClassDefFoundError` / `NoSuchMethodError`**（本框架是对着 Spring Framework 7 / Jackson 3 编译的）。方式 B 接入前务必确认甲方 parent 的 Spring Boot 基线就是 4.0.x。

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.shepherd</groupId>
      <artifactId>shepherd-dependencies</artifactId>
      <version>0.2.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>com.shepherd</groupId>
    <artifactId>shepherd-spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>com.shepherd</groupId>
    <artifactId>shepherd-spring-boot-starter-mybatis</artifactId>
  </dependency>
  <dependency>
    <groupId>com.shepherd</groupId>
    <artifactId>shepherd-spring-boot-starter-security</artifactId>
  </dependency>
</dependencies>
```

两种方式下，`<dependencies>` 里都**不写任何第三方版本号**——按需要的能力挑选 starter（对象存储用 `shepherd-spring-boot-starter-oss`，Excel 用 `shepherd-spring-boot-starter-excel`），每个 starter 已经把自己需要的重型依赖（含 Redis、jjwt、MyBatis-Plus、AWS SDK、FastExcel 等）作为**非 optional 的普通依赖**传递给下游，版本由 BOM 统一钉住，下游 pom 里唯一出现的 `<version>` 就是自身的版本号与继承/import 的 `0.2.0-SNAPSHOT`。

## 配置项

jar 内带 `META-INF/spring-configuration-metadata.json`（由 `spring-boot-configuration-processor` 编译期生成），下游在 `application.yaml` 里写 `shepherd.*` 时 IDE 有补全、默认值与类型校验，字段上的 javadoc 会作为提示文案显示。

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

### `shepherd.excel.*`（需 `shepherd.excel.enabled=true` 才装配）

| 属性 | 默认值 | 说明 |
|---|---|---|
| `shepherd.excel.enabled` | `false` | 显式开启才装配——会拖进 POI + xmlbeans 约 18 个 jar |
| `shepherd.excel.max-rows` | `100000` | 导入行数硬上限，超出抛 `41301` |
| `shepherd.excel.batch-size` | `500` | 分批回调大小 |
| `shepherd.excel.async-threshold-rows` | `5000` | **仅作建议值暴露**，Starter 自身不据此分叉；同步/异步由业务方显式选 |
| `shepherd.excel.inline-error-limit` | `200` | 错误行超过此数则落文件并截断 `errors` |
| `shepherd.excel.export-key-prefix` | `excel/export/` | 异步导出结果的对象存储路径前缀 |
| `shepherd.excel.error-key-prefix` | `excel/error/` | 错误行 Excel 的路径前缀 |
| `shepherd.excel.template.default-rows` | `500` | 模板下拉生效行数。不对整列加有效性——会明显拖累文件体积与 Excel 打开速度 |
| `shepherd.excel.template.hidden-sheet-name` | `__options` | 超长/含逗号选项的承载 sheet |
| `shepherd.excel.task.ttl` | `24h` | 任务记录在 Redis 的存活时长 |
| `shepherd.excel.task.endpoint-enabled` | `true` | 是否注册内置的任务查询端点 |
| `shepherd.excel.task.endpoint-path` | `/excel/tasks` | 任务查询端点路径 |

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

## Excel 导入导出

> **三条必读限制**
>
> 1. **下拉框是填写体验，不是数据保证。** 用户从别处粘贴值时 Excel 不拦截，
>    因此导入侧的逐行校验一条都不能省。
> 2. **分批入库的事务边界是「每批一个事务」。** Starter 不在回调外层开事务，
>    失败批次不回滚已提交的批次。要求「全量原子导入」的场景不适用本能力。
> 3. **大导出一旦开始 flush 就无法回落成 JSON 错误。** `response.reset()` 只在
>    尚未 flush 时有效——这是流式导出的固有代价。

DTO 直接用 FastExcel 的 `@ExcelProperty`，Starter 不另造一套注解。

同步与异步是两套独立 API，**Starter 不做自动分叉**：自动分叉会让同一接口有时返回
xlsx 流、有时返回 `Result<taskId>` JSON，与「响应恒为 `Result`、HTTP 恒 200」的契约冲突。
`shepherd.excel.async-threshold-rows` 只是给业务方判断用的建议值。

### 同步导出

```java
@GetMapping("/users/export")
public void export(HttpServletResponse response) {
    ExcelResponses.write(response, "用户列表.xlsx", UserExportVo.class, service.listAll());
}

// 分页流式，内存恒定；pageFetcher 入参是 1-based 页码，返回空 List 即终止
@GetMapping("/orders/export")
public void exportOrders(HttpServletResponse response) {
    ExcelResponses.writeStream(response, "订单.xlsx", OrderVo.class,
            pageNo -> service.page(pageNo, 1000));
}
```

导出方法签名是 `void` + `HttpServletResponse`，Spring 会置 `requestHandled=true`，
根本不进入 `ResponseBodyAdvice`——因此**不需要**标 `@NoResultWrap`。
该隐式行为由 `ExcelResponsesWrapBypassTest` 钉死，advice 日后改动会立刻变红。

### 导入模板（运行时下拉）

```java
@GetMapping("/users/import-template")
public void template(HttpServletResponse response) {
    ExcelResponses.writeTemplate(response, "用户导入模板.xlsx", UserImportDto.class,
            ExcelTemplateOptions.builder()
                    .dropdown("deptName", deptService.listNames())   // 按 DTO 字段名，不是列索引
                    .dropdown("status", List.of("启用", "停用"))
                    .rows(500)                                       // 下拉生效行数
                    .build());
}
```

- **按字段名定位**：在 DTO 中间插一列时，列索引写法会静默错位；字段名写错则在生成模板时
  立即抛 `40007`，不会产出一个下拉莫名失效的文件。
- **约束类型自动切换**：选项拼接后 ≤255 字符且不含逗号时走内联约束；否则自动写入隐藏 sheet
  并改用公式约束。这两个坑（超 255 字符 / 选项含逗号）的表现都是「文件能下载，Excel 打开报已损坏
  或选项被拆开」，所以由 Starter 兜住，业务方无感。
- `rows` 未设时回落到 `shepherd.excel.template.default-rows`；超出该行数的行仍可填写，只是没有下拉约束。
  不对整列（1048575 行）加有效性——会明显拖累文件体积与 Excel 打开速度。

### 同步导入

```java
ExcelImportResult<UserImportDto> r =
        excelImporter.read(file, UserImportDto.class, batch -> service.saveBatch(batch));
```

逐行流程：FastExcel 解析成 DTO → jakarta validation 校验 → 通过的进批次缓冲，
满 `batch-size` 触发一次 `Consumer<List<T>>`；不通过的记 `ExcelRowError(rowNum, column, message)`，
**不进批次**。`rowNum` 是 1-based 的 Excel 行号（表头第 1 行，首条数据第 2 行），
直接对应用户在 Excel 里看到的行号。

回调自身抛异常时，**该批全部计入失败并记录原因，解析继续**——单批写库失败不该让用户
丢掉整个文件的解析结果。

```java
record ExcelImportResult<T>(
        long total,                        // 解析到的数据行数（不含表头）
        long succeeded,
        long failed,
        List<ExcelRowError> errors,        // 超过 inline-error-limit 时截断
        boolean errorsTruncated,
        @Nullable String errorFileKey)     // 错误行 Excel 的 object key（同步导入恒为 null）
```

不提供「一行代码把整表读成 List」的便捷方法——那是 OOM 诱饵。

### 异步导入导出

```java
String taskId = asyncExcel.submitExport(ExcelExportRequest.<OrderVo>builder()
        .fileName("订单.xlsx").head(OrderVo.class)
        .pageFetcher(pageNo -> service.page(pageNo, 1000)).build(), currentUserId());

String taskId = asyncExcel.submitImport(file, UserImportDto.class,
        batch -> service.saveBatch(batch), currentUserId());

// 前端轮询：GET /excel/tasks/{taskId} → Result<ExcelTaskVo>
```

`ExcelTaskVo` 含 `status`（`PENDING` / `RUNNING` / `SUCCEEDED` / `FAILED`）、`processed` / `total`
进度，以及完成后的 `downloadUrl`——导出任务指向结果文件，导入任务指向错误行 Excel
（无错误行时为 `null`），形成「下载错误行 → 改完重传」的闭环。
`ownerId` 与 `resultKey` 刻意不在 VO 里：前者泄露他人标识，后者泄露存储布局。

`submitImport` 在返回前就把 `MultipartFile` 的内容读出来——请求结束后临时文件会被清理，
异步线程再读就是空的。

任务查询按 `ownerId`（提交时的 Spring Security principal name）判定归属，
**不匹配时返回 `40402 任务不存在` 而非 403**：403 等于确认「该 id 存在但不属于你」，
给了枚举探测的确认信号。

执行提交给 Boot 的 `applicationTaskExecutor`，其上挂着 `MdcTaskDecorator`，
因此异步任务的日志带得上 traceId。

### 异步的前置与 fail-fast

异步能力以「容器中有 `StringRedisTemplate`」为启用信号——任务态存 Redis，没有 Redis 就谈不上异步。
启用后缺 `ObjectStorage`（承载结果文件）或 `SerialNoGenerator`（发任务号）会在**启动时 fail-fast
并指明缺什么**，不同于 `@RepeatSubmit` 缺依赖时的静默失效。只用同步导入导出则不受影响。

任务记录存 Redis（key `shepherd:excel:task:{id}`，TTL 取 `shepherd.excel.task.ttl`），
到期即清理——Redis 丢数时重导一次即可。要持久化就声明自己的 `ExcelTaskStore` Bean 覆盖默认实现。

内置的任务查询端点是 Starter 首个内置 controller，属基础设施端点、不含业务模型；
设 `shepherd.excel.task.endpoint-enabled=false` 可完全关闭，由下游自行实现。

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
| `40004` | Excel 表头与模板不匹配 | Starter |
| `40005` | 不支持的文件格式（非 xlsx/xls/csv） | Starter |
| `40006` | 文件为空或无数据行 | Starter |
| `40007` | 模板下拉指定的字段名在 DTO 中不存在 | Starter |
| `40100` | 未认证或登录已失效 | Starter |
| `40101` | 登录凭证无效 | Starter |
| `40102` | 登录已过期 | Starter |
| `40300` | 无权限访问 | Starter |
| `40400` | 资源不存在 | Starter |
| `40401` | 对象不存在 | Starter |
| `40402` | 导入/导出任务不存在（归属不匹配复用此码，不返回 403） | Starter |
| `40500` | 请求方法不支持 | Starter |
| `40900` | 请勿重复提交 | Starter |
| `41300` | 上传文件过大 | Starter |
| `41301` | 导入行数超出 `shepherd.excel.max-rows` | Starter |
| `42900` | 请求过于频繁 | Starter |
| `50000` | 系统繁忙，请稍后再试 | Starter |
| `50001~50004` | 存储上传 / 下载 / 删除 / 预签名失败 | Starter |
| `50005` | Excel 解析失败 | Starter |
| `50006` | Excel 生成失败 | Starter |
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

### 排除 springdoc + Swagger UI

`shepherd-spring-boot-starter-web` 把 `springdoc-openapi-starter-webmvc-ui`（含 `org.webjars:swagger-ui`）作为**非 optional** 依赖带入，会进生产 fat jar。生产环境不需要 API 文档时，两件事都要做：

```xml
<dependency>
  <groupId>com.shepherd</groupId>
  <artifactId>shepherd-spring-boot-starter-web</artifactId>
  <exclusions>
    <exclusion>
      <groupId>org.springdoc</groupId>
      <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

```yaml
shepherd:
  security:
    permit-urls: [ ] # 默认放行 /swagger-ui/**、/swagger-ui.html、/v3/api-docs/**；不需要文档时一并清空或改写（List 是整体替换，见上文）
```

只排除依赖不改 `permit-urls`，这三个路径仍在免认证名单里（虽然此时 controller 已经不存在了，实际风险有限，但配置与依赖应当一致）。

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

## 开发与发布

本仓库是 root 聚合 pom + 10 个子模块的多模块工程，以下命令均在仓库根目录执行，覆盖全部模块：

```bash
mvn test                     # 全量单测（215 个 / 44 个测试类），验收门槛
mvn -DskipTests install      # 装进本地仓库 11 个制品（10 子模块 + root 聚合 pom），供本机下游项目联调
mvn deploy -P release        # 发布到 GitHub Packages（附带 sources + javadoc jar）
```

`release` profile 定义在 `shepherd-parent` 里，只对继承它的模块生效；`shepherd-dependencies` 与 root 聚合 pom 不继承 `shepherd-parent`，不触发 javadoc/sources 执行。**实测结论：javadoc 对 `packaging=pom` 的模块（`shepherd-parent` 自身）不报错**——`javadoc:jar` 无源码可处理时静默跳过，`BUILD SUCCESS`，无需为它加 `<skip>` 之类的特殊配置。5 个薄 starter 没有 `src/`，同理静默跳过，不产出 sources/javadoc jar，这也是预期行为而非缺陷。

发布前置：`export GITHUB_TOKEN=$(gh auth token)`，且该 token 含 `write:packages`（`gh auth refresh -h github.com -s write:packages`）。

也可以打 tag 让 CI 发布（`.github/workflows/publish.yml`，用 Actions 内置的 `GITHUB_TOKEN`，无需本地凭证）：

```bash
mvn versions:set -DnewVersion=0.1.0 && mvn versions:commit
git commit -am "release: 0.1.0" && git tag v0.1.0 && git push --follow-tags
```

**GitHub Packages 不允许覆盖同名 release 版本**（SNAPSHOT 可以反复推）。发布正式版后若要改动，必须递增版本号。

本机环境注意（与 yun 项目同）：mise 把全局 `java` 钉在 zulu-8，非交互 shell 里需显式指定
`JAVA_HOME=/Users/yun/.local/share/mise/installs/java/zulu-21.50.19.0 mvn ...`；用本机 Maven 配置的阿里云镜像时带上 `-P home`。

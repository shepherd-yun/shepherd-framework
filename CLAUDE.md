# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目性质

单模块 Spring Boot Starter（`com.shepherd:shepherd-spring-boot-starter`，基础包 `com.shepherd.boot`），**没有可运行的应用**——没有 `@SpringBootApplication`、没有 `application.yaml`，一切验证都靠单测。产物发布到 GitHub Packages 供下游业务项目引用。

面向下游使用者的完整文档（配置项表、错误码表、能力矩阵、运维前提、已知取舍）在 `README.md`，改动行为时**必须同步更新 README 对应小节**。本文件只讲在本仓库里干活需要知道的事。

## 常用命令

```bash
mvn -P home test                          # 全量单测（当前 214 个），唯一验收门槛
mvn -P home test -Dtest=ExcelImporterTest # 单个测试类
mvn -P home test -Dtest='ExcelImporterTest#reads_rows_in_batches_and_reports_success'  # 单个方法
mvn -P home -DskipTests install           # 装进本地仓库，供本机下游项目联调
mvn -P home deploy -P release             # 发布到 GitHub Packages（附 sources + javadoc jar）
```

- `-P home` 是本机 `~/.m2/settings.xml` 里的阿里云公共镜像 profile；不在家庭网络或 CI 上省略它（CI 跑的是 `mvn -B verify`）。
- 本仓库根目录有 `mise.toml` 钉住 `java = zulu-21`，直接 `mvn` 即可，无需再手动导 `JAVA_HOME`（README「开发与发布」小节末尾那条 JAVA_HOME 提示是迁出聚合工程前的遗留说明）。
- 发布前置：`export GITHUB_TOKEN=$(gh auth token)` 且 token 含 `write:packages`。GitHub Packages **不允许覆盖同名 release 版本**，正式版发出去后改动必须递增版本号。打 `v*` tag 推上去会触发 `.github/workflows/publish.yml` 自动发布。

## 技术栈里容易踩错的几点

- **Spring Boot 4 / Spring Framework 7**，不是 3.x。Web starter 叫 `spring-boot-starter-webmvc`（不是 `-web`），测试用 `spring-boot-starter-webmvc-test`。
- **Jackson 3**：包名是 `tools.jackson.*`，类型是 `JsonMapper` 而不是 `ObjectMapper`；定制序列化走 `JsonMapperBuilderCustomizer`（见 `ShepherdWebAutoConfiguration.shepherdLongToStringCustomizer`，Long 全部序列化成字符串避免 JS 精度丢失）。
- Redis 自动配置的类名在 Boot 4 里换了包，`@AutoConfiguration` 里用字符串形式 `afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"` 引用，别改成类引用（那个类不在本模块的编译期 classpath 上）。
- **JSpecify 空安全**：每个包都有 `package-info.java` 标 `@NullMarked`，即默认不可为 null，可空处显式标 `@Nullable`。**新增包必须补上 `package-info.java`**。
- **Lombok 必须排在 `spring-boot-configuration-processor` 之前**（`pom.xml` 的 `annotationProcessorPaths`）：`@Data` 生成的 getter/setter 要先存在，配置元数据处理器才扫得到属性。

## 依赖策略

除 `spring-boot-autoconfigure` 与 `jspecify` 外，**pom 里所有依赖都是 `optional=true`**，不传递给下游；下游按需自行声明（矩阵见 README「按需补齐能力依赖」）。`dependencyManagement` 同样不随 jar 传递，所以下游要自己管版本——这是有意的设计，改动时别顺手把 optional 去掉。

两处 `commons-logging` 排除（`aliyun-sdk-oss`、`apache-client`）是**版本收敛**而非类冲突，pom 里有注释说明，不要删。

## 自动配置架构

8 个 `@AutoConfiguration` 类全在 `com.shepherd.boot.autoconfigure`，注册在
`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
**新增自动配置类必须手动加进这个文件**，否则永远不生效且没有任何报错。

| 自动配置 | 装配开关 |
|---|---|
| `ShepherdWebAutoConfiguration` | servlet web 环境即生效（Result 包装、全局异常、TraceId、CORS、访问日志、MDC 透传、兜底 `AuditorProvider`） |
| `ShepherdSecurityAutoConfiguration` | classpath 有 security + jjwt 且 `shepherd.security.enabled≠false` |
| `ShepherdMybatisAutoConfiguration` | classpath 有 MyBatis-Plus |
| `ShepherdOpenApiAutoConfiguration` | classpath 有 springdoc |
| `ShepherdRateLimitAutoConfiguration` | classpath 有 aspectj + redis **且容器里有 `StringRedisTemplate`** |
| `ShepherdStorageAutoConfiguration` | `shepherd.storage.enabled=true` |
| `ShepherdSerialNoAutoConfiguration` | 有 `StringRedisTemplate`（缺则静默不装配） |
| `ShepherdExcelAutoConfiguration` | `shepherd.excel.enabled=true`（异步层再要求有 `StringRedisTemplate`） |

### 缺依赖时的三种处置——选哪种是本仓库最重要的设计判断

改动装配逻辑时按这个标准走，别凭感觉：

1. **fail-fast（启动抛异常）**——缺失会造成安全语义失效或运行期才炸的费解报错时用。做法是声明一个 `@ConditionalOnMissingBean(X.class)` 的「兜底 Bean 方法」，方法体直接 `throw new IllegalStateException(...)`，异常文案要列出可操作的几条出路。范例：`ShepherdSecurityAutoConfiguration.missingLoginTokenStore()`（缺 Redis 时不做 no-op 降级，因为那会让登出与踢人静默失效）、`ShepherdStorageAutoConfiguration.missingObjectStorage()`、`ShepherdExcelAutoConfiguration` 异步层缺 `ObjectStorage` / `SerialNoGenerator`。
2. **静默不装配**——纯可选增强，缺了只是功能没有。范例：`@RepeatSubmit` / `@RateLimit` 缺 AOP 或 Redis、`SerialNoGenerator` 缺 Redis。代价是「注解写了不生效」，所以 README 必须写明前提。
3. **让位（`@ConditionalOnMissingBean`）**——下游声明同类型 Bean 即接管。**Starter 的每个 Bean 都必须挂这个。** 注意 `SecurityFilterChain` 是整体接管：下游一旦自己声明，`shepherd.security.permit-urls` 等配置对它完全无效。

两个特别注意点：

- `MdcTaskDecorator` 必须标 `@ConditionalOnMissingBean(TaskDecorator.class)`（按类型而非按方法）：Boot 用 `ObjectProvider` 取唯一 `TaskDecorator`，容器里存在两个会被**静默跳过**，traceId 透传双双失效且无任何报错。
- 嵌套 `@Configuration` 类在配置解析阶段先于外层类自身的 `@Bean` 方法被处理，所以「嵌套类提供真实 Bean + 外层类提供 fail-fast 兜底 Bean」这个组合是成立的（`ShepherdStorageAutoConfigurationTest` 已用 `ApplicationContextRunner` 钉死）。

## 响应契约与错误码

- `Result<T>`（`code` / `message` / `data` / `traceId`）是唯一响应信封，`traceId` 从 MDC 自取。
- `ResultBodyAdvice` 自动包装 controller 返回值，放行规则（已是 `Result`、`byte[]`/`Resource`/`StreamingResponseBody`、`@NoResultWrap`、`org.springdoc.*` 与 actuator 硬排除、`base-packages` 未命中）见 README「自动包装」。改动放行规则要同时看 `ResultBodyAdviceTest` 与 `ExcelResponsesWrapBypassTest`——后者专门钉死「`void` + `HttpServletResponse` 的导出方法不进 advice」这条隐式行为。
- **HTTP 状态恒为 200**，错误语义全在 body 的 `code` 里。编码规则 `<3 位 HTTP 状态><2 位序号>`，共 5 位，成功码恒为 `0`。
- **每段内低两位 `00~49` 归 Starter、`50~99` 归下游业务**。`CommonResultCodeTest` / `StorageErrorCodeTest` / `ExcelErrorCodeTest` 是值域守卫单测，新增错误码必须落在 Starter 区间，否则测试变红。
- `40100~40149` 是「凭证失效需登出」区间，前端据此清登录态。别把非登出语义的码放进来。
- 存储/Excel 错误码**按 HTTP 语义分散**在 `400`/`404`/`500` 三段，不是模块连续段——这是有意取舍（错误码首要服务于「前端该怎么处理」），新增时照此归段。

## 测试约定

- JUnit 5 + AssertJ + Mockito；自动配置类一律用 `ApplicationContextRunner` / `WebApplicationContextRunner`，断言 `hasSingleBean` / `doesNotHaveBean` / `getFailure()`，不起真实容器。
- 测试方法名用 snake_case 描述行为（`starter_decorator_stands_down_for_a_downstream_one`），读起来是一句话。
- 每个「设计取舍」都应该有一个钉死它的测试，并在测试或生产代码的 javadoc 里写清**为什么**这么做——本仓库的注释风格是解释权衡，不是复述代码。
- **不写容器化集成测试**。`S3ClientsTest` 只验证四种 preset 都能构建出客户端（断言全是 `isNotNull()`），**不验证真实连通性**；`AliyunObjectStorage` 只有 Mockito 单测。别误判 S3/OSS 实现有真实读写覆盖。

## 新增一项能力时的清单

1. 在 `com.shepherd.boot.<能力>` 下建包，补 `package-info.java` 的 `@NullMarked`。
2. 重依赖加进 pom 并标 `optional=true`，版本收进 `dependencyManagement`。
3. 写 `Shepherd<能力>AutoConfiguration`，每个 Bean 挂 `@ConditionalOnMissingBean`，按上面三种处置选一种应对缺依赖。
4. 注册进 `AutoConfiguration.imports`。
5. 配置类用 `@ConfigurationProperties(prefix = "shepherd.<能力>")` + Lombok `@Data`，字段写 javadoc（会进 `spring-configuration-metadata.json`，下游 IDE 里显示为提示文案）。
6. 需要错误码时落在对应 HTTP 段的 `00~49` 内，并扩展值域守卫单测。
7. 写 `ApplicationContextRunner` 装配测试 + 行为单测。
8. 更新 README：能力依赖矩阵、配置项表、错误码表，以及有取舍的话补进「已知取舍」。

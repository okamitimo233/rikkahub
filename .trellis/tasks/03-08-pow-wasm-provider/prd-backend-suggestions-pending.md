# 后端视角建议：DeepSeek PoW WASM 集成

根据 `03-08-pow-wasm-provider` 的 `prd.md` 以及项目现有 backend / ai 架构（`Provider`、`ProviderSetting`、`ProviderManager`、`MessageChunk` 转换链路），从后端视角对实施计划提出以下几点改进建议：

## 1. 拆分 Provider 职责，避免把所有逻辑塞进 `DeepSeekProvider`
**当前计划**：PRD 中倾向于由 `DeepSeekProvider` 同时承担 PoW、建请求、发请求、解析响应等职责。

**建议**：
- `DeepSeekProvider` 只保留“编排”职责，内部依赖几个小组件完成实际工作：
  - `DeepSeekChallengeClient`：请求 `create_pow_challenge`
  - `DeepSeekPowSolver`：加载 WASM 并执行 `wasm_solve`
  - `DeepSeekPowEncoder`：把 challenge + answer 编码成 `x-ds-pow-response`
  - `DeepSeekMessageMapper`：负责 `UIMessage` ↔ DeepSeek 请求/响应格式转换
- 这样可以保持和现有 Provider 的“无状态 + 参数驱动”风格一致，也更方便做单元测试。
- 不建议把 challenge DTO、WASM 内存分配、header 编码细节直接写死在 `Provider` 主文件里，否则后续排查 PoW 失败会很痛苦。

## 2. 明确 PoW Challenge 生命周期，不要把一次性状态持久化到 `ProviderSetting`
**当前计划**：PRD 提到 `DeepSeekProviderSetting`，但还没有区分“持久配置”和“运行时状态”。

**建议**：
- `ProviderSetting` 里只放稳定配置，例如：
  - `token`
  - `baseUrl`
  - `models`
  - 是否启用某些兼容开关
- 不要把以下内容持久化：
  - 最近一次 `challenge`
  - `expire_at`
  - 计算出的 `answer`
  - 本次请求的 `target_path`
- 这些都属于单次请求或短生命周期状态，应该放在 provider 内部的运行时对象里，以免出现“重启 app 后复用过期 challenge”这类隐蔽 bug。

## 3. 为 PoW 失败建立清晰的重试策略，而不是无限回环
**当前计划**：PRD 已说明要先获取 challenge，再计算 PoW，但还没有定义失败后的处理矩阵。

**建议**：
- 至少明确以下几类失败：
  - challenge 接口失败
  - WASM 加载失败
  - `wasm_solve` 计算失败
  - completion 接口返回“PoW 无效 / challenge 过期 / 签名不匹配”
- 对“PoW 无效或已过期”场景，建议只做**一次**自动恢复：
  1. 重新拉取 challenge
  2. 重新计算
  3. 重新发起 completion
- 不要无上限重试，否则在 challenge 协议变更或 token 失效时会造成耗电、卡顿和难以定位的问题。
- 最好在 PRD 里补一个简单错误矩阵（Good / Base / Bad case），后续测试会更容易落地。

## 4. 统一流式与非流式响应转换层，避免重复维护两套解析逻辑
**当前计划**：验收标准要求同时支持流式和非流式，但 PRD 里尚未明确转换层是否复用。

**建议**：
- 把 DeepSeek 响应先转换成项目内部一致的中间表示，再输出为 `MessageChunk` / `UIMessagePart`。
- 尤其要单独定义并测试这些映射规则：
  - 普通文本 → `Text`
  - reasoning / thinking → `Reasoning`
  - 流式增量分片的合并策略
  - finish reason 与最终消息收尾
- 不建议在 `generateText()` 和 `streamText()` 里各写一份“边解析边拼 UIMessage”的逻辑，否则后面一改字段就要改两遍。

## 5. 先定义序列化 DTO 和协议边界，尽量避免 `JsonObject` 到处手搓
**当前计划**：PRD 目前更偏实现流程，还没有把请求/响应契约拆成明确的数据结构。

**建议**：
- 为以下协议定义单独 DTO：
  - `create_pow_challenge` 请求/响应
  - completion 请求体
  - 非流式 completion 响应
  - 流式 chunk 响应
- 对可枚举字段使用明确类型，不要全靠字符串常量散落在各处。
- 这样既符合当前项目 `kotlinx.serialization` 的风格，也能让后续测试直接构造样例 JSON 做回归。

## 6. WASM 模块建议做成“懒加载 + 单例缓存”，不要每次请求都重新初始化
**当前计划**：PRD 计划把 WASM 资源放入 `assets/` 并通过 Chasm 调用。

**建议**：
- 不要每次发消息都重新从 assets 读取并初始化一遍 WASM 模块，这会显著增加首包延迟和内存抖动。
- 更合适的方式是：
  - 首次使用 DeepSeek 时懒加载模块
  - 之后复用已初始化实例或封装好的求解器对象
- 但缓存的应该是“模块/运行时实例”，不是 challenge 结果。缓存边界要分清。
- 同时建议在模块加载失败时抛出可识别的异常，方便和网络错误区分。

## 7. 保持异常传播风格一致，同时补足结构化日志
**当前计划**：PRD 中已有错误处理要求，但还没有和仓库现有 backend 规范对齐。

**建议**：
- 按项目 backend 规范，Provider 层优先采用“异常传播”，不要把所有错误都糊成 `Result.failure` 或返回空字符串。
- 但可以定义少量语义明确的异常类型，例如：
  - `DeepSeekAuthException`
  - `DeepSeekPowException`
  - `DeepSeekProtocolException`
- 日志上建议至少记录这些维度：
  - challenge 获取是否成功
  - PoW 计算耗时
  - completion 首次请求是否命中 PoW 错误
  - 是否触发过一次自动重试
- 注意不要把 token、challenge 原文、完整 header 直接打进日志。

## 8. 把“可测试性”写进设计里，尤其是 PoW 与协议映射部分
**当前计划**：Definition of Done 里提到了单元测试，但还不够具体。

**建议**：
- 至少准备这几类测试：
  - `DeepSeekPowEncoder`：给定 challenge + answer，能生成正确 header
  - `DeepSeekMessageMapper`：`UIMessage` 到 DeepSeek payload 的映射
  - 流式 chunk 合并：reasoning 和 text 的顺序、收尾是否正确
  - 失败恢复：收到 PoW 过期错误后，只重试一次
- 如果 WASM 运行时本身不好在 JVM 单测里直接跑，可以把 `solve()` 抽象成接口，在单测里用 fake solver 替身验证流程，避免测试全被原生依赖拖慢。

## 9. 建议严格收敛首版范围，避免在 PoW 接入阶段顺手扩需求
**当前计划**：PRD 的 Out of Scope 已经排除了登录和多账号，但现有前端建议里已经开始延伸到登录流程。

**建议**：
- 从后端角度，首版最好严格坚持“用户手动提供 token”的方案，先把这几个关键链路做稳：
  - provider 注册
  - challenge 获取
  - PoW 计算
  - completion 请求
  - 响应转换
- 登录、刷新 token、多账号轮换这些都是独立需求，会额外引入认证状态管理、存储策略和更多失败场景，建议等首版 PoW provider 跑通后再单开任务。

## 实施检查单总结
- [ ] 将 `DeepSeekProvider` 拆成编排层 + challenge / solver / encoder / mapper 几个组件。
- [ ] 明确 `ProviderSetting` 与运行时状态的边界，不持久化 challenge 相关字段。
- [ ] 为 PoW 相关失败定义清晰的单次重试策略与错误矩阵。
- [ ] 统一流式 / 非流式响应转换层，减少重复解析逻辑。
- [ ] 为协议定义 DTO，而不是在多处直接手写 JSON 结构。
- [ ] 让 WASM 模块以懒加载 + 单例缓存方式运行，避免重复初始化。
- [ ] 保持异常传播风格一致，并补充不泄密的结构化日志。
- [ ] 优先补齐 mapper / encoder / retry 流程测试。
- [ ] 首版严格收敛到“手动 token + PoW provider 跑通”，不要顺手扩成登录系统。

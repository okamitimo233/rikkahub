# 待合入 PRD 的前端建议：DeepSeek PoW WASM 集成

根据 [03-08-pow-wasm-provider 的 PRD](./prd.md) 以及项目的前端规范 (Jetpack Compose)，从前端视角对实施计划提出以下几点改进建议：

## 1. PoW 计算期间的 UI 状态反馈
**当前计划**：提到“在 API 请求前执行 PoW 计算”，但是 PoW 计算属于 CPU 密集型任务，可能需要耗费一定时间。
**建议**：
- UI 需要优雅地处理这段延迟。当用户发送消息时，在状态切换为“Generating（生成中）”之前，应该有清晰的可视化提示表明应用正在“Verifying（验证中）”或“Computing（计算中）”。
- 对应的 `UIMessage` 状态可能需要增加一个新的枚举值（如 `MessageStatus.Verifying`），专用于这个请求前置校验阶段。这样 UI 就能针对此阶段显示合适的加载状态或骨架屏（Skeleton）。

## 2. 错误处理与超时管理
**当前计划**：在验收标准中提到了错误处理，但在 UI 层面上缺乏具体说明。
**建议**：
- 在移动设备上，PoW 计算可能会失败、超时或占用过多内存。
- UI 应该具备针对 PoW 失败的特定错误状态提示（例如：“验证失败，请重试”），这应该有别于普通的网络错误提示。
- 确保 PoW 计算支持取消（比如将其绑定到 Compose 的 `coroutineScope` 或 ViewModel 的 `viewModelScope` 上）。当用户离开当前页面或取消提示时，WASM 的计算能及时停止，避免不必要的电量损耗。

## 3. 登录 UI 与 Token 管理（状态流转）
**当前计划**：提议在 `ProviderConfigure.kt` 中直接添加邮箱/密码登录界面。
**建议**：
- **安全性**：如果不打算用作持久化存储方案，密码绝不能以明文形式存储，也不应放入普通的 DataStore 中。如果应用只在登录时需要 Token，获取 Token 后 UI 状态里就应立刻清除密码。
- **表单状态**：在 Compose 中编写登录字段时，务必使用前端规范 (`component-guidelines.md`) 要求的 `FormItem`。确保“登录”按钮在请求过程中有视觉反馈（比如加载动画），并在请求尚未返回时设置为禁用状态以防重复点击。
- **Token 过期反馈**：PRD 提到了 `tokenExpireAt`。对于快要过期或已经过期的 Token，UI 应当主动显示警告或提示重新登录，而不是等到下一次聊天请求由于验证失败而中断时才毫无准备地失败。

## 4. 推理过程（Reasoning Content）的展示交互
**当前计划**：提到将 `THINK` 类型的内容转换为 `ReasoningPart`。
**建议**：
- DeepSeek 的“思考”过程可能非常长。为了避免“思考”记录占用大量的历史聊天界面，对于 `ReasoningPart`，UI 应实现一种支持展开/折叠（Expandable/Collapsible）的组件。
- 在“思考”内容以流式传输时，该区块应该默认展开，并且专门用来显示推理过程的输入提示符。而当状态变更为 `FINISHED` 或转为普通 `text` 后，可以提供一个按钮或者将其自动折叠，仅在用户需要时展开查看详细的思考过程。

## 5. 避免阻塞主线程（性能）
**当前计划**：`PoWSolver` 调用 `wasm_solve`。
**建议**：
- **关键问题**：绝对不能在主线程（UI 线程）直接调用 `wasm_solve` 函数。因为它是 CPU 密集型的计算任务，而非阻塞式的 IO，必须严格放入 `Dispatchers.Default` 中执行。这样才能防止在端侧计算期间发生 UI 卡顿或引发 ANR（Application Not Responding，即应用无响应）弹窗。

## 实施检查单总结：
- [ ] 在 UI 数据模型中添加 `MessageStatus.Verifying` 状态。
- [ ] 确保在 `Dispatchers.Default` 线程上执行 WASM 执行操作，避免 UI 假死。
- [ ] 为 WASM 任务实现支持协程取消的处理逻辑。
- [ ] 为 `ReasoningPart` 设计一个可展开折叠的 Compose 组件。
- [ ] 使用 `FormItem` 搭建登录界面，并完善的加载和不可交互状态。
- [ ] 在 UI 层面上优雅地处理 Token 过期的预警和提示。

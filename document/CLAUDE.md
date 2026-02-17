[根目录](../CLAUDE.md) > **document**

# document 模块

## 模块职责

文档解析模块，负责将 PDF、DOCX、PPTX 等文档格式转换为纯文本（Markdown 格式）。在 AI 聊天场景中，用户上传的文档通过本模块解析后以文本形式注入到消息上下文中。

## 入口与启动

本模块为 Android Library 模块（`me.rerere.document`），不包含独立入口。由 `app` 模块的 `DocumentAsPromptTransformer` 调用。

## 架构说明

### 目录结构

```
document/src/main/java/me/rerere/document/
├── PdfParser.kt     # PDF 文档解析器
├── DocxParser.kt    # DOCX 文档解析器
└── PptxParser.kt    # PPTX 文档解析器
```

### 解析器详情

#### PdfParser

- 使用 MuPDF (`com.artifex.mupdf`) 库解析 PDF
- 逐页提取结构化文本
- 输出格式：每页以 `--- Page N:` 分隔

#### DocxParser

- 基于 XML Pull Parser 解析 DOCX（ZIP 内的 `word/document.xml`）
- 支持解析：
  - 段落文本（含粗体 `**`、斜体 `*` Markdown 格式化）
  - 标题层级（`# Heading`）
  - 有序/无序列表（缩进支持）
  - 表格（转换为 Markdown 表格）

#### PptxParser

- 基于 ZipFile + XML Pull Parser 解析 PPTX
- 支持解析：
  - 幻灯片内容（文本框、形状）
  - 项目符号列表
  - 表格
  - 演讲者备注（Speaker Notes）
- 输出格式：`## Slide N` + 内容 + 可选 `### Speaker Notes`

## 对外接口

| 接口 | 说明 |
|------|------|
| `PdfParser.parserPdf(file: File): String` | 解析 PDF 文件为文本 |
| `DocxParser.parse(file: File): String` | 解析 DOCX 文件为 Markdown 文本 |
| `PptxParser.parse(file: File): String` | 解析 PPTX 文件为 Markdown 文本 |

所有解析器均为 `object` 单例，接收 `java.io.File` 参数，返回 `String` 结果。

## 关键依赖

| 依赖 | 用途 |
|------|------|
| MuPDF (via local AAR/JAR) | PDF 解析 |
| Android XML Pull Parser | DOCX/PPTX XML 解析 |
| `java.util.zip` | ZIP 文件处理 |

注意：MuPDF 库通过 `app` 模块的 `fileTree` 方式引入（`libs/` 目录下的 AAR/JAR）。

## 数据模型

内部数据类：
- `ListInfo(level, isNumbered, number)` -- DOCX 列表信息
- `SlideContent(slideNumber, content, notes)` -- PPTX 幻灯片内容

## 测试与质量

- `src/test/java/me/rerere/document/ExampleUnitTest.kt` -- 示例单元测试
- `src/androidTest/` -- 仪器测试

缺口：三个解析器缺少针对实际文档的集成测试。

## 常见问题 (FAQ)

**Q: 为什么 PDF 解析使用 MuPDF 而不是其他库？**
A: MuPDF 提供了高质量的文本提取，支持复杂 PDF 布局的结构化文本输出。

**Q: 大文档的性能如何？**
A: 文档解析在 IO 线程执行，但超大文档可能导致内存压力。建议对大文档采用分页解析策略。

## 相关文件清单

- `document/build.gradle.kts` -- 构建配置
- `document/src/main/java/me/rerere/document/` -- 主要源码（3 个文件）
- `document/src/test/` -- 单元测试
- `document/src/androidTest/` -- 仪器测试

## 变更记录 (Changelog)

- **2026-02-17** -- 初始文档生成

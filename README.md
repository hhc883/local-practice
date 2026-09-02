# 📚 QuestionBankAndroid

一个完全离线的 Android 题库刷题应用，支持多种题型、横向滑动翻题、按题库分类收藏。

**全程本地运行，无需网络权限。** AI 助手(可选)需要联网,仅在用户主动配置 API Key 后启用。

---

## AI 助手(可选)

支持多供应商,通过 OpenAI 兼容协议接入:

| 供应商 | 默认 baseUrl | 预设模型 |
|---|---|---|
| **智谱 AI** | `https://open.bigmodel.cn/api/paas/v4` | glm-4.7-flash / glm-4-flash / glm-z1-air |
| **DeepSeek** | `https://api.deepseek.com` | deepseek-chat / deepseek-reasoner |
| **MiniMax** | `https://api.minimaxi.com/v1` | MiniMax-Text-01 / abab6.5s-chat |
| **自定义** | 用户填 baseUrl + model | — |

智谱启用 `thinking.type=enabled` 思考模式提升 JSON 输出质量;其他 provider 走标准 OpenAI 协议。

任何 OpenAI 兼容 API(Ollama、OpenRouter 等)都能用"自定义"模式接入。

未配置 API Key 时 App 仍完全离线运行,AI 功能仅作为可选增强。

### AI 修复 JSON 反馈

导入失败时,失败项右侧的 **"AI 修复"** 按钮调用 AI 把损坏的 JSON 修好,UI 展示**前后差异**(AI 改了哪几处)+ **修复后 JSON**,用户确认后入库。

**修复成功反馈**(弹修复对话框):
```
修改了 3 处:
● questions[15].answer: 字符串 "p是接口变量..." → 数组 ["p是接口变量..."]
● questions[15].type:  "READ"  →  "BLANK"
● bankName: ""  →  "Java 习题 7"

[ 可滚动的修复后 JSON 预览框 ]
[ 应用 ]  [ 取消 ]
```

**修复失败反馈**(复用 ErrorDetailDialog):
- **HTTP 401**: API Key 鉴权失败,请到 AI 助手设置核对 Key
- **HTTP 429**: API 调用频率超限,请稍后重试
- **Unable to resolve host**: 网络问题:无法连接 AI 服务,检查 WiFi/代理
- **timeout**: 网络超时,检查 WiFi 后重试
- **AI 返回无法解析**: 可能是模型问题,换模型或重试
- **未配置 Key**: 前往 AI 助手设置填写

详见 [AppViewModel + JsonDiff 实现]。

---

## 功能特性

### 支持的题型

| 类型 | JSON type | 说明 | 答题方式 |
|---|---|---|---|
| 单选题 | `SINGLE` | 蓝色标签 | 点选即判 |
| 多选题 | `MULTI` | 绿色标签 | 选完 → 点"确认答案" → 点"下一题" |
| 判断题 | `JUDGE` | 橙色标签 | 点选即判（显示"正确/错误"选项）|
| 挑错题 | `DEBUG` | 紫色标签 | 点选即判 |
| 填空题 | `BLANK` / `READ` | 青色标签 | 文本输入 → 点"确认答案" → 点"下一题" |

### 核心功能

- **JSON 导入** — 通过系统文件选择器（SAF）导入，无需存储权限
- **题库管理** — 题库列表、导入、删除（二次确认）
- **四种刷题模式** — 顺序 / 随机 / 错题本 / 仅收藏
- **点选即判** — 单选/判断/挑错题点选后立即显示对错与解析
- **两步翻页** — 多选/填空题需先确认答案，再点"下一题"翻页
- **横向滑动翻题** — 整页滑动切换题目，可点击顶部标签栏跳转
- **顶部类型标签** — 彩色圆点显示题型分布，点击直达对应题
- **收藏功能** — 每题星标收藏，按题库分组展示，支持左滑删除+撤销
- **错题追踪** — 每次作答记录正确性，可单独复习错题
- **解析展示** — 提交后彩色卡片显示对错与解析

---

## 技术栈

| 组件 | 版本 |
|---|---|
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 1.9.25 |
| Jetpack Compose BOM | 2024.05.00 |
| Room | 2.6.1 |
| Moshi（JSON 解析）| 1.15.1 |
| Navigation Compose | 2.7.7 |
| Lifecycle | 2.7.0 |

**架构：** Clean Architecture + MVVM + 手动 DI（无 Hilt）

---

## 项目结构

```
app/src/main/java/com/local/questionbank/
├── data/
│   ├── database/        # Room 数据库（4张表）
│   │   ├── entity/      # Room 实体
│   │   └── dao/         # Data Access Objects
│   ├── datasource/     # JSON 文件解析（JsonFileParser）
│   ├── mapper/         # Entity ↔ Domain Model 转换
│   └── repository/     # Repository 实现
├── domain/
│   ├── model/          # 领域模型（Question, QuestionBank, Favorite...）
│   └── repository/      # Repository 接口
├── presentation/
│   ├── screen/         # Compose 页面（题库/刷题/收藏/导入）
│   ├── viewmodel/      # ViewModel（状态管理）
│   ├── navigation/      # 路由定义 + NavHost
│   └── theme/           # Material3 主题
└── di/
    └── AppContainer.kt  # 手动依赖容器
```

---

## 题库 JSON 格式

```json
{
  "bankName": "Java 程序设计基础 · 习题 1",
  "desc": "涵盖 Java 语言概论、编译运行环境及源文件结构",
  "questions": [
    {
      "type": "SINGLE",
      "title": "JDK 提供的编译器是?",
      "options": ["java.exe", "javac.exe", "javap.exe", "javaw.exe"],
      "answer": ["1"],
      "analysis": "javac.exe 是编译工具，java.exe 是运行工具。"
    },
    {
      "type": "MULTI",
      "title": "下列哪些是 Java 的数据类型?",
      "options": ["int", "String", "bool", "double"],
      "answer": ["0", "3"],
      "analysis": "Java 有 int、double 等基本类型，String 是类，bool 不是关键字。"
    },
    {
      "type": "JUDGE",
      "title": "Java 语言的主要贡献者是 James Gosling。",
      "options": [],
      "answer": ["0"],
      "analysis": "Java 语言由 James Gosling 领导的 Sun 团队开发。"
    },
    {
      "type": "BLANK",
      "title": "阅读程序:上述源文件的名字是什么?\npublic class Speak {...}",
      "options": [],
      "answer": ["Speak.java"],
      "analysis": "源文件中有 public 类 Speak，故文件名必须为 Speak.java。"
    },
    {
      "type": "DEBUG",
      "title": "Example1.java 的错误是?",
      "options": ["A", "B", "C", "D"],
      "answer": ["3"],
      "analysis": "D 中 system 应大写为 System(Java 关键字)。"
    },
    {
      "type": "PROG",
      "title": "编程题:写出 assert 断言语句。",
      "options": [],
      "answer": ["assert (x >= 0 && x <= 100) : \"非法数据\";"],
      "analysis": "assert 关键字用法。"
    }
  ]
}
```

### 题型 `type` 说明

- `SINGLE` — 单选题，answer 为选项下标（0-based）
- `MULTI` — 多选题，answer 为多个选项下标
- `JUDGE` — 判断题，answer 为 `["0"]`(正确)或 `["1"]`(错误)，options 可为空
- `DEBUG` — 挑错题，同 SINGLE
- `BLANK` / `READ` — 填空题/阅读题,answer 为字符串(如 `"Speak.java"`),options 必须为空数组
- `PROG` — 编程题,answer 为参考代码字符串(`\n` 在 JSON 里写为 `\\n`,会被解析为真实换行),options 必须为空数组
- `UNKNOWN` — 解析兜底:JSON/CSV 出现未识别的 type 时自动归类(灰色标签,按填空题处理)。**不推荐在 JSON 中主动写 UNKNOWN**

**`codeSnippet` 字段**(可选,题面附加代码):等宽字体展示在题干下方,适合"以下程序输出什么"题型

### 题面附加代码（codeSnippet，v1.1+）

任意题型都可以在题干下方附带代码片段，App 用等宽字体 + 浅灰背景渲染：

**JSON 示例**：

```json
{
  "type": "SINGLE",
  "title": "以下程序的输出是?",
  "codeSnippet": "System.out.println(1 + 2 + \"3\");",
  "options": ["6", "123", "33", "出错"],
  "answer": ["1"],
  "analysis": "Java 先算 1+2=3,然后字符串拼接 \"3\" → \"33\""
}
```

**CSV 示例**（新增 `code` 列）：

```csv
type,title,code,answer
SINGLE,以下输出?,int x = 1; System.out.println(x+1);,1
```

**规则**：

- 字段可选；缺失/空列 → 不渲染代码块
- 多行代码：JSON 中写 `\n`；CSV 必须用双引号包裹字段并允许换行
- 含逗号：CSV 必须 `"..."` 双引号包裹
- 不支持语法高亮（等宽字体 + 浅灰底，仅此而已）；如需高亮后期再加

---

## 题库 CSV 格式（v1.0+）

### 字段定义

| 列名 | 必填 | 说明 |
|---|---|---|
| `type` | ✓ | 题型：SINGLE / MULTI / JUDGE / DEBUG / BLANK / READ / PROG |
| `title` | ✓ | 题干（必填非空） |
| `optA` ... `optZ` | 视题型 | 选项列，按顺序读取；遇空列停止 |
| `answer` | ✓ | 答案（下标字符串数组，多选/多空用 `;` 分隔） |
| `analysis` | 选填 | 解析 |
| `bankName` | 选填 | 题库名；首行填写 |
| `desc` | 选填 | 题库描述；首行填写 |

### 最小可工作示例

```csv
type,title,optA,optB,optC,optD,answer,analysis,bankName,desc
SINGLE,JDK 编译器是?,java.exe,javac.exe,javap.exe,javaw.exe,1,javac 是编译工具,Java 基础,入门
MULTI,Java 基本类型,int,String,bool,double,0;3,int 和 double 是基本类型,Java 基础,入门
JUDGE,Java 由 Sun 公司开发,T,F,,,0,正确,Java 基础,入门
BLANK,源文件 Speak.java 的扩展名是?,,,,,java,见题面,Java 基础,入门
DEBUG,Example.java 中错误是?,A,B,C,D,3,D 选项 system 应大写,Java 基础,入门
```

### 各题型 CSV 写法

#### SINGLE / DEBUG(单选题、挑错题)

```csv
type,title,optA,optB,optC,optD,answer
SINGLE,正确答案是 B 的题,选项 A,选项 B,选项 C,选项 D,1
DEBUG,Example.java 错误行是?,A,B,C,D,2
```
- answer 存**单个下标字符串**(如 `"1"` = B)
- options 至少 1 个非空

#### MULTI(多选题)

```csv
type,title,optA,optB,optC,optD,answer
MULTI,Java 哪些是基本类型,int,String,bool,double,0;3
```
- answer 用 **`;` 分隔**多个下标(如 `"0;3"` = A 和 D)

#### JUDGE(判断题)

```csv
type,title,optA,optB,answer
JUDGE,Java 由 Sun 公司开发,T,F,0
JUDGE,Java 是开源软件,T,F,1
```
- options **可为空**(系统自动展示"正确/错误"两按钮)
- 也可显式写 T/F 列
- answer: `0` = 正确 / `1` = 错误(也接受 `T`/`F`)

#### BLANK / READ(填空题/阅读题)

```csv
type,title,optA,optB,answer
BLANK,扩展名是?,,,java
BLANK,1 + 1 = ?,,,2
READ,程序输出是?,,,Hello World
```
- options **全空**
- answer 存原文（程序不会拆分,整段作为答案）

#### PROG(编程题)

```csv
type,title,answer
PROG,写出 assert 断言,assert (x >= 0 && x <= 100) : "非法";
```
- options 全空
- answer 存参考代码
- 转义换行:CSV 中换行用 `\n`(解析时自动转为真实换行)

### 转义与编码

- 文件编码必须为 **UTF-8**(不要 UTF-8 BOM;记事本「另存为」编码选 UTF-8)
- 答案含 `,` 或换行时,字段用双引号包裹,双引号写两次转义 `""`
- 换行转义:`\n` 解析为真实换行

### CSV vs JSON 选择建议

| 场景 | 推荐 |
|---|---|
| 大批量题目（几百道以上）| CSV（Excel 友好） |
| 需要嵌套结构（选项里带图、代码片段）| JSON |
| 与 AI 协作生成题库 | JSON（AI 输出 JSON 更稳） |
| 用户不熟悉技术 | CSV（Excel 直接编辑） |

---

## AI 功能详解

App 内置 3 个 AI 入口,统一走 [OpenAI 兼容协议](https://open.bigmodel.cn/api/paas/v4),可在 AI 助手设置页自由切换供应商与模型。

### 1. AI 出题(刷题页入口)

**入口**:刷题页 TopAppBar 的 `AutoAwesome` 图标(全局可用,任何题库任何刷题模式都能点)。

**行为**:
1. 取当前题目题面 + 选项(若有)调 AI
2. AI 基于同知识点生成 1 道新题
3. 弹出 **BottomSheet** 渲染新题(同 QuestionBody 组件)
4. 用户在 sheet 内作答 → 提交 → 显示对错 + 解析
5. 可选"加入当前题库"入库(若 currentBankId ≠ 0)

**配置要求**:
- 题库模式(当前题库不是全局 `bankId=0`)时,新题可保存回原库
- 全局模式(错题/收藏入口 `bankId=0`)时,新题**不**入库,只展示
- AI Key 缺失 → 弹"未配置 Key"提示,跳到设置页

**典型用例**:刷到 `int n=10; n++; n--;` 的输出题,点 AI 出题,生成 "int n=0; while(n++<5) n+=2;" → 反复练 while 与 n++ 后置

### 2. AI 批量出题(题库页入口) ⭐ v1.2+

**入口**:题库页 → 每张题库卡片右侧的 `AutoAwesome` 紫色按钮。

**行为**:
1. 点击 → 跳到 `BankAiQuizScreen`
2. 依次为该题库的**每道题**调用 AI 出 1 道同知识点新题(后台串行)
3. 用户逐道作答,判分,显示对错 + 解析
4. 全部答完可"加入题库"一次性入库;中途可"丢弃"全部离开

**UI 状态机**:
```
启动
  ├─ 正在生成第 k 题(Loading + 进度条 "3/10")
  ├─ 生成失败(跳过该题,记到 failedItems)
  └─ 全部完成 → 进入作答模式
作答
  ├─ 上一题 / 下一题(可回头改答案)
  ├─ 提交 → 显示正误
  └─ 全部答完 → 底部"加入题库" / "丢弃"
加入
  └─ 逐道 addQuestion 到 DB,失败部分记 Toast
```

**典型用例**:期末复习,一套 30 道题,想巩固薄弱环节 → AI 出 30 道同知识点的变形题,边答边学,全对入新题库(用 AI 训练集)

### 3. AI 修复 JSON(导入页入口)

**入口**:导入失败 → 失败项右侧 **"AI 修复"** 按钮。

**行为**:见上文 `## AI 助手(可选) > ### AI 修复 JSON 反馈` 章节,展示 diff + 错误分类。

### 4. AI 测试连接(设置页入口)

**入口**:AI 助手设置页 → 点 **"测试连接"** 按钮。

**行为**:用当前配置的 provider + key 调一次最小请求(`max_tokens=1` + 一个 1 字符 prompt),返回非空 content 即视为连接成功。失败时按错误分类(401 / 429 / 网络 / 超时 / 解析 / 未配 Key)给具体建议。

**典型用例**:换供应商 / 换模型 / 换 Key 后,点一下"测试连接"立即知道是否配对,不用真的去导入题库或 AI 出题时才发现失败。

### 5. AI 通用设置

**入口**:首页顶部第三张卡 "AI 助手" → 设置页。

**可配置项**:
- **供应商(provider)**:4 个预设(智谱 / DeepSeek / MiniMax / 自定义)
- **模型(model)**:根据所选供应商列出的预设模型,或自定义输入
- **API Key**:所有供应商共用一个 key 字段(密文存 EncryptedSharedPreferences)
- **自定义 baseUrl**:仅 CUSTOM 模式必填,其他用供应商默认 URL

**安全**:Key 用 AndroidX `security-crypto` 的 `EncryptedSharedPreferences` 加密,存于本机 `/data/data/com.local.questionbank/shared_prefs/ai_settings.xml`;**不会上传任何服务器**。

**使用流程**:
1. 首页 → AI 助手 → 设置页
2. 选 "智谱 AI" → 选 "glm-4.7-flash"
3. 粘贴 Key(去 `bigmodel.cn` 控制台拿)
4. 点"测试连接" → 看到 "连接成功"
5. 退出 → 进刷题页 → 点 `AutoAwesome` 图标 → AI 出题
6. 导入失败 → AI 修复 → 看到 diff

### 6. AI 错误处理统一表

| 错误 | 触发场景 | 建议 |
|---|---|---|
| `HTTP 401` | Key 错 / Key 失效 | 去供应商控制台核对 Key |
| `HTTP 429` | 调用太频繁 | 稍等几秒重试 |
| `Unable to resolve host` | DNS 污染 / 离线 | 换 DNS 8.8.8.8 或开代理 |
| `timeout` | 网络慢 | 检查 WiFi 重试 |
| `AI 返回内容无法解析` | 模型乱输出 | 换模型(智谱/DeepSeek)重试 |
| `尚未配置 API Key` | 没填 Key | 去 AI 助手设置填写 |
| `仅等宽字体:AI 助手异常` | 其他错误 | 复制 Snackbar 全文贴给开发者 |

---

## 常见问题 FAQ

### Q1: 导入 JSON 失败,看不到具体原因
**A**: v1.1+ 已加错误详情对话框。失败项右侧点 **"详情"** 按钮,弹窗展示完整错误信息 + 原始字段值 + 排查建议。

### Q2: CSV 文件从 Excel 另存为后导入失败
**A**: 大概率是 **UTF-8 BOM** 问题(记事本/Excel 默认带 BOM)。改用 VSCode / Notepad++ 保存为 UTF-8 无 BOM,或用文本编辑器把文件开头 3 字节 `﻿` 删掉。

### Q3: AI 出题卡在 "正在生成..." 一直不动
**A**: 通常是网络问题或 API Key 错。先去 **AI 助手设置 → 测试连接**,若失败按错误提示处理;成功则重试出题。

### Q4: 想让 AI 出"填空题",但当前题是"单选题"
**A**: 通用做法是保存当前题库 → 用 `AI 批量出题`(题库页入口)对整个题库生成新题;单选题会生成同知识点的填空题。

### Q5: 长题干滚动看不到底部
**A**: v1.1+ 题目渲染区有 `verticalScroll` 包装,支持上下滑动看完整题面;提交后答案区也在同一滚动容器里。

### Q6: 删除题库后能撤销吗?
**A**: v1.1+ 删除题库会弹 Snackbar "已删除\"XX\"",5 秒内点 **"撤销"** 恢复,带全部原题与答案。

### Q7: 题库能否加图片?
**A**: v1.2+ 已支持 `codeSnippet`(代码片段,等宽字体)。**纯图片/公式渲染暂未实现**,如需可加依赖(prism4j for syntax highlight, coil for image)。

### Q8: AI 出题入库后能删除吗?
**A**: 与普通题一样,在错题本 / 收藏夹页面,或重新打开题库后从"刷题"模式翻到该题;**目前没有针对单题删除的快捷入口**。后续版本会加。

### Q9: 7 套题库(XT1-XT7)如何导入?
**A**: 仓库自带 `samples/XT1.json` ~ `samples/XT7.json`,App 启动时**不会自动导入**;用户用"导入题库"按钮多选文件即可批量入库。

### Q10: 数据存在哪里?会丢失吗?
**A**: SQLite 存在本机 `/data/data/com.local.questionbank/databases/question_bank.db`;卸载 App 时随包清空。建议每学期末用系统备份或自行导出 JSON 备份。

---

## 路线图

| 版本 | 状态 | 计划 |
|---|---|---|
| v1.0 | ✅ | 基础题库 + JSON/CSV 导入 + AI 出题(单题) |
| v1.1 | ✅ | AI 修复 + diff 反馈 + 错误详情对话框 + 删除撤销 + 长题干滚动 |
| v1.2 | ✅ | AI 批量出题 + 错题复习强化 + 题库拖拽排序 |
| v1.3 | 计划中 | 图片/PDF 题库 + 公式渲染(KaTeX) + AI 错题讲解 |
| v2.0 | 远期 | 同步题库(可选 Google Drive / WebDAV) + 社区题库市场 |

---

## 构建

```bash
# 首次配置 Gradle Wrapper（如果需要）
gradle wrapper

# 编译 Debug APK
JAVA_HOME=/path/to/jdk17 ./gradlew assembleDebug

# 运行单元测试(22 个用例)
JAVA_HOME=/path/to/jdk17 ./gradlew :app:testDebugUnitTest

# 输出路径
app/build/outputs/apk/debug/app-debug.apk
```

**要求：** JDK 17+

**测试覆盖**:
- `JsonFileParserTest` (3): PROG 题型解析、UTF-8 BOM 容错、未知 type 兜底
- `Migration_2_3_Test` (4): v2→v3 迁移(sortIndex)
- `Migration_3_4_Test` (3): v3→v4 迁移(favorite.sortIndex)
- `ZhipuApiTest` (4): 智谱 API 客户端 / MockWebServer 往返

---

## License

```bash
# 首次配置 Gradle Wrapper（如果需要）
gradle wrapper

# 编译 Debug APK
JAVA_HOME=/path/to/jdk17 ./gradlew assembleDebug

# 输出路径
app/build/outputs/apk/debug/app-debug.apk
```

**要求：** JDK 17+

---

## License

MIT License
"# local-practice" 

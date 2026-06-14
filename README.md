# 📚 QuestionBankAndroid

一个完全离线的 Android 题库刷题应用，支持多种题型、横向滑动翻题、按题库分类收藏。

**全程本地运行，无需网络权限。**

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
      "title": "JDK 提供的编译器是？",
      "options": ["java.exe", "javac.exe", "javap.exe", "javaw.exe"],
      "answer": ["1"],
      "analysis": "javac.exe 是编译工具，java.exe 是运行工具。"
    },
    {
      "type": "MULTI",
      "title": "下列哪些是 Java 的数据类型？",
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
      "title": "阅读程序：上述源文件的名字是什么？\npublic class Speak {...}",
      "options": [],
      "answer": ["Speak.java"],
      "analysis": "源文件中有 public 类 Speak，故文件名必须为 Speak.java。"
    }
  ]
}
```

### 题型 `type` 说明

- `SINGLE` — 单选题，answer 为选项下标（0-based）
- `MULTI` — 多选题，answer 为多个选项下标
- `JUDGE` — 判断题，answer 为 `["0"]`（正确）或 `["1"]`（错误），options 可为空
- `DEBUG` — 挑错题，同 SINGLE
- `BLANK` / `READ` — 填空题，answer 为字符串（如 `"Speak.java"`），options 必须为空数组

---

## 构建

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

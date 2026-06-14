# 项目目录结构（Clean Architecture + MVVM）

> 包名根：`com.local.questionbank`
> 命名约束：模块内只允许 `data / domain / presentation` 三层之间的依赖由外向内（presentation → domain ← data），不反向依赖。

```
app/
├── build.gradle.kts
├── proguard-rules.pro
├── schemas/                              # Room 导出的 schema 目录（KSP 自动生成）
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/local/questionbank/
    │   │   ├── QuestionBankApp.kt        // Application：负责初始化 AppDatabase 单例
    │   │   ├── MainActivity.kt           // 单 Activity + NavHost
    │   │   │
    │   │   ├── data/                     // 数据层：对外暴露领域模型，隐藏 Room / 文件 IO 细节
    │   │   │   ├── database/
    │   │   │   │   ├── AppDatabase.kt
    │   │   │   │   ├── entity/
    │   │   │   │   │   ├── QuestionBankEntity.kt
    │   │   │   │   │   ├── QuestionEntity.kt
    │   │   │   │   │   └── AnswerRecordEntity.kt
    │   │   │   │   └── dao/
    │   │   │   │       ├── QuestionBankDao.kt
    │   │   │   │       ├── QuestionDao.kt
    │   │   │   │       └── AnswerRecordDao.kt
    │   │   │   ├── datasource/
    │   │   │   │   └── JsonFileParser.kt // SAF Uri → 领域模型（Dispatchers.IO）
    │   │   │   ├── mapper/
    │   │   │   │   └── EntityMappers.kt  // Entity <-> 领域模型 双向转换
    │   │   │   └── repository/
    │   │   │       ├── QuestionBankRepositoryImpl.kt
    │   │   │       ├── QuestionRepositoryImpl.kt
    │   │   │       └── AnswerRepositoryImpl.kt
    │   │   │
    │   │   ├── domain/                   // 领域层：业务模型 + 抽象仓库接口
    │   │   │   ├── model/
    │   │   │   │   ├── QuestionBank.kt
    │   │   │   │   ├── Question.kt
    │   │   │   │   ├── QuestionType.kt   // enum: SINGLE / MULTI / JUDGE
    │   │   │   │   └── AnswerRecord.kt
    │   │   │   ├── repository/
    │   │   │   │   ├── QuestionBankRepository.kt
    │   │   │   │   ├── QuestionRepository.kt
    │   │   │   │   └── AnswerRepository.kt
    │   │   │   └── usecase/              // 可选：复杂业务抽 usecase（本项目按需新增）
    │   │   │
    │   │   ├── presentation/             // 表现层：Compose 屏幕 + ViewModel + UiState
    │   │   │   ├── theme/
    │   │   │   │   ├── Color.kt
    │   │   │   │   ├── Theme.kt
    │   │   │   │   └── Type.kt
    │   │   │   ├── viewmodel/
    │   │   │   │   ├── BankListViewModel.kt
    │   │   │   │   ├── QuestionViewModel.kt
    │   │   │   │   └── ImportViewModel.kt
    │   │   │   ├── navigation/
    │   │   │   │   └── AppNavGraph.kt
    │   │   │   └── screen/
    │   │   │       ├── BankListScreen.kt
    │   │   │       ├── ImportScreen.kt
    │   │   │       └── QuestionScreen.kt
    │   │   │
    │   │   └── di/                       // 手写依赖容器（Application 提供 lazy 委托）
    │   │       └── AppContainer.kt
    │   │
    │   └── res/
    │       ├── values/
    │       │   ├── strings.xml
    │       │   └── themes.xml            // Material3 主题（Compose 内仍可用作启动主题）
    │       ├── xml/
    │       │   ├── backup_rules.xml
    │       │   └── data_extraction_rules.xml
    │       └── mipmap-*                  // 启动图标
    │
    └── test/                             // 单元测试（领域层 + 解析器）
        └── java/com/local/questionbank/
            └── data/datasource/JsonFileParserTest.kt
```

## 分层职责矩阵

| 层 | 允许依赖 | 禁止依赖 | 主要产物 |
| --- | --- | --- | --- |
| `data` | `domain` 接口、Room/Moshi/Android SDK | `presentation` | Entity、Dao、AppDatabase、Repository 实现、Mapper |
| `domain` | Kotlin Stdlib、Coroutines | Android SDK、Room、Compose | Model、Repository 接口 |
| `presentation` | `domain` 模型、Compose、Lifecycle | Room、SAF、Moshi | Screen、ViewModel、UiState、Navigation |

## 关键约定

1. **跨层通信只走领域模型** —— Compose 页面不允许直接持有 Entity。
2. **Repository 接口在 `domain` 中定义，实现放在 `data/repository`**，由 `AppContainer` 在 `QuestionBankApp` 构造时单例化。
3. **ViewModel 通过 `ViewModelProvider.Factory` 注入 Repository**（不引入 Hilt），使用 `viewModel { ... }` 形式的工厂委托。
4. **所有 IO/DB 调用必须 `withContext(Dispatchers.IO)`**（在 Repository 实现内统一处理，ViewModel 只面向 `suspend` / `Flow`）。
5. **文件选择全部走 SAF**：调用方在 `ActivityResultContracts.OpenDocument()` 中请求 `application/json` 即可拿到临时授权的 `Uri`。


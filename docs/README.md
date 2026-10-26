# TestPilot 使用与运维文档

## 项目简介

TestPilot 用 AI 浏览器 Agent 按用户提示词探索指定网站，记录网络请求；用户将接口编排成一个或多个业务场景，再由大模型为每个场景生成自动化用例。用例可在用例库中查看、编辑，并同步到配置的自动化平台。

当前主流程：创建自动化任务 → AI 浏览器录制 → 选择接口并保存场景编排 → AI 生成用例 → 用户保存用例。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.3.5、Spring AI、Spring Data JPA |
| 模型 | DeepSeek Chat API + OpenAI 兼容 Embedding API |
| 浏览器 | Playwright MCP，通过可插拔 Provider 接口调用 |
| 前端 | Vue 3、Vite |
| 数据库 | PostgreSQL + pgvector |

编排规则会将名称、场景说明和有序接口链路写入 pgvector。推荐时用当前场景说明及录制接口链路生成查询向量，按余弦相似度检索；高于配置阈值的规则才会显示为推荐，默认阈值为 `0.70`。`vectorized` 标记记录规则是否已建立向量索引，旧规则会在首次参与推荐时补建索引。

## 启动

需要 JDK 17+、Maven 3.8+、Node 18+、PostgreSQL，以及可用的 Playwright MCP 运行环境。

1. 配置 PostgreSQL 连接（默认值见 `src/main/resources/application.yml`）。
2. 配置 Chat 和 Embedding API。Embedding 端点与密钥和 DeepSeek Chat 分开配置，Embedding API 需要兼容 OpenAI embeddings 接口：

   ```bash
   export DEEPSEEK_API_KEY=你的DeepSeek密钥
   export EMBEDDING_API_KEY=你的Embedding服务密钥
   export EMBEDDING_BASE_URL=https://你的OpenAI兼容Embedding服务地址
   export EMBEDDING_MODEL=你的Embedding模型名
   export EMBEDDING_DIMENSIONS=1536
   export RULE_RECOMMENDATION_SIMILARITY_THRESHOLD=0.70
   ```

3. 启动后端：`mvn spring-boot:run`
4. 启动前端：`cd frontend && npm install && npm run dev`

管理员账号和 JWT 本地默认配置位于 `application.yml`，部署时请通过配置覆盖敏感值。

## 主要代码结构

```text
src/main/java/com/testpilot/
├── browser/       # 浏览器 Provider 抽象及 Playwright MCP 实现
├── controller/    # 任务、规则库、用例库、认证、个人中心 API
├── domain/        # AgentTask、CapturedApi、ArrangeRule、TestCase、User
├── dto/           # API 请求和响应对象
├── repository/    # JPA Repository
└── service/       # 录制、任务、编排规则和用例生成业务
src/main/resources/prompts.yml  # 大模型提示词模板
frontend/                       # Vue 前端
database/migrations/             # 手动执行的数据库迁移脚本
```

## 数据迁移

应用配置中的 `spring.jpa.hibernate.ddl-auto` 当前为 `update`，它不会删除已废弃列或表。清理旧版本遗留结构前，先备份并检查数据，再按需执行 `database/migrations/V20261022__remove_legacy_task_and_workflow_fields.sql`。该脚本将旧 `filter_prompt` 合并到 `recording_goal`，并删除旧任务字段及已退役工作流表；应用不会自动执行该脚本。

## 向量推荐说明

Spring AI PgVectorStore 使用 `testpilot_rule_vectors` 表保存规则向量，应用启动时初始化 pgvector schema。新增规则在首次参与推荐时向量化；编辑过的规则会在下一次推荐时更新向量。删除规则时同步删除对应向量。推荐返回余弦相似度分数，前端按分数从高到低展示；仅当相似度严格大于阈值时才会推荐。Embedding 模型维度必须与 `EMBEDDING_DIMENSIONS` 一致；更换模型或维度后需要重建该向量表并重新索引规则。

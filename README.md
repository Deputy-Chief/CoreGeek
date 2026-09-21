# CoreGeek《未来战争》参赛 AI

云核心网第十届编程大赛《未来战争》选手程序。基于 JDK 内置 `HttpServer` 的 HTTP 决策服务，对应任务书 v1.0 与接口文档 v1.0。

## 技术栈

| 项目 | 说明 |
|------|------|
| 语言 | Java 8+ |
| HTTP 框架 | `com.sun.net.httpserver.HttpServer`（JDK 内置） |
| JSON 序列化 | Gson 2.8.2（`lib/gson-2.8.2.jar`） |
| 构建方式 | `javac` 命令行编译，`makelist.txt` 管理源文件列表 |
| 运行方式 | `java -cp "bin;lib/*" com.huawei.Main <port>` |

## 目录结构

```
CoreGeek/
├── doc/                        # 任务书、设计文档、样例数据
│   ├── 任务书v1.0.md
│   ├── 项目设计文档.md
│   ├── request.txt             # 夜晚回合（roundNo=85）样例请求
│   └── request_day.txt         # 白天回合（roundNo=10）样例请求
├── lib/gson-2.8.2.jar
├── src/main/java/com/huawei/
│   ├── Main.java               # 入口，启动 HttpServer
│   ├── MyHttpHandler.java      # HTTP 请求处理器
│   ├── model/                  # 数据模型（POJO）
│   ├── strategy/               # 决策引擎
│   ├── llm/                    # LLM 客户端
│   ├── sandbox/                # 沙盒命令执行器
│   └── util/                   # 工具类
├── src/main/resources/log4j.properties
├── makelist.txt                # 编译用源文件列表（相对 src/ 目录）
├── build.bat / build.sh        # 编译脚本
└── run.bat                     # 运行脚本
```

## 编译

Windows（双击或命令行）：

```bat
build.bat
```

Linux / macOS / Git-Bash：

```bash
chmod +x build.sh && ./build.sh
```

等价的手动命令：

```bash
cd src
javac -Xlint:unchecked -Xlint:-options -source 1.8 -target 1.8 \
  -encoding UTF-8 -cp "../lib/*" -d ../bin @../makelist.txt
```

## 运行

```bash
run.bat 8080
# 或
java -cp "bin;lib/*" com.huawei.Main 8080
```

## 测试

```bash
# 夜晚回合（应生成 attack 指令）
curl -s -X POST -H "Content-Type: application/json" \
  -d @doc/request.txt http://localhost:8080/

# 白天回合（应生成 collect/build 指令）
curl -s -X POST -H "Content-Type: application/json" \
  -d @doc/request_day.txt http://localhost:8080/
```

## 关键设计

- 昼夜判定：`roundNo % 130 < 70` 为白天（见 `MapUtil.isDayTime`）。
- 决策流程：`DecisionEngine.decide()` → 初始化上下文 → 昼夜分发 → 白天/夜晚策略 → 返回 Response。
- 跨回合状态：`GameContext`（阵营、基地位置、LLM 日限额、任务状态机、已知矿点等）。
- 自进化任务：`TaskSolver` 状态机（IDLE → 移动 → 接任务 → LLM → 沙盒 → 提交）。
- 指令合法性预检：仅白天下发建造/采集、仅夜晚攻击、阵亡角色不下发指令，避免异常累计。

详细设计见 `doc/项目设计文档.md`。

## 策略书 v1.0 实现与验证

已接入分阶段建设、工人采矿分工、双任务点轮转、成功脚本复用、传闻宝藏推理、矿价事件囤售和第6–8天的有条件骚扰。夜间按工人1→加特林、工人2→火箭、开拓者→电磁炮分配，急救/急修优先于攻击。八方向 BFS 配合同回合预约和最终指令预检，检查角色、距离、背包、金币和冲突。

运行 `test.bat`（Windows）或 `bash test.sh`（Linux/macOS）执行回归测试，包含跨回合状态机和 HTTP 样例验证，无需额外测试依赖。

行为映射、参数假设与验证范围见 [策略实现说明](doc/策略实现说明.md)。

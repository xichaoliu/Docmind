# Docker 化部署排错记录

记录三个服务（React/Java/Python）容器化过程中遇到的问题和解决方式，按遇到的顺序整理，供复习和面试参考。

## 1. Docker Hub 镜像拉取超时

**现象**：`FROM python:3.10-slim` 卡在 metadata 拉取阶段，60 秒超时报 `DeadlineExceeded`。

**原因**：国内网络访问 Docker Hub（`registry-1.docker.io`）不稳定，与镜像内容本身无关。

**解决**：在 Dockerfile 里直接指定国内镜像加速地址，比配置 Docker Desktop 全局 `registry-mirrors` 更直接生效：
```dockerfile
FROM docker.m.daocloud.io/library/python:3.10-slim
```

## 2. 前端：平台专属原生依赖导致跨平台构建失败

**现象**：
```
npm error notsup Unsupported platform for @rolldown/binding-darwin-arm64@1.2.9
wanted {"os":"darwin","cpu":"arm64"} (current: {"os":"linux","cpu":"arm64"})
```

**原因**：现代前端构建工具（Rollup/Vite 的下一代实现 Rolldown 等）底层用 Rust/Go 编写，发布为平台专属的原生二进制包。本地 macOS 开发机生成的 `package-lock.json` 里锁定了 darwin 版本，而 Docker 容器是 Linux，`npm ci` 严格按 lock 文件安装，装的二进制和运行平台不匹配。

更深层原因：排查发现这个包被意外写入了 `package.json` 的 `dependencies`（正常应在 `optionalDependencies`，由 npm 根据当前平台自动判断是否安装），推测是项目初始化脚手架带入的异常依赖。

**解决**：从 `package.json` 的 `dependencies` 中删除这一行，重新生成 lock 文件：
```bash
rm -rf node_modules package-lock.json
npm install
```
删除后本地 `npm run dev` / `npm run build` 均正常，确认这个依赖本来就是多余的。

**经验**：跨平台依赖问题，优先检查是否有平台专属包被错误固化到 `dependencies` 而非 `optionalDependencies`，而不是一味切换 `npm ci`/`npm install` 试错。

## 3. 前端：TypeScript 未使用变量检查在构建时暴露

**现象**：`npm run dev` 正常，`docker build` 执行 `npm run build` 时报多处 `TS6133 declared but never read`。

**原因**：`npm run build` 脚本是 `tsc -b && vite build`，先执行完整的 TS 类型检查（包含 `noUnusedLocals`/`noUnusedParameters` 规则），而 `npm run dev` 走 esbuild 转译，不做此类严格检查。本地开发时的"残留未使用代码"（重构后忘记清理的 import、类型定义、解构变量）在构建阶段才被暴露。

**解决**：逐一清理未使用的 import 和变量。过程中顺带发现一处真实的功能缺陷——`ApiError` 类的构造函数参数漏写了 `public` 修饰符：
```typescript
// 错误：status 只是构造函数的临时参数，没有真正成为实例属性
constructor(status: number, message: string) { super(message); }

// 正确：public 修饰符让 TS 自动声明并赋值这个属性
constructor(public status: number, message: string) { super(message); }
```

**经验**：构建时的严格检查和开发时的宽松检查规则不同，不能只以 `npm run dev` 不报错作为代码健康的标准，提交前应该跑一次完整的 `npm run build` 验证。

## 4. 前端：`erasableSyntaxOnly` 禁止参数属性简写语法

**现象**：加上 `public status: number` 后，报错"启用 erasableSyntaxOnly 时，不允许使用此语法"。

**原因**：`erasableSyntaxOnly` 是较新版本 TS/Vite 脚手架默认开启的编译选项，只允许"单纯擦除类型标注即可编译成合法 JS"的语法。参数属性简写（`constructor(public x: type)`）会在编译后生成真实的赋值代码（`this.x = x`），不是单纯类型擦除，因此被该选项禁止。

**解决**：改回手动声明字段 + 构造函数内赋值的传统写法：
```typescript
class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}
```

**经验**：这类选项是在推动写更贴近标准、不依赖编译器"魔法"的代码，建议保留该选项而非关闭，手动写法本身也更利于长期维护。

## 5. Java：测试目录下的代码生成器工具缺少依赖，阻断容器构建

**现象**：`mvn clean package -DskipTests` 报编译错误，提示 `mybatis-plus-generator` 相关的包不存在。

**原因**：代码生成器（一次性工具，放在 `src/test/java`，依赖隔离进了 `codegen` 这个 Maven profile，默认不激活）。`-DskipTests` 只跳过运行测试，**不跳过编译测试代码**，容器构建默认不激活任何自定义 profile，导致这个文件的依赖缺失、编译失败。

**解决**：改用 `-Dmaven.test.skip=true`，彻底跳过测试代码的编译：
```dockerfile
RUN mvn clean package -Dmaven.test.skip=true
```

**经验**：`-DskipTests` 和 `-Dmaven.test.skip` 语义不同，前者"编译但不运行"，后者"完全不编译"；当测试目录下有依赖独立于主代码的工具类时，后者才是正确选择。

## 6. Python：`torch` 等大型依赖下载缓慢/卡住

**现象**：`torch`（400MB+，`sentence-transformers` 的间接依赖）下载反复卡顿。

**排查与解决**：
1. 优先检查 Dockerfile 的分层顺序：确认 `COPY requirements.txt` 和 `pip install` 在 `COPY . .`（复制全部源码）**之前**，否则每次改动业务代码都会使依赖安装层缓存失效，重新触发整个下载。
2. 换用国内 PyPI 镜像源：
```dockerfile
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple --timeout 120
```
3. 如果容器只在 CPU 上运行，可单独安装 CPU-only 版本的 torch，避免默认版本携带的 GPU 相关依赖增大体积、拖慢下载。

## 7. Python：Embedding 模型文件缺失，容器启动即崩溃

**现象**：
```
OSError: Error no file named model.safetensors, or pytorch_model.bin, found in directory /app/models/damo/...
```

**原因**：项目内 `rag/models/damo/...` 目录通过 bind mount 挂载进容器，但该目录本身只包含模型的配置文件（`config.json`、`tokenizer.json` 等），缺少真正的权重文件。本地开发时服务能正常运行，是因为实际加载的是系统级缓存目录（`~/.cache/modelscope/hub/...`）里的完整模型，项目内目录从未被真正使用过，问题被掩盖，直到 Docker 构建把这个不完整的目录当作唯一数据源才暴露。

**解决**：从系统缓存目录复制完整的模型文件（含权重）到项目目录：
```bash
cp -r ~/.cache/modelscope/hub/models/damo/nlp_gte_sentence-embedding_chinese-base rag/models/damo/
```

**经验**：本地开发环境里"能跑"不等于所有依赖路径都被正确配置，排查类似问题时要留意是否存在"实际使用的路径"和"代码里配置的路径"不一致、只是因为某个隐藏的默认行为（如库自带的缓存查找机制）掩盖了配置错误。

## 8. Java：反射获取 classpath 路径在 jar 包运行模式下失效

**现象**：
```
java.lang.UnsupportedOperationException
    at jdk.zipfs.ZipPath.toFile(Unknown Source)
```

**原因**：代码中用 `SomeClass.class.getProtectionDomain().getCodeSource().getLocation()` 反射获取"当前类加载位置"，本地 IDE 运行时这是一个真实磁盘目录（`target/classes`），`.toFile()` 正常工作；但 Docker 容器内应用打包为 jar 运行，这个位置变成了 jar 包内部的虚拟路径（`jdk.nio.zipfs` 体系），`.toFile()` 在这种虚拟路径上不受支持，直接抛异常。

**解决**：删除这种反射获取路径的写法，改为从显式配置读取文件存储路径（`application.yml` + `@ConfigurationProperties`），不依赖代码运行时所处的加载形态：
```java
Path dir = Paths.get(appProperties.getUploadDir(), docId);
```

**经验**：任何"反射获取代码自身位置"来推导业务路径的写法都存在环境耦合风险，业务路径应始终来自显式配置，而不是依赖类加载器的内部实现细节——这类细节会随着"本地运行/打包成 jar/部署到不同环境"而改变语义。

## 9. 数据库初始化脚本未生效

**现象**：`docker-entrypoint-initdb.d` 挂载了 `schema.sql`，但容器启动后查询不到任何表。

**原因**：PostgreSQL 官方镜像的这一机制只在数据卷**首次创建、完全为空**时触发。此前反复调试已经创建过 `pgdata` 这个命名卷（即使之前启动失败过），卷已存在，初始化脚本被跳过。

**解决**：
```bash
docker compose down -v   # -v 一并删除数据卷
docker compose up postgres
```

**经验**：调试 Docker Compose 涉及数据卷的服务时，"重新 up"不等于"重新初始化"，容易产生"明明配置对了却不生效"的困惑，需要明确区分这两种重启方式。

## 10. 前端环境变量：构建时固化 vs 运行时读取

**现象**：容器启动后修改环境变量，前端请求地址不变。

**原因**：Java/Python 的环境变量在程序运行时读取（`os.getenv`/`@ConfigurationProperties`），但前端的 `import.meta.env.VITE_XXX` 是在 `npm run build` 这一步被 Vite **直接替换成具体字符串、打包进静态文件**的，容器启动后只是用 Nginx 托管这些静态文件，没有任何程序会再去读取环境变量。

**解决**：采用三层 `.env` 结构（`.env` 通用默认值、`.env.development` 本地开发、`.env.production` 构建专用），`npm run build` 自动读取 `.env.production`；如需构建时动态指定（不同部署目标用不同后端地址），用 Dockerfile 的 `ARG`/`ENV` 在构建命令中覆盖：
```dockerfile
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build
```

同时需理解容器间通信（服务名，如 `http://gateway:8080`）与浏览器访问（宿主机暴露端口，如 `http://localhost:8080`）是两套完全不同的地址语义——前端发出的请求来自用户浏览器，不在 Docker 内部网络中，必须使用宿主机暴露的端口。

## 总体经验

这一轮排错暴露出一个共性规律：**本地开发环境里能正常工作的代码，隐藏了大量"恰好可行"的环境假设**（文件在哪个目录被加载、依赖是否被某个缓存掩盖、环境变量何时生效），Docker 化过程本质上是把这些隐藏假设逐一显式化、变成可配置项的过程。每一个报错看似独立，但排查思路是一致的：先定位是构建时问题还是运行时问题，再判断是路径/网络问题还是代码逻辑本身依赖了某个不该依赖的环境细节。

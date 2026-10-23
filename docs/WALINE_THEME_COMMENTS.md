# SterSU 主题评论区（Waline）

## 当前范围

- 云端主题详情和界面风格详情都有“查看与发表评论”入口，进入独立评论页。
- 内置官方 `@waline/client` **3.16.1**；客户端 JS、CSS 随 APK 分发，不依赖运行时 CDN。
- 支持昵称评论、选填邮箱、回复、点赞、分页、排序，以及日夜模式和窄窗口布局。
- 当前使用昵称评论，不自动使用商店 GitHub 资料，不把自填昵称冒充成已认证身份；发布者可能冒名，管理员必须审核。若以后启用强制账号登录，需同时适配客户端登录流程，不能只改服务端 `LOGIN=force`。
- 默认不发送图片、位置，不启用图库上传、GIF 搜索、远程表情、访问量统计。
- 没有配置服务地址时显示“评论服务尚未启用”，不会请求 Waline 官网或任何演示服务，也不影响下载和安装。

**已按用户提供的地址将默认服务配置为 `https://waline.nh24.top`。2026-10-23 从 Windows 主机执行 HTTPS 只读检查：根页面及指定测试主题的 `/api/comment` 均返回 HTTP 200，API 返回 `errno: 0`；页面自报服务端版本为 1.41.6。没有为此创建新的 Vercel/Neon 资源，也没有注册管理员或修改该服务配置。**

该服务当前只读响应的 `Access-Control-Allow-Origin` 为 `*`。这不等于评论来源已经被安全限制；服务所有者仍需检查 `SECURE_DOMAINS`、审核策略、备份和管理员权限。本次只读检查不证明发表、回复、点赞、审核及持久化已可用。未经明确授权，不向服务发布测试评论或更改其安全设置。

## 部署服务

Waline 官网是说明文档，不是可共用的评论服务器。**不需要购买服务器或域名**：官方支持 Vercel + Neon，可使用 Vercel 提供的 HTTPS 子域名。Vercel Hobby 仅适用于个人、非商业用途；Vercel 和 Neon 免费档均有额度限制，不保证无限请求或永久可用。部署时只选择免费档，不启用付费升级或计费资源。

### 首选：Vercel + Neon 免费档

1. 登录自己的 Vercel 账号；没有账号时由账号所有者自行注册、确认服务条款。不要把密码、验证码、数据库密码或 Token 发到聊天或仓库。
2. 使用 [Waline 官方 Vercel 部署入口](https://vercel.com/new/clone?repository-url=https%3A%2F%2Fgithub.com%2Fwalinejs%2Fwaline%2Ftree%2Fmain%2Fexample)，创建独立的评论服务项目和对应仓库，例如 `stersu-comments`。只部署 Waline 模板，不要把整个 SterSU 工作区上传到 Vercel。正式上线前固定并实测 `@waline/vercel` 的具体版本；自建示例固定为 `1.43.4`，但它未在本机启动实测，不要将本地模拟服务测试当作真实服务端兼容性验证，也不要无审查跟随 `latest`。
3. 按官方流程在项目 `Storage` 中选择 Neon，确认 **Free** 套餐后创建并连接到该项目。若出现付款、付费套餐或新增授权范围，先停止并由账号所有者确认。数据库与函数尽量选择相近地区。
4. 在 Neon 的 SQL Editor 中检查目标库尚无 Waline 数据表，再执行对应服务端版本的官方 [`waline.pgsql`](https://github.com/walinejs/waline/blob/c4b0f1ebb9a7970d36493d4c3580f3f43e098fa5/assets/waline.pgsql)。只初始化一次；已有表时不要重复执行、删除表或覆盖数据来处理错误。
5. 确认 Vercel 已注入 PostgreSQL 配置。固定版本 `1.43.4` 原生识别 `POSTGRES_DATABASE`、`POSTGRES_USER`、`POSTGRES_PASSWORD`、`POSTGRES_HOST`、`POSTGRES_PORT` 和 `POSTGRES_SSL`，也支持对应 `PG_DB`、`PG_USER` 等变量。如果集成只提供连接串，必须按该固定版本的实际配置要求适配，不能只填一个不被识别的变量就宣称已接通。数据库密码只保存在云端环境变量中。
6. 在 Vercel 的 Production 环境设置下列变量，然后重新部署使其生效。`JWT_TOKEN` 使用独立、至少 32 随机字节的密钥，只保存在服务端；不要使用管理员密码、主题签名私钥或 GitHub Token。

   | 变量 | 值 |
   | --- | --- |
   | `JWT_TOKEN` | 独立随机密钥，不能提交到 Git |
   | `SERVER_URL` | 实际 `https://<项目域名>.vercel.app` |
   | `SITE_URL` | 同一实际 HTTPS 地址 |
   | `SITE_NAME` | `SterSU Theme Store` |
   | `SECURE_DOMAINS` | `appassets.androidplatform.net,<实际项目域名>`，不含协议，不使用 `*` |
   | `COMMENT_AUDIT` | `true` |
   | `IPQPS` | `60` |
   | `DISABLE_USERAGENT` / `DISABLE_REGION` | `true` |
   | `AVATAR_PROXY` / `AKISMET_KEY` | `false` |
   | `MARKDOWN_TEX` / `MARKDOWN_HIGHLIGHT` | `false` |

7. 在对外发布服务地址或启用 APK 之前，由所有者亲自在 `/ui/register` 完成首个管理员注册并确认后台权限。首个注册账号会成为管理员；部署期间使用平台可用的访问保护，不要把尚未认领管理权的地址公开。
8. 管理员就绪后开放需要的客户端 API，实测 TLS、来源限制、提交与审核、回复、点赞和刷新持久化。最后将这个**公开服务 URL**配置到管理器并重新编译。受 Vercel 登录保护的地址不能直接供 Android 客户端访问。

Vercel 函数的本地文件系统不是持久数据库。**不要把 Docker 方案中的 SQLite 数据库或命名卷配置照搬到 Vercel**；评论持久化使用 Neon PostgreSQL，并另外安排数据导出或备份。管理员账号、数据库凭据和 JWT 密钥不进入 APK。

### 备选：自建 Docker

仅在已有自建服务器、Docker Compose 和 HTTPS 域名时使用此方案；这不是免费 Vercel 部署的前置条件。

部署文件在 `deploy/waline/`：

1. 把 `.env.example` 复制成同目录的 `.env`。
2. 将 `WALINE_HOST` 改成真实域名（不含协议、路径）。生成并填入独立的随机 `WALINE_JWT_SECRET`，至少 32 随机字节。不要复用 GitHub Token、管理员密码或主题签名私钥。
3. 在该目录执行 `docker compose config --quiet`；空密钥会阻止启动。随后执行 `docker compose up -d`。首次启动的 `waline-init` 使用同一固定版本的镜像，下载并校验对应版本的官方空白 SQLite 模板；成功后才启动服务。它不会覆盖已有数据库，下载失败或校验失败会阻止启动。首次启动需能连接 `raw.githubusercontent.com`；用 `docker compose logs waline-init` 检查结果。
4. 配置 DNS 和 HTTPS 反向代理，示例见 `nginx.conf.example`。容器端口仅监听服务器本机 `127.0.0.1:8360`，不要直接暴露到公网；不要关闭证书校验。
5. **先限制公网访问，亲自在 `https://真实域名/ui/register` 注册第一个账号。Waline 会将首个注册账号设为管理员。确认管理权后再开放服务。** 后续在 `/ui` 审核、删除垃圾评论。
6. `COMMENT_AUDIT=true` 为默认值，所有评论先审核。`IPQPS=60` 为按 IP 的发表限频；反向代理只信任自己的入口，不接受客户端伪造真实 IP。发布后检查 API 返回的 IP 是否为真实客户端地址。

数据库使用命名卷 `waline-data`，重建容器不会主动删除评论；必须另外备份该卷。**不要执行 `docker compose down -v`，这会删除数据库卷。** 更新服务前备份数据并记录旧镜像；示例固定官方 1.43.4 镜像及摘要，不自动跟随 latest。

SQLite 数据库和 JWT 私钥只存在服务端，不进入 APK、公开仓库、主题包或日志。`.env`、数据和备份目录已加入本目录忽略规则。若开放注册及邮件通知，需自行补齐 SMTP 配置、滥用防护与隐私政策。

初始化只验证文件标识与官方模板摘要，不替代数据库完整性检查。如果已有数据库损坏，应先停服务、备份现场，再恢复验证过的备份；不要删除数据库或卷来“修复”评论丢失。

## 连接管理器

默认使用用户指定的 `https://waline.nh24.top`，正常构建不再需要单独传入地址。可在 Gradle 用户配置或构建环境中覆盖**公开服务地址**：

```properties
STERSU_WALINE_SERVER_URL=https://comments.example.com
```

上面是占位地址，覆盖时必须替换成自己的真实地址，再重新编译管理器。也可传 `-PSTERSU_WALINE_SERVER_URL=https://真实域名`，或使用同名环境变量；Gradle 属性优先于环境变量。显式设置为空可关闭评论服务。不要把数据库密码、JWT 密钥、管理员账号或 GitHub Token 写进这个配置。

服务地址要求 HTTPS，不允许用户名、密码、查询参数或片段。支持部署在子路径，但反向代理必须相应转发；示例配置默认部署在根路径。

客户端页面来源固定为 `https://appassets.androidplatform.net`。随附的 Docker 示例 `SECURE_DOMAINS` 已包含该来源及自己的域名；现有云端服务需要其所有者确认配置，不能仅凭 HTTP 200 推断。请勿使用 `*`；若加上自己的网页客户端，只添加自己控制的确切域名。

评论稳定标识：

| 内容 | Waline path |
| --- | --- |
| 云端主题 | `/stersu/theme/<theme.id>` |
| 界面风格 | `/stersu/style/<style.id>` |

主题和界面风格不会混区；版本、名称、语言变更不改变讨论标识。新版本发布时应保留原来的主题 ID。这里的 path 是评论标识，不代表服务端存在对应网页。

## 安全和隐私

首次访问某个服务地址前有隐私确认，更换服务地址后需要重新确认。服务端接收昵称、正文、选填邮箱、网络 IP 和浏览器信息；即使隐藏 IP 地区/UA 的公开展示，服务器仍然可能记录这些数据。头像和评论中的远程图片可能请求第三方站点（Gravatar 可包含邮箱摘要）。不要在评论中发布含隐私的 root 日志。

评论页使用单独的普通 WebView，关闭本地文件/Content URI 访问、混合 HTTP 内容、第三方 Cookie、定位和网页权限；没有 JavaScript-to-native/root 桥，也不复用模块 WebUI。只开放列入白名单的本地评论资源。网页导航不能修改固定的服务或讨论参数，主动点击的 HTTPS 外链交给浏览器，其它协议阻止。客户端保留 Waline 版权链接和 MIT 许可。

## 上线验收

1. 未配置服务：有明确提示；未同意隐私：不创建 WebView、不加载服务。
2. 在测试主题中评论、回复、点赞，审核后刷新能看到；重启管理器和升级主题仍落在同一评论区。
3. 相同 ID 的主题和界面风格评论互不串区。
4. 断网、服务 500/错误响应时显示重试；恢复网络后可重新连接。
5. 亮色、暗色切换不丢失正在输入的内容；小窗和大字体下能滚动、提交。
6. 验证外链不会调用 root/安装接口，也不能访问本地文件；检查服务来源限制和管理员权限。
7. 在测试环境验证数据库卷的备份、恢复、升级，不要通过删除生产卷测试。

本地自动化使用模拟服务，不向真实服务发布评论；上线的域名、TLS、CORS、审核和数据库持久化仍需要部署后实测。SQLite 初始化测试仅覆盖 Docker 备选方案，不能证明 Vercel + PostgreSQL 已部署成功。

开发时可在 Node.js + Playwright 环境运行 `node scripts/test-theme-comments.cjs`，测试评论、回复、点赞、分页、排序、日夜对比度和窄窗口。`node scripts/test-waline-deployment.cjs` 检查初始化的失败与保留已有数据库行为，并下载校验一次官方空白模板；不启动公开服务。

若本机 Node.js 无法直接访问 GitHub，可先下载该固定提交的 `assets/waline.sqlite`，通过 `STERSU_WALINE_TEST_SEED` 环境变量指定文件绝对路径，再运行部署测试；测试仍会校验生产脚本中的同一 SHA-256，不接受任意替代模板。

### 本次本地核验（2026-10-23）

- Windows / PowerShell 7.6.5，Java 21、Node.js 25.8.0、Edge 无头浏览器。
- `:app:compileReleaseKotlin` 和 `:app:testDebugUnitTest` 成功，1,030 项单元测试通过。
- 官方打包客户端的 12 项模拟服务浏览器检查通过，覆盖提交、回复、点赞、分页、排序、独立主题标识、日夜可读性、草稿保留、窄窗口、平板大字体、错误重试和英文。
- 初始化的 5 项安全检查通过；成功分支使用预先从官方固定提交下载并核对摘要的空白 SQLite 文件，已有库不被覆盖。
- 本机没有 Docker，未运行 Compose 容器；没有创建新的云资源或上传私钥。后续按用户指定的 `https://waline.nh24.top` 完成 HTTPS 只读请求核验；没有发布真实评论，写入、审核及持久化仍待服务所有者授权和实测。
- 使用打包的客户端和真实 `https://waline.nh24.top` 做只读浏览器联调：主题与界面风格的独立测试标识均得到 HTTP 200 / `errno: 0`，编辑器可见，切换日夜模式保留未发送草稿，无 JavaScript 页面错误；脚本阻止所有远程非 GET 请求。记录位于 `out/theme-comments-live/results.json`。运行方法：显式设置 `STERSU_WALINE_LIVE_SERVER=https://waline.nh24.top` 后执行 `node scripts/test-theme-comments-live.cjs`；这仍然不验证真实写入、审核、数据库备份和 Android 实机。
- 配置真实地址后执行 `clean :app:assembleRelease :app:testDebugUnitTest` 成功，1,030 项测试全部通过；12 项模拟交互检查再次通过。Release APK 为 `out/release-20261023-waline/SterSU_3.3.2_33200-release.apk`，17.63 MiB；APK 内 DEX 包含指定地址，6 个评论资源文件逐一与源文件摘要一致，签名及 16 KiB 对齐检查通过。签名沿用已有 Android Debug 证书，与上一版一致，APK 本身为不可调试的 Release 构建；未安装到手机。

## 来源与许可

- Waline 官方文档：https://waline.js.org/
- 客户端属性：https://waline.js.org/reference/client/props.html
- 独立部署：https://waline.js.org/guide/deploy/vps.html
- Vercel 官方部署：https://waline.js.org/guide/deploy/vercel.html
- 数据库部署：https://waline.js.org/guide/database.html
- Vercel Hobby 范围：https://vercel.com/docs/plans/hobby
- Neon 免费档与额度：https://neon.com/pricing
- 服务端变量：https://waline.js.org/reference/server/env.html
- SQLite 部署：https://waline.js.org/guide/database.html#sqlite
- 官方项目：https://github.com/walinejs/waline
- `@waline/client` 3.16.1（MIT）原许可位于 `manager/app/src/main/assets/theme-comments/vendor/LICENSE`。发行包未做代码修改。
- 官方客户端 npm 包 integrity：`sha512-3zrFOl1kK0ITz8npaB7HFecx6PS6qpTNfF9qdNx49pffGxQ4luIX06MaPBn6LgbnhCTTWHCHa0v3wHn5j7BwDw==`。
- `waline.js` SHA-256：`4b007b2a4ffd4c1d40e17c7f781e52557ddc709d68aff00218c92067dd1fe462`。
- `waline.css` SHA-256：`9e418af7d3a507f6c4add97932ed728cd08a81693934f677c75143c726523976`。
- SQLite 空白模板固定到官方提交 `c4b0f1ebb9a7970d36493d4c3580f3f43e098fa5`（服务端 1.43.4），SHA-256：`ac08959a80b2756701742d97ad445fab24597428b3bc56e0c87541c4ea8b1b37`；只在服务端首次初始化时下载。

# SterSU 主题评论区（Waline）

## 当前范围

- 云端主题详情和界面风格详情都有“查看与发表评论”入口，进入独立评论页。
- 内置官方 `@waline/client` **3.16.1**；客户端 JS、CSS 随 APK 分发，不依赖运行时 CDN。
- 支持昵称评论、选填邮箱、回复、点赞、分页、排序，以及商店“我的”中的邮箱登录、注册、头像和名称设置、退出和过期状态恢复。
- 评论页跟随当前商店配色、日夜主题、主题色、全局／当前页面壁纸、UI 穿透表面透明度、字号、字体和文字／图标／图片各自的透明度。WebView 本身透明，不复制或上传壁纸。文字使用绘制颜色透明度，不对包含图片、图标的容器整体降低透明度。自定义字体仅开放当前选中的字体文件，不开放目录访问；不可用时退回系统字体。
- 当前账号入口为 **商店 → 我的 → 评论账号**。只提供邮箱登录、注册；不再显示 QQ、微博、GitHub 等第三方授权，也不跳转浏览器等待会话回传。表单直接向配置的 HTTPS 服务发送 `POST /api/token`、`POST /api/user`，登录后另以 `GET /api/token` 验证会话。已启用两步验证的邮箱账号可输入验证码；注册需要邮箱验证时明确提示，不假报登录成功。昵称评论仍可使用，评论账号不等同于商店的 GitHub 公开资料。`login: 'disable'` 禁用供应商自己的登录控件。
- 登录后可设置名称和公开的 HTTPS 头像地址。使用 `PUT /api/user` 保存，再以 `GET /api/token` 重读确认，成功后更新按服务地址隔离的本地会话；其它主题评论页在恢复时读取同一账号。头像留空保留当前值。Waline 没有通用的头像文件托管接口，此处不把本地文件路径伪装成可供其它设备访问的头像地址。密码不写入本地存储，提交后清空密码和验证码。
- 默认不发送图片、位置，不启用图库上传、GIF 搜索、远程表情、访问量统计。
- 没有配置服务地址时显示“评论服务尚未启用”，不会请求 Waline 官网或任何演示服务，也不影响下载和安装。

**已按用户提供的地址将默认服务配置为 `https://waline.nh24.top`。2026-10-23 从 Windows 主机执行 HTTPS 只读检查：根页面及指定测试主题的 `/api/comment` 均返回 HTTP 200，API 返回 `errno: 0`；页面自报服务端版本为 1.41.6。没有为此创建新的 Vercel/Neon 资源，也没有注册管理员或修改该服务配置。**

该服务当前只读响应的 `Access-Control-Allow-Origin` 为 `*`。这不等于评论来源已经被安全限制；服务所有者仍需检查 `SECURE_DOMAINS`、审核策略、备份和管理员权限。本次只读检查不证明发表、回复、点赞、审核及持久化已可用。未经明确授权，不向服务发布测试评论或更改其安全设置。

## 2026-10-24 邮箱账号调整

- 原 OAuth 窗口、浏览器临时回传页、本地回传端口及会话 Cookie 探测实现已从客户端移除。服务端本身的第三方授权配置没有修改；客户端不再进入这些流程。
- 账号页直接复用评论文档的可信本地来源和按服务隔离的存储，但使用独立 `mode=account`，不生成虚假的主题标识，也不加载或提交评论。资料草稿在会话重读时保留。
- 保存会话前始终以配置服务的 `/api/token` 校验；没有认证的窗口消息、旧的 `WALINE_USER` 广播和外部链接不能注入账号。存储失败不报告登录成功；服务暂时不可用时保留原会话，明确显示重试。
- Windows / Java 21 编译及单元测试通过：1,119 项中 1,118 项通过、1 项在线 DNS 检查跳过、0 失败。Node 24.22.1 / Edge 156 浏览器模拟检查覆盖 28 组场景，包括邮箱注册和登录、资料保存与重新载入、评论携带认证、退出、过期、断网、日夜主题、壁纸穿透及 240px 小窗与 160% 字体。
- 当前真实服务的只读浏览器检查通过：主题与界面风格评论请求均返回 HTTP 200 / `errno: 0`，邮箱账号表单正常加载，0 页面错误。没有注册真实账号、提交密码、修改真实头像或名称，也没有在 Android 设备安装验证。本次未打包 APK、未推送仓库。记录在 `out/theme-comments-browser/email-account-unit.log`、`out/theme-comments-browser/results.json`、`out/theme-comments-live/results.json`。

以下部署说明仍适用；文末旧的网页登录验证记录是历史实现，当前行为以本节和“已实现”说明为准。

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

评论页和账号页使用单独的普通 WebView，关闭本地文件/Content URI 访问、混合 HTTP 内容、第三方 Cookie、多窗口、定位和网页权限，不复用模块 WebUI 或 root 桥。`WebViewCompat.addWebMessageListener` 只向可信本地顶层评论页开放固定的 `open-my` 动作，并校验来源和完整讨论 URL；不接收账号、Token、密码、任意 URL 或命令。账号请求使用 `credentials: omit`、`redirect: error`，会话按服务地址隔离；不读取浏览器或其它应用的 Cookie。只开放白名单中的评论资源和当前字体。主动点击的 HTTPS 外链交给浏览器，其它协议阻止。客户端保留 Waline 版权链接和 MIT 许可。

### 登录与账号隔离

- 密码和两步验证码只在服务端的官方登录网页中输入，评论页面不再读取、提交或保存凭据。Android 页显示实际网页域名，支持返回、关闭和网络失败重试；关闭会取消未完成的认证，迟到响应不能重新登录。登录网页不获得本地资源加载器、文件／Content URI 访问、定位／网页权限或 root／原生桥，禁止 HTTP 混合内容；除下述受限 QQ／微博客户端唤起外阻止非 HTTPS 协议。系统返回及顶栏返回均关闭网页登录，保留原评论页。
- 登录状态按规范化服务地址保存在 WebView LocalStorage 的 `STERSU_WALINE_USER:<serverURL>` 中，直到主动退出或服务判定失效。不同主题／风格共享同一服务账号，不共享讨论；换服务不会携带旧服务的 Token。
- 固定客户端 3.16.1 通过 VueUse `StorageEvent` 接收当前页面的内存账号。其旧版无服务范围的 `WALINE_USER` 键不保存 Token。只接受本次主动打开窗口、严格匹配服务 origin 的 `userInfo` 消息，并通过该服务的 `GET /api/token` 重新取得账号资料；不信任消息提供的昵称、邮箱、权限。其它 WebView 的旧键事件和其它消息（包括 `profile`）全部拦截。Token 不进入回跳 URL；升级客户端时必须重跑隔离测试。
- 重新打开、回到前台会通过官方 `GET /api/token` 验证已保存的账号。网络故障保留已保存记录供重试，但不把未经验证的账号当作已登录；明确过期则清除记录。登录、退出与外观更新不刷新页面，保留编辑中的内容及回复目标。
- 注册、找回密码及 CAPTCHA／第三方 OAuth 等由服务原有登录网页提供，不自动申请账号、不更改服务端设置，也不绕过验证；具体服务的真实认证仍需账号所有者验收。评论区“退出”清除本机评论会话，不代替服务登录页自身的“记住我／退出”设置。
- 原生只向本地评论页传入经过 JSON 编码的外观值、取消网页登录或当前一次性浏览器登录结果；登录结果仍必须通过所配置服务的 `/api/token` 校验。不读取网页密码，不将远程 HTML 注入原生界面，也没有网页到原生／root 的桥。外观值仅接受固定颜色格式、有限数字和字体白名单，不执行任意 CSS、URL 或脚本；外观更新不变更服务地址与主题 ID。

## 上线验收

1. 未配置服务：有明确提示；未同意隐私：不创建 WebView、不加载服务。
2. 在测试主题中评论、回复、点赞，审核后刷新能看到；重启管理器和升级主题仍落在同一评论区。
3. 相同 ID 的主题和界面风格评论互不串区。
4. 断网、服务 500/错误响应时显示重试；恢复网络后可重新连接。
5. 亮色、暗色切换不丢失正在输入的内容；小窗和大字体下能滚动、提交。
6. 验证外链不会调用 root/安装接口，也不能访问本地文件；检查服务来源限制和管理员权限。
7. 在测试环境验证数据库卷的备份、恢复、升级，不要通过删除生产卷测试。
8. 使用测试账号确认密码错误、两步验证码、登录成功、重进验证、退出、取消迟到响应及会话过期；验证登录前后的草稿和回复目标不丢失。不要在生产环境发布测试评论，或将真实账号凭据放进测试脚本。

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

## 历史：外观同步与本地登录核验（2026-10-24）

- 本次修改仅涉及评论页、外观适配、隐私说明与测试；没有修改内核、上传仓库、安装管理器或交付新的 APK。
- Windows / PowerShell 7.6.5、Java 21；`:app:compileReleaseKotlin` 与 `:app:testDebugUnitTest` 成功，1,032 项单元测试通过。Gradle 仍报告仓库既有的实验设置／弃用警告和 5 个缺少默认值的无关资源，未在本任务中扩改。
- Node.js 25.8.0 / Edge 156.0.4314.42：21 项模拟服务浏览器检查通过，无 JavaScript 页面错误。覆盖背景与表面透明度、主题色、日夜、中文／英文、大字体、小窗、独立前景透明度、自定义字体、API 登录、错误密码、可选两步验证码传递、会话重进验证／过期／网络故障、取消迟到响应、退出清理账号邮箱、草稿及回复目标保留、跨服务隔离和消息注入拦截。更改字号／字体时重新计算编辑器高度，普通配色帧不强制重排。
- 真实 `https://waline.nh24.top` 的只读主题／风格联调成功，编辑器及登录弹窗可见；`/api/token` 的无凭据 OPTIONS 返回 204，允许 Content-Type 和 Authorization 请求头。未提交真实密码、未创建账号、未发表评论、未更改服务设置。该服务仍返回 CORS `*`，服务所有者应继续核查来源限制和审核策略。
- 记录与截图位于 `out/theme-comments-browser/`、`out/theme-comments-live/`。真实账号认证、CAPTCHA／OAuth、生产写入与数据库持久化、Android 实机尚未验证；模拟登录通过不能替代这些验收。

## 网页登录切换（2026-10-24）

- 移除评论资源中的本地密码／两步验证码表单及其弹窗样式，改为打开服务端官方登录网页。Android 用单独的普通 WebView 呈现为完整页面，显示当前 HTTPS 域名；原评论文档保留在后方，不使用移动端整页重定向，所以草稿与回复状态不必重建。
- 只有本地评论页的用户手势可创建窗口，首个页面必须严格匹配所配置服务的登录路径；关闭后及时释放 WebView。认证消息必须同时匹配请求窗口、服务 origin、消息类型和有长度上限的 Token，再由该服务的只读认证接口取得可信账号。取消时中止验证并废弃迟到结果；登录网页无法访问管理器本地资源或原生／root 桥。
- Node.js 25.8.0 / Edge 156.0.4314.42 的 24 项模拟浏览器回归通过，无页面脚本错误。除既有评论／外观覆盖外，补测官方网页回传协议、消息中的伪造账号字段、登录中错误 origin／窗口、无效及超长 Token、弹出被阻止、验证期间取消，以及关闭网页后的可恢复状态。
- 当前真实服务的主题／风格只读检查通过：评论 API 有效、编辑器可见、官方登录网页的邮箱输入框可打开；未提交真实凭据，也未发表评论或更改站点配置。真实账号认证、第三方认证／验证码兼容性和 Android 实机仍未验证。
- 本轮不生成管理器 APK、不安装到设备、不上传仓库；旧 APK 不包含此项改动。测试记录位于 `out/theme-comments-browser/results.json` 和 `out/theme-comments-live/results.json`。

## 访问速度与指定 DNS（2026-10-24）

### 加载流程

- 删除首屏额外的 `type=count` 健康检查，直接观察 Waline 自己的列表请求；本地客户端加载、评论读取不再串行等待账号恢复。验证账号期间允许阅读、编写草稿，但阻止以游客身份误提交。
- 列表读取有 15 秒前端超时、原生 12 秒整体超时和错误重试；重试调用 Waline 原本的刷新操作，不销毁编辑器，因此草稿及回复目标保留。不缓存评论正文或账号 Token。
- Windows / Node.js 25.8.0 / Edge 156 的受控延时回归中，账号检查延迟 2000ms、列表延迟 200ms 时，首屏评论约 479ms 出现；这是模拟结果，不是手机实测或公网速度承诺。

### DNS 生效范围与边界

- 只有本地评论 WebView 对配置服务的 **GET `/api/comment`**（含翻页、排序）使用独立 OkHttp 读取层。解析源固定为 `8.8.8.8`、`1.1.1.1`、`114.114.114.114`，并发选择成功应答，支持 A / AAAA / CNAME。
- 使用普通 UDP DNS，不是 DoH / DoT；解析服务能看到服务域名。单次 UDP 等待上限 700ms；解析整体等待上限 2300ms。缓存遵循应答 TTL，上限 60 秒；零 TTL 不缓存，失败不负缓存。三台都失败时返回可重试错误，**不静默回退系统 DNS**。
- HTTPS 请求仍使用原服务域名、平台证书链及主机名校验。禁止自动重定向和代理改写，不发送 Cookie；认证列表的 Bearer 头只发往同一 HTTPS 列表端点。响应上限 2MiB，无正文磁盘缓存，退出评论页取消本页未完成的读取。
- **网页登录及 Token 校验、发表评论／回复／点赞／编辑／删除、头像及其他远程资源仍由 WebView 正常联网，不在固定 DNS 范围内。** Android WebView 的标准拦截接口不提供 POST 请求体，本实现不伪造写入、不注入凭据原生桥、不安装 VPN、不修改手机系统 DNS，也不改商店其他请求的 DNS。
- 同意说明补充 DNS 接收方、未加密解析和生效范围；使用新的同意记录键，旧用户首次进入新版评论区需重新确认。

### 已验证与未验证

- JVM 单测覆盖指定解析源、缓存命中／过期／零 TTL、单解析器故障、IPv6-only 应答、CNAME、异常报文／ID／压缩指针、同源同路径请求限制、跳转拒绝、响应大小、Cookie 隔离和失败关闭。
- 在本机 Java 21 的显式只读检查中，固定 DNS 冷解析采样 419ms、缓存命中 0ms，HTTPS 列表读取成功（HTTP 200）。一次完整读取采样约 7.7 秒，包含 TLS、连接和服务响应，不能据此推断公网持续提速；主要可确定的改进是移除重复请求与串行等待。
- 浏览器模拟回归补测不重复探测、账号恢复不阻塞首屏、未验证账号时阻止误提交、超时、失败重试保留草稿；真实服务的主题／风格只读检查和官方登录页面打开通过，没有向真实服务发评论、提交账号或改配置。
- Android 实机 WebView、运营商限制 UDP 53、VPN／IPv6-only 网络及真实账号认证仍未验证；指定解析器被网络屏蔽时，读取会明确失败而不会偷偷使用其他 DNS。没有 APK 打包、设备安装或 GitHub 上传，现存旧 APK 不包含本次修改。
- API 依据：Android `WebViewClient.shouldInterceptRequest` / `WebResourceRequest`，OkHttp `Dns` 接口及 RFC 1035；未增加新依赖。源文件为 `ThemeCommentDns.kt`、`ThemeCommentReadClient.kt` 和 `theme-comments/network.js`。

## 登录跳转兼容性修正（2026-10-24）

- 点击登录时同步创建空白窗口，再导航到所配置服务的官方 HTTPS 登录页，保留原评论页和 `opener`；窗口创建或导航异常时关闭空白窗口并恢复重试入口，不丢弃草稿。
- Android 宿主按固定来源、路径和解码后的完整查询参数识别当前评论页，允许等价的 URL 编码和参数顺序，拒绝修改服务／讨论参数、重复参数或附加参数。先交付 `WebViewTransport`，再呈现登录窗口。
- 用户手势限制、首个登录地址检查、无原生桥、来源与窗口校验、服务端 Token 验证保持不变。桌面浏览器回归不等同于 Android 实机验证；本次不自动安装或打包 APK。
- Windows / PowerShell、Java 21 下 `:app:compileReleaseKotlin :app:testDebugUnitTest` 成功：1112 项中 1111 通过、0 失败、1 项显式开启的线上 DNS 测试跳过。Node.js 24.22.1 / Edge 156.0.4314.42 的 29 项模拟回归通过；真实服务的主题、风格评论只读检查均能打开官方登录页，未提交凭据或真实评论。手机旧版日志未提供明确根因，新代码尚未安装到实机验证。

## QQ／微博登录兼容与浏览器入口（2026-10-24）

- 修正登录 WebView 拒绝客户端协议后立即关闭整个登录页的分支。只有列入白名单的官方 QQ／微博 HTTPS 授权来源可以唤起对应客户端；固定包名为 `com.tencent.mobileqq`、`com.sina.weibo`，不转发网页提供的 Intent 组件、附加参数、授权标志或 fallback 地址。无客户端或不支持的协议显示提示，保留登录页和原评论草稿。
- 临时登录 WebView 局部启用第三方 Cookie，允许 QQ 所需的 `about:blank` 子框架与 HTTPS 框架，并启用宽视口／页面适配。关闭时恢复此视图的第三方 Cookie 策略；主评论 WebView 的第三方 Cookie 仍禁用。该兼容设置不放开本地文件、Content URI、混合 HTTP 内容或网页权限。
- 当时在登录页右上角新增“浏览器登录”，确认后直接打开配置服务的全新 `/ui/login?lng=...`。由于系统浏览器与应用内 WebView 会话独立，这个旧实现不能自动同步。下节“一次性浏览器登录同步”已取代此入口的直接跳转；仍不转交原有 OAuth state／Token URL，无法打开浏览器时保留当前登录窗口。
- WebView 关闭操作延后到当前 Chromium 回调结束，避免在回调栈内直接销毁视图。没有找到可归因到 SterSU 的 AndroidRuntime 崩溃栈；“微博闪退”目前只能定位到协议被拒绝后登录页被关闭，不能等同于已确认整个应用进程崩溃。
- 只读打开真实微博授权页，确认其“一键登录”脚本使用 `sinaweibo://browser?url=...`，已为该实际协议添加校验用例；没有点击确认授权或提交账号。新增 JVM 覆盖官方来源／固定包名、伪造域名与危险协议拒绝、空白框架、浏览器独立会话以及失败保留登录页的源码契约。
- 使用 Android WebView UA 的桌面 Edge 只读检查可打开真实 QQ 授权页并发现两个 `about:blank` 框架，未重现手机白屏；未提交账号、授权或评论。QQ／微博真实账号的 Android 完整授权和回流仍待实机验收，不将桌面检查或源码契约测试当作实机修复证明。本轮不打包、不安装、不推送；旧 APK 不包含本节改动。
- Windows / PowerShell、Java 21 下 `:app:compileReleaseKotlin :app:testDebugUnitTest` 成功：1118 项中 1117 通过、0 失败、1 项显式开启的线上 DNS 测试跳过；其中 `ThemeCommentsTest` 的 19 项均通过。Node.js 24.22.1 / Edge 156.0.4314.42 的 29 项模拟浏览器回归通过。构建仍有既有的 Gradle 过时配置与部分资源缺少默认值警告，不属于本次登录改动。

## 一次性浏览器登录同步（2026-10-24）

### 使用方式

1. 在评论区打开登录页，点击右上角“浏览器登录”并确认。
2. 系统浏览器打开本机临时同步页，点击“打开 Waline 登录页”，在所配置服务的官方 HTTPS 页面完成认证。浏览器若阻止新窗口，会明确提示允许弹窗后重试。
3. 浏览器显示“登录结果已送达管理器”后，切回管理器。当前评论页向同一服务的 `GET /api/token` 验证账号，成功后关闭应用内登录窗口并更新账号。原评论草稿和回复目标保留。

请保留当前评论页，不能强制结束管理器或刷新页面。会话有效期为 5 分钟，关闭登录／退出评论页／重新开始流程会立即废弃旧会话；超时或管理器被系统回收时需要从管理器重新开始。只显示“已送达”不代表账号验证已经成功，服务不可用或 Token 失效时仍显示验证失败／重试提示。

### 实现与边界

- 每次创建仅绑定 `127.0.0.1` 随机端口的临时监听器，以及随机 256 位路径和会话标识；不新增线上服务、公开监听、永久密钥、Deep Link 或网页到原生／root 的桥。
- 本机页只接受本次打开的官方 HTTPS 登录窗口、严格匹配服务 origin 的 Waline `userInfo` 消息，不接受其他页面或来源。账号昵称、权限等来自 `/api/token` 的校验结果，不信任消息自带资料。
- Token 先用 AES-256-GCM 加密，随机 AES 密钥用本次 RSA-2048 OAEP SHA-256 公钥包装，再 POST 到本机。会话标识作为 AAD，并严格核对解密后的服务和会话；Token 不进入 URL、日志、剪贴板或临时页面存储。临时页面与密钥不持久化，账号校验成功后仍使用现有按服务隔离的评论会话存储。
- 接收端校验精确 Host／Origin／路径／方法、请求大小和密文结构，拒绝重复 Header、Transfer-Encoding 和无效 Token，只接收一次。回传仅交付给同一个评论 WebView、相同的完整讨论 URL 和仍有效的登录窗口；取消、切换页面或超时后的迟到结果不能恢复登录。
- 页面与脚本带有 `no-store`、`no-referrer`、来源受限 CSP；临时文件不加入主评论页本地资源白名单。不能读取或复制系统浏览器 Cookie，也不要求用户粘贴登录 Token。
- 这是对 Waline 现有 `postMessage` 登录协议的受限回传，不是新增第三方 OAuth SDK，也不宣称实现 OAuth PKCE 或 RFC 8252 的全部要求。Waline 服务若改变回传协议，或设置阻断 opener 的策略，需要相应适配。浏览器和 Android 对本机页面、后台进程及弹出窗口的限制仍需实机确认。

### 本地验证

- 使用模拟服务和测试 Token，不提交真实凭据、不确认真实第三方授权、不发布评论，不更改线上配置。
- `ThemeCommentBrowserSessionTest` 使用真实 JVM Socket 和加密，覆盖回传、重放、跨会话、错误服务／state、密文篡改、Token 长度、请求头／体边界、取消和过期；显式设置 `STERSU_TEST_NODE` 可启用 Edge／Playwright 到 JVM 接收端的实际 WebCrypto 测试。
- 浏览器回归检查当前会话校验、关闭后迟到、验证中取消、无效 Token、草稿保留，以及原应用内网页登录仍可重试。同步页同时检查日夜主题和 280px 小窗口。
- Windows／Java 21 下 `:app:compileReleaseKotlin :app:testDebugUnitTest` 成功：1127 项中 1126 通过、0 失败、1 项显式开启的线上 DNS 测试跳过；本次浏览器同步的 9 项 JVM 测试全部通过，包括实际 Edge WebCrypto 到 JVM 的回传。Node.js 24.22.1／Edge 156.0.4314.42 的 33 项模拟服务浏览器回归通过，无页面脚本错误；记录位于 `out/theme-comments-browser/results.json`，同步页日夜截图位于同目录的 `browser-handoff-light.png` 和 `browser-handoff-dark.png`。
- 这些检查不能替代 Android 实机上 QQ／微博完整授权、浏览器弹窗和回流验收。本轮仅修改源码并编译验证，不打包、不安装、不推送；现有 APK 不包含此次同步修复。

## 浏览器登录结果确认与恢复（2026-10-24）

本节取代前述一次性同步流程中“需先打开应用内登录页”和“收到回传后即停止监听”的行为。用户反馈临时页一直提示完成登录或没有反应，表明该次流程尚未收到结果；目前没有证据确定具体手机浏览器丢失结果的原因。

1. 在评论页点“浏览器登录”，打开本机临时页，再点“打开 Waline 登录页”。在同一个浏览器完成认证；若临时页未收到结果，点“我已登录，重新同步”。它重开本次命名登录窗口，利用官方页面读取已保存账号后重发 `userInfo` 的机制恢复回传；30 秒未收到时显示明确说明。
2. 收到结果后只显示“请切回管理器完成账号校验和保存”，不把 HTTP 202 当作登录成功。Android 在内存中暂存本次结果，待评论页回到前台后交付，不再要求应用内登录窗口仍然存活。
3. 评论页对同一服务调用 `GET /api/token`，校验并写入按服务隔离的 LocalStorage 后才确认成功。临时页轮询不含凭据的本机状态，收到管理器确认后才显示已登录；短暂连接失败可重试，刷新临时页也可重新读取确认结果。
4. 临时网络故障保留内存中的待验证结果，管理器显示“重试同步”；取消、超时、验证拒绝或无法保存都不会恢复登录。原评论草稿保留。同步仍有 5 分钟期限，退出评论页、刷新评论页或结束管理器进程后需重新开始。

本地验证：Windows／PowerShell、Java 21 下 Release Kotlin 编译成功，完整 JVM 测试共 1140 项，1139 通过、0 失败、1 项需显式开启的在线 DNS 测试跳过。Node.js 24.22.1／Edge 156.0.4314.42 的 37 组模拟浏览器回归通过；另有 Edge WebCrypto 到真实 JVM Socket 的集成测试，模拟首次登录遗漏回传后重新同步，覆盖来源校验、一次性加密交付及管理器确认。取消、网络重试、存储失败、账号重载和草稿保留都有回归。记录在 `out/theme-comments-browser/`。

未实测真实手机浏览器上的 QQ／微博完整授权；未代用户登录账号、安装管理器或发布真实评论。本轮未打包、安装或推送，现有 APK 不包含本轮改动。

## 官方网页登录与本站会话同步（2026-10-24）

用户实测上一版在 QQ 和其他登录方式成功后，无法回到临时同步页；上一轮模拟回归没有覆盖这个设备行为。因此默认入口改回管理器内的官方登录网页，并新增独立于 `opener`／`postMessage` 的会话同步，不再将“重新打开临时页”作为默认登录的必要步骤。

- 官方页完成登录后，从该服务自己的 `window.TOKEN`、大写 `sessionStorage.TOKEN`、`localStorage.TOKEN` 及旧版小写会话键读取候选值；另支持同一应用 WebView Cookie 存储中名为 `TOKEN` 的单一 Cookie 作为备用。不是读取或复制系统浏览器／QQ／微博应用的 Cookie，也不导出整个 Cookie 头。
- 原生层在读取前后都检查当前登录 WebView、HTTPS 服务来源和 `/ui` 路径，只能交付给仍活动的原评论页。不读取密码、验证码、其他站点的存储或任意 Cookie；切到第三方 OAuth 域名时不执行会话读取，暂停／关闭登录页停止轮询。登录页没有 root／模块 WebUI 桥。
- 候选会话仍必须由原评论页向同一服务 `GET /api/token` 验证并保存，不能凭 Cookie／存储存在就显示已登录。第三方账号允许邮箱、显示名缺省，以服务验证后的稳定账号 ID 为准；无效账号仍拒绝。过期候选值不会抢先关闭官方表单，用户可重新登录或点“同步登录”。
- 运行时支持受限消息通道的 Android WebView 由原生返回／关闭事件管理登录窗口寿命，不依赖 OAuth 过程中可能失去的 `WindowProxy.closed`。原有浏览器加密回传路径保留为备用，仍受来源、会话及验证约束。

验证使用独立测试浏览器、模拟账号和本机拦截的 API，不提交真实账号凭据或真实评论。除常规模拟回归外，下载并使用该服务实际加载的 Waline admin 脚本进行无 `opener` 的登录和刷新恢复测试；脚本摘要和记录位于 `out/theme-comments-browser/owned-session-results.json`。Cookie 单元测试覆盖选定键、编码、重复键、来源、长度及非法值；这不等同于实机浏览器 OAuth 授权和 HttpOnly Cookie 行为验证。

本轮交付核验：Windows／PowerShell、Java 21 下 Release 编译及打包成功；1145 项 JVM 测试中 1144 通过、0 失败、1 项在线 DNS 检查跳过，39 组浏览器回归通过。实际 admin 脚本的 SHA-256 为 `1a34a07131e3980a4febba93404e38ce3db40a4e045c82e2e328c7d1f1fe64d9`。首次测试中模拟服务缺少登录后 `/ui/profile` 路由，导致刷新测试被拦截；补齐该测试路由后通过，并未将此测试夹具问题当作产品故障。

新包为 `out/release-20261024-login-session/SterSU_3.3.2_33200-login-session-fix.apk`（18,624,453 字节，SHA-256 `f6cd440416be372d054fd5c1ed028289af0080288e6f9e5970d837cb909ceddd`），沿用已有调试证书的 Release 构建。签名及 16KiB 对齐通过，包内登录 JS 与源码逐一核对一致，DEX 中含原生会话读取代码。未自动安装到手机、未代用户完成真实 QQ／其他方式授权，实机验收仍需用户使用新包的默认“网页登录”入口；右上角浏览器入口仅作备用。

## 来源与许可

主题评论区使用 [Waline](https://github.com/walinejs/waline) 开源评论系统。管理器内的 Android WebView、主题讨论标识、明暗主题／壁纸同步、邮箱登录注册及资料编辑由 SterSU 接入；它们与下列未修改的官方客户端资源分开维护。当前账号入口位于「商店 → 我的」，只提供邮箱登录和注册；前文带日期的小节保留历史排障记录，不代表仍提供第三方登录入口。

随包分发的 `@waline/client` 3.16.1 遵循 MIT 许可，原版权声明为 `Copyright (C) 2021 - PRESENT by Mr.Hope<mister-hope@outlook.com>`，完整许可和客户端署名均予以保留。此处的 MIT 仅指客户端资源，不改变 SterSU 或 Waline 服务端的许可。评论及账号数据由配置的独立服务处理，SterSU 与 Waline 无官方隶属或背书关系。仓库首页和 `THIRD_PARTY_NOTICES.md` 同步提供本声明。

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

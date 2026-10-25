# CofferTunnel

> **Plugin ID**: `tech.coffers.tunnel`  
> **作者**: coffersTech  
> **目标平台**: IntelliJ IDEA 2023.1 ~ 2024.1+ (全线兼容 Community 社区版与 Ultimate 专业版)  
> **技术栈**: Kotlin, Gradle IntelliJ Plugin, Swing, IntelliJ Platform API  

---

## 一、核心定位与业务原理

CofferTunnel 是面向后端开发者的 SSH 本地端口转发隧道管理工具，专为跳板访问内网数据库（PostgreSQL、MySQL、Redis、MongoDB）及微服务设计。

### 核心工作原理
- **系统级端口转发**：插件不重新造轮子实现私有 SSH 协议，也不调用 IDEA 内部私有 API；
- **底层托管 OpenSSH**：底层通过 `ProcessBuilder` 调度操作系统的原生 OpenSSH 客户端，动态组装跳板档案与隧道映射：
  ```bash
  ssh -N -L {localPort}:{remoteHost}:{remotePort} -p {sshPort} -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -i {privateKey} user@jumpHost
  ```
- **系统级监听**：端口监听由操作系统的 OpenSSH 在本机网络栈建立，本机启动的 SpringBoot 应用、终端、Navicat、DBeaver 等所有工具均可直接连接 `127.0.0.1:本地端口`；
- **全生命周期托管**：插件全局服务持有进程句柄，实时收集 stderr/stdout 错误并精准展示，IDE 关闭时自动回收所有子进程，杜绝僵尸进程。

---

## 二、架构演进：跳板档案 (SshProfile) 与 隧道 (Tunnel) 解耦复用

为了解决多条隧道连接相同跳板机时重复配置主机、凭据的问题，插件采用了**跳板档案与隧道解耦架构**：

1. **独立跳板机档案 (SshProfile)**：
   - 集中维护跳板机的核心属性：档案名称、跳板 Host、SSH 端口、用户名、私钥文件路径、额外 SSH 参数；
   - 点击工具栏【跳板档案】按钮即可唤起集中管理面板，支持增删改查与毫秒级网络连通性测试。
2. **轻量化隧道配置 (TunnelConfig)**：
   - 隧道仅需关联指定的【跳板档案】，搭配目标类型（MySQL、PG、Redis 等）、本地监听端口（支持冲突检测）与远端地址；
   - 一套跳板档案可同时供数十条内网服务隧道复用。
3. **安全与变更约束**：
   - **防误删保护**：删除跳板档案时严格校验引用，若有隧道正在绑定该跳板，系统自动阻断并明确指出引用该档案的隧道列表；
   - **防抖运行保障**：修改跳板档案时，不会强行杀死正在运行的隧道进程，并给出手动重启提示；
   - **动态参数装配**：启动子进程时，动态读取跳板档案参数与隧道专属参数，智能拼接 `ssh -N -L` 命令。
4. **全局兼容开关**：
   - 工具栏支持一键切换「启用跳板档案复用模式」；关闭时无缝回退为旧版自包含独立跳板模式。
5. **完整导入与导出**：
   - 导出 JSON 文件同时完整包含 `profiles` 与 `tunnels`，导入时智能兼容新老版本格式，保持引用关系不丢失。

---

## 三、代码分层架构

```
tech.coffers.tunnel
├── config                      // 配置持久化模块
│   ├── CofferTunnelSettingsState.kt   // 持久化状态载体 (useProfileMode, profiles, tunnels)
│   └── CofferTunnelSettingsService.kt // PersistentStateComponent 应用级服务 (带删除引用约束)
├── model                       // 领域模型
│   ├── SshProfile.kt           // SSH 跳板机档案实体
│   ├── TunnelConfig.kt         // 隧道配置实体 (支持 profileId 绑定与自包含模式)
│   └── TunnelStatus.kt         // 运行状态枚举 (已停止/启动中/运行中/异常)
├── service                     // 核心进程管理与工具服务
│   ├── SshDetector.kt          // 跨平台 OpenSSH 路径探测 (macOS/Linux/Windows)
│   ├── TunnelPortValidator.kt  // 本地端口冲突与占用检测
│   ├── TunnelConnectivityTester.kt // 跳板机网络连通性测试服务
│   ├── TunnelProcessService.kt // 核心服务：动态读取 Profile 启动子进程与流分发
│   └── TunnelAppLifecycleListener.kt // IDE 退出事件监听（安全回收子进程）
└── ui                          // 原生界面层 (Swing / IntelliJ Platform UI)
    ├── SshProfileDialog.kt     // 跳板档案新建/编辑弹窗 (含连通性测试)
    ├── SshProfileManagerDialog.kt // 跳板档案集中管理弹窗 (防误删引用校验)
    ├── TunnelDialog.kt         // 隧道表单弹窗 (跳板下拉复选 + 快捷新建 + 端口冲突检测)
    ├── TunnelListCellRenderer.kt // 卡片式列表项渲染器 (呈现 Profile 关联名称与状态)
    ├── TunnelToolWindowPanel.kt// ToolWindow 主面板 (跳板管理按钮 + 全局模式开关 + 列表 + 控制台)
    ├── TunnelToolWindowFactory.kt // ToolWindow 工厂注册
    └── TunnelExportImportHelper.kt// JSON 备份与导入导出助手 (兼容 v2.0 与 v1.0)
```

---

## 四、快速构建与调试

### 方式一：在 IntelliJ IDEA 界面内一键构建（推荐）
1. 打开 IDEA 设置（`Cmd + ,`）-> `Build Tools` -> `Gradle`，确保 **Gradle JVM** 选择的是 `Project SDK (JDK 17)`；
2. 在 IDEA 右侧侧边栏打开 🐘 **Gradle** 面板；
3. 展开 `CofferTunnel -> Tasks -> intellij`，双击 **`buildPlugin`**；
4. 插件离线安装包将生成至：`build/distributions/CofferTunnel-1.0.0.zip`。

### 方式二：在 IDEA 沙盒中实时热启动调试
在 Gradle 面板中双击 `intellij -> runIde`，即可启动独立的沙盒 IDEA 实例，在右侧边栏即时体验 **CofferTunnel**。

# 战区修改器 - 完整项目

## 项目结构

```
WarZoneChanger/
├── app/                          # Android客户端
│   ├── build.gradle
│   ├── libs/                     # 本地依赖
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/warzone/changer/
│       │   ├── App.kt                           # Application
│       │   ├── ui/
│       │   │   ├── MainActivity.kt              # 主界面
│       │   │   ├── LoginActivity.kt             # 卡密激活页
│       │   │   └── LocationPickerActivity.kt    # 战区选择器
│       │   ├── service/
│       │   │   └── VpnProxyService.kt           # VPN代理服务
│       │   ├── injector/
│       │   │   └── LocationInjector.kt          # ★核心：请求拦截&响应修改
│       │   ├── data/
│       │   │   ├── LocationStore.kt             # 位置存储
│       │   │   └── DeviceStore.kt               # 设备标识
│       │   └── utils/
│       │       └── ApiClient.kt                 # 服务端通信
│       └── res/                                 # 布局/样式/图标
│
├── server/                       # 服务端（PHP，部署到域名）
│   ├── .htaccess                 # URL重写
│   ├── admin.php                 # ★管理后台（卡密/公告/设备/日志）
│   ├── api/
│   │   ├── config.php            # 配置文件
│   │   └── index.php             # ★客户端API接口
│   └── data/                     # JSON数据（自动生成）
│       ├── cards.json
│       ├── devices.json
│       ├── announcements.json
│       └── logs.json
│
├── build.gradle                  # 顶层Gradle
└── settings.gradle               # Gradle设置
```

## 核心原理

### Android端
1. **VPN代理**：通过 NetBare 库创建本地VPN，拦截所有网络流量
2. **请求嗅探**：只拦截发往 `apis.map.qq.com/ws/geocoder/v1` 的 GET 请求
3. **请求注入**：修改请求URL中的 `location` 参数为用户选择的虚拟坐标
4. **响应注入**：修改返回JSON中的 `ad_info`、`address_component`、`formatted_addresses`、`location` 等字段
5. **编解码处理**：自动处理 gzip/deflate 压缩的请求和响应

### 服务端
1. **卡密系统**：支持天卡/周卡/月卡/年卡/永久卡，一设备一卡密
2. **设备绑定**：卡密绑定设备ID，不可多设备共用
3. **公告管理**：客户端启动时拉取公告展示
4. **心跳保活**：客户端定期心跳，检测授权状态

## 部署步骤

### 1. 服务端部署（你的虚拟主机）

```bash
# 1. 将 server/ 目录上传到 https://lnzdy.xf79.cn/

# 2. 修改配置 server/api/config.php
#    - ADMIN_USER / ADMIN_PASS：管理后台账号密码
#    - APP_SECRET：通信密钥（必须与Android端一致）

# 3. 确保 data/ 目录可写 (chmod 755)

# 4. 访问 https://lnzdy.xf79.cn/admin.php 登录管理后台

# 5. 生成卡密 → 复制卡密给用户
```

### 2. Android端编译

```bash
# 1. 用 Android Studio 打开 WarZoneChanger/ 项目

# 2. 修改 App.kt 中的 API_BASE 为你的域名
#    const val API_BASE = "https://lnzdy.xf79.cn/api"

# 3. 修改 ApiClient.kt 中的 APP_SECRET 与服务端一致

# 4. 编译运行
```

## API接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/index.php?action=verify` | POST | 验证卡密激活设备 |
| `/api/index.php?action=heartbeat` | POST | 心跳保活 |
| `/api/index.php?action=announcements` | GET | 获取公告 |
| `/api/index.php?action=check` | GET/POST | 检查授权状态 |

### verify 请求参数
```
card_key=WZXXXXXXXX-XXXXXXXX
device_id=设备唯一标识
sign=md5(card_key + device_id + APP_SECRET)
```

## 管理后台功能

- **仪表盘**：总卡密数、已用/未用/已禁用统计、在线设备数
- **卡密管理**：批量生成卡密、查看所有卡密、禁用卡密
- **公告管理**：添加/编辑/删除公告（客户端启动时展示）
- **设备管理**：查看所有授权设备
- **操作日志**：查看所有操作记录

## 注意事项

1. 服务端 `data/` 目录需要 PHP 有写入权限
2. 管理后台默认账号 admin/admin123，请务必修改
3. Android端需要用户授权VPN权限
4. 战区数据文件 `战区.json` 从 sdcard 根目录读取，需提前放置
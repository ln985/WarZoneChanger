# WarZoneChanger - 战区修改器

## 原理

通过本地VPN代理拦截王者荣耀对腾讯地图API的HTTP明文请求，采用**静态重发**模式直接返回修改后的假响应。

### 核心流程
```
王者荣耀 → http://apis.map.qq.com/ws/geocoder/v1 (HTTP明文)
         ↓
VPN本地拦截器匹配目标URL
         ↓
直接返回构造的假响应（adcode = 目标区域编码）
         ↓
王者荣耀读取修改后的adcode → 设置战区
```

### 关键点
1. 王者荣耀走HTTP明文（80端口），不需要证书
2. 静态重发：拦截后直接返回假响应，不转发到真实服务器
3. 修改字段：`result.ad_info.adcode`

## 使用方法
1. 安装APK
2. 点击"选择战区"选择目标区域
3. 点击"开始修改"启动VPN代理
4. 打开王者荣耀，战区已修改

## 编译
```bash
# Push到GitHub后通过Actions自动编译
# 或本地编译：
./gradlew assembleDebug
```

## 项目结构
```
WarZoneChanger/
├── app/
│   ├── src/main/java/com/warzone/changer/
│   │   ├── App.kt                    # Application
│   │   ├── injector/
│   │   │   └── LocationInjector.kt   # 核心：拦截器，构造假响应
│   │   ├── service/
│   │   │   └── VpnProxyService.kt    # VPN服务
│   │   ├── data/
│   │   │   ├── LocationStore.kt      # 位置存储
│   │   │   └── DeviceStore.kt        # 设备标识
│   │   └── ui/
│   │       ├── MainActivity.kt       # 主界面
│   │       └── LocationPickerActivity.kt # 战区选择
│   └── src/main/
│       ├── assets/warzone.json       # 战区数据
│       └── res/
├── .github/workflows/build.yml       # CI自动编译
└── server/                           # 服务端（卡密验证）
```
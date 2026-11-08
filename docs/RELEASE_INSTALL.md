# alpha.42 安装与升级

这是 Create: Sentry Mechanical Arm 的非官方修复开发版本，建议作为 Alpha 测试版使用。完整玩法、配置与兼容边界见 [MOD_GUIDE.md](MOD_GUIDE.md)。

## 环境

- Minecraft 1.21.1，Java 21，NeoForge 21.1.228 或以上。
- Create **6.0.10**、官方 TaCZ **1.2.0**。当前元数据固定这两个版本，不保证其他版本可运行。
- JEI 可选；可使用 19.70.0.469。
- Sable／Create: Aeronautics 可选；兼容验证环境为 Sable 2.0.3、Aeronautics 1.3.2。Sable Companion 1.6.0 已内嵌，无须另装该库。

将本模组 JAR 放入客户端和服务端各自的 mods 目录，并一起更新。移除旧版同模组 JAR，避免同时加载两份。发行包不包含 Create、TaCZ、测试枪包或测试世界。

**Create JEI Compat 1.0.3 只放客户端。** 该版本在独立服务端会因加载客户端 Screen 类而失败；它不是本模组前置，不影响无 JEI 服务端运行。

## 升级

升级前保存世界和配置备份。网络协议为 15，客户端与服务端使用同一发行版本。

alpha.30 起移除底火壳、底火和小火药，不提供这些旧库存的转换。旧再装填半成品可单独合成取回空壳。现行工艺为新壳制造、流态火药装药和单发再装填。

启动级配置 config/sentrymechanicalarm-startup.toml 的 casings.enabled 默认为 true。设为 false 会直接取消弹壳、半成品和模具注册，需要重启并保持两端一致；旧库存不能在取消注册期间保留。仅想停止射后产物时应使用世界 serverconfig 中的运行开关。

## 当前发布范围

当前仍为 Alpha 版本，双独立客户端和长期运行尚未完成稳定版验收。VS2 未安装实测，复杂枪包可通过数据包明确制造与抛壳规则。不要把自动识别理解为任意枪包无条件适配。

发布包附 MIT LICENSE。

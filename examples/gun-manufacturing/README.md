# 枪械制造数据包示例

将此目录放入世界 datapacks 并启用，执行 /reload。AK47 示例沿用自动组件规则，将枪管改为强化、握持改为基础，枪机数量改为 2、复进为 1、控制为 0；其余组件与原方特殊材料照常保留，成品沿用原方的枪械及配件数据。

examples/ 下的文件不自动生效。disable-gun.json 放入 data/<命名空间>/sentry_guns/<枪械路径>.json 可停用单把枪的生成路线；spring-part.json 放入 data/sentrymechanicalarm/gun_parts/gun_spring.json 可修改弹簧的材料与产量。

precision-barrel-part.json 放入 data/sentrymechanicalarm/gun_parts/precision_barrel_component.json，示范完整工序覆盖：先安装黄铜板，再切削、安装连接件、压制。共用毛坯的不同路线必须使用不同第一步，不能只改投入数量。

mixed-tier-gun.json 放入 data/<命名空间>/sentry_guns/<枪械路径>.json，示范混合等级与数量。component_tiers 覆盖单类等级，component_counts 覆盖单类数量，0 可省去后续组件，receiver 固定为 1。省略的类保留自动规则。可用 tier 统一覆盖等级，再用 component_tiers 单独调整。完整 materials／steps 不与 component_counts 混用，避免互相覆盖；追加材料使用 extra_materials。

data/tacz/sentry_guns/ak47.json 示例与 mixed-tier-gun.json 相同。机匣右键选择目标后进入序列装配。完整工序使用 steps，不再支持 pattern/key。

完整入口与字段见 [自定义制造说明](../../docs/CUSTOM_MANUFACTURING.md)。模板不会自动安装进世界。

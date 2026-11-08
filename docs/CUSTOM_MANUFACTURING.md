# 自定义制造与抛壳

## 文件入口

统一文件：data/弹药命名空间/sentry_manufacturing/弹药路径.json。例如 tacz:9mm 对应 data/tacz/sentry_manufacturing/9mm.json。文件由服务端数据包加载，修改后 /reload；同一路径按原版数据包优先级整份覆盖，不跨包合并字段。

根字段 enabled 可明确覆盖原来的弹壳自动识别及旧 sentry_casings 允许规则；自动不识别的弹药如需本模组弹壳与制造功能，应显式设为 true。根字段省略则保留原来的弹壳适用范围。

三个独立段为 reloading、forming、drops。省略某段表示该段继续使用旧入口或自动规则；该段 enabled:false 表示明确停用，不会自动补回。根 enabled:false 停用该弹药的本模组简化制造与抛壳规则。

完整示例：

```json
{
  "enabled": true,
  "reloading": {
    "materials": [],
    "propellant": 40
  },
  "forming": {
    "input": {"item": "minecraft:copper_ingot"},
    "output_count": 5
  },
  "drops": {
    "casing": "tacz:9mm",
    "count": 1,
    "chance": 1.0
  }
}
```

## 优先级

制造配方来源按以下顺序：

1. 数据包提供的完整原生 Create 配方。制壳使用 sentrymechanicalarm:casing_forming/弹药命名空间/弹药路径；再装填支持稳定 ID sentrymechanicalarm:casing_reloading/弹药命名空间/弹药路径，亦保留已加载的版本化 ID。稳定 ID 对应 recipe/ JSON 即使因条件禁用或解析失败而未加载，也不会用自动配方补回。
2. sentry_manufacturing 统一文件中明确提供的对应段。
3. 原来的 sentry_reloading 再装填文件；弹壳适用范围也兼容旧 sentry_casings enabled 规则。
4. 原方成本自动推导。
5. 制壳无可识别金属来源时的通用铜壳规则。

完整原生配方和统一段都属于自定义；完整原生配方表达能力最高，因此排在前面。不想保留完整原生替换时，应从数据包移除对应 recipe/ 文件，再使用简化段。根 enabled 管理本模组的简化规则，不删除另外编写的完整原生配方。

统一段存在但写错时，记录具体资源位置并停用该段，不退回便宜的自动配方。根文件格式或字段有误时，停用该文件管理的简化规则。未知字段会报错，以免拼写错误被静默忽略。

自定义数量不经过自动材料折扣、制壳预算重算或自动装药换算。自定义再装填仍消耗一壳并产一发同口径原枪包弹药。

## 再装填

reloading 沿用旧入口的 materials 与 propellant：

- materials 为数组，每项使用 ingredient（原版/NeoForge Ingredient JSON）与 count。count 默认为 1，材料单位总量最多 1024。
- propellant 是直接消耗的流态火药 mB 整数，默认为 0，范围 0–16000；超过 1000 mB 分次注液。
- 至少声明一种材料或正数装药，不能是零费用空配方。
- 注液 → 按声明顺序加料 → 冲压，最多 16 道工序。仅相邻相同的普通材料合并投放，数量不变。

例如额外加入一粒铜、40 mB 流体：

```json
{"reloading":{"materials":[{"ingredient":{"item":"create:copper_nugget"},"count":1}],"propellant":40}}
```

自动推导开关关闭时，显式规则仍生效；再装填功能总开关关闭时，本模组生成的显式规则也关闭。

## 制壳

forming.input 为 Ingredient JSON，可使用具体物品、标签或匹配组件的原料；每次固定消耗一件输入，保留模具，不支持 input.count 或其他多件基础输入写法。

forming.output_count 是本口径弹壳固定产量，范围 1–64，默认 1。不会因为自动成本计算而修改。input 必须能解析到非空物品；带数量语义的输入不能冒充单件原料。

forming.enabled:false 可实现只回收、不制造新壳。制壳总开关和启动注册开关仍保留。

## 抛壳类型、数量与概率

同一统一文件的 drops 段控制每次经过有限弹药消耗确认的射击事件：

- enabled:false 或 count:0：关闭该弹药的抛壳。
- casing：本模组弹壳对应的弹药 ID，默认原弹药 ID。可改成另一个已加载口径。目标若被统一规则根 enabled:false 明确禁用，则拒绝该映射。
- item：标准 ItemStack JSON，可设置任意已注册物品及其 components。item 与 casing 二选一；本模组带口径弹壳使用 casing 字段。
- count：固定数量 0–1024。默认使用 item 中的数量（未指定则 1）；casing 默认为 1。
- chance：0–1 的概率，省略时使用服务端 casings.dropChance。明确指定时覆盖该默认概率。

普通物品示例：

```json
{"drops":{"item":{"id":"minecraft:iron_nugget"},"count":2,"chance":0.5}}
```

显式 drops 可覆盖自动排除结果，用于特殊弹药抛出自定义物品。若映射到另一个本模组口径弹壳，会将该目标变体加入服务端认可的弹壳集合并同步给客户端。映射只改变抛出的物品，不自动替换原口径再装填配方的输入；共享壳体的多种输出加工路线需另行明确设计，避免多个配方争抢同一输入。

数量超过物品堆叠上限时拆成多个掉落实体，均使用原有寿命设置。自定义物品实体带本模组标记，与普通弹壳一起计入区块实体上限；达到上限时可能少于配置数量。

启动注册总开关、抛壳运行总开关、玩家/动力臂/其他生物开关，以及创造模式、无限弹药和真实消耗限制仍保留。自定义不能绕过这些限制。数据包规则由服务端执行，自定义物品组件通过原生物品实体同步。

## 重载与在制品

统一规则跟随服务端数据资源重载，删除文件后恢复低优先级来源；旧开关保持兼容。简化再装填规则依然自动生成成本指纹，成本改变后旧在制品可单独合成取回壳再加工，已投入材料不退还。完整原生 Create 配方沿用其自身 ID 与在制品行为，作者更改成本时应同步管理旧在制品。

## 示例数据包

[examples/manufacturing-rules](../examples/manufacturing-rules) 中只有 data/tacz/sentry_manufacturing/9mm.json 默认生效；examples/ 下的文件供复制参考。数据包需要自行安装到世界 datapacks 目录。

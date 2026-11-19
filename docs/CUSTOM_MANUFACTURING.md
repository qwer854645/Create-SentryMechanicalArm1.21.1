# 自定义制造与抛壳

## 配件制造

配件使用独立的选型列表，复用枪械序列的目标身份、工序指纹、未加工退回与机械手复制机制。手持配件基座右键选择；基座与机匣不能交叉进入彼此的总装路线。JEI 根据目标身份区分两类待装配件。

```toml
[attachmentManufacturing]
enabled = true
autoFromTaCZ = true
removeNativeAttachmentRecipes = false
```

修改后服务端 `/reload`。enabled 关闭配件专用零件和自动／JSON 总装的生成与选型；不取消物品注册。枪械与配件制造只要有一项开启，共用零件仍可生产。autoFromTaCZ 只控制自动路线，显式 JSON 仍可生成。完整原生配方由作者管理。

removeNativeAttachmentRecipes 只移除成功生成并保留了替代序列的原配方，且必须完全匹配替代路线的成品物品、组件数据和产量。生成失败、显式停用、隐藏或内置而未显式启用，以及其他皮肤／变体或不同产量的原配方，不会被误删。关闭后重载恢复当前资源仍提供的原方；同一次资源快照反复重建时保留内部来源，新资源重载后不会复活已删除配方。枪械原方开关与配件原方开关分别生效。

### 共用零件与默认需求

新增三种毛坯：attachment_blank、optical_blank、muzzle_blank。对应三类组件：attachment_base（配件基座）、optics（光学）、muzzle（枪口）。命名空间为 sentrymechanicalarm；每类有基础、precision_、reinforced_ 三种规格，ID 例如 attachment_base_component、precision_optics_component、reinforced_muzzle_component。基座毛坯由黄铜粒安装铁粒并冲压；光学毛坯由玻璃板切削、冲压；枪口毛坯由铜锭安装铁板并冲压。

三档组件从各自毛坯直接加工，首步分别安装连接件、黄铜板、坚固板；后续切削、安装和冲压沿用共用组件工艺。光学组件加入紫水晶碎片，基座和枪口组件加入铁板。零件覆盖仍使用 `data/sentrymechanicalarm/gun_parts/<零件ID>.json`。三种毛坯与九种组件均使用独立原生 16×16 透明贴图，沿用枪械零件的整数像素绘制流程。

每个成品默认消耗一个基座、一个连接件，再按 TaCZ 声明类型加入：

- scope：一个光学组件。
- muzzle：一个枪口组件。
- stock、grip：一个握持组件。
- laser：一个光学组件、一个精密控制组件。
- extended_mag：一个供弹组件、一个弹簧。

基座与其他组件先按上述基础组合初始化，再根据以下功能数据修正。不读取名称或 ID 关键词，不改变配件战斗属性或可安装枪型。

- 瞄具：服务端读取枪包 `display/attachments/` JSON 的 zoom 数组，取最大的有限正数；缺失、非数值或非法倍率不参与判断，缺失时使用基础光学。最大倍率达到 4× 时光学改为精密；达到 8× 时需两个精密光学；达到 12× 时需三个精密光学，基座也改为精密。不把高倍率或 scope 标记直接视为电子瞄具。
- 扩容弹匣：读取 extended_mag_level，默认按 1–3 级处理，越界值钳制到此范围。1 级使用一个基础供弹、一个弹簧；2 级使用两个精密供弹、两个弹簧；3 级使用三个强化供弹、三个弹簧，基座为强化。不按“弹鼓”等名称推断结构。
- 枪口：读取 TaCZ 解析后的 SILENCE 修饰数据。明确启用消音音效，或不含脚本的距离修饰将默认枪声传播距离降低时，使用两个精密枪口组件；其他枪口使用一个基础枪口组件。仅减少火光或烟雾不会被识别为消音器。不执行自定义脚本推断制造成本，脚本特例可显式覆盖。
- 激光器仍使用光学组件和精密控制组件；握把与枪托保持基础握持组合。

功能推导结束后，再应用 tier、component_tiers、component_counts、spring_count、fastener_count；显式数据不会被自动判断改回。

默认只处理具有已加载 TaCZ 原配方、非 hidden 且非 builtin_attachment 的六类外置配件。NONE 不可制造。没有原配方、隐藏或内置的配件，需要显式 JSON 规则启用。

### 配件规则

入口为 `data/<命名空间>/sentry_attachments/<配件路径>.json`，例如配件 ID my_pack:scope 对应 `data/my_pack/sentry_attachments/scope.json`。

配件允许字段：enabled、tier、component_tiers、component_counts、spring_count、fastener_count、input、materials、extra_materials、steps、output。

- tier：统一覆盖基础／精密／强化等级。component_tiers 再覆盖单类等级。
- component_tiers、component_counts：接受现有八类枪械组件及 attachment_base、optics、muzzle。未使用的类数量默认 0，可以通过数量覆盖启用。attachment_base 固定为每件 1，其余数量允许 0–64。
- spring_count、fastener_count：按每件成品覆盖弹簧、连接件数量，整数 0–64，0 表示不需要。连接件默认每件 1，弹簧按扩容等级生成，其他类别默认 0；不与完整 materials／steps 混用。
- input：必须为单个配件基座 Ingredient，可允许多种基座规格，但不能混入机匣或要求多件输入。选择目标时消耗第一个基座。
- materials：完整替换选择之后的整批安装材料；extra_materials 追加整批材料。格式与枪械规则一致。
- steps：完整替换后续整批工序，支持 deploying、cutting、pressing，最多 16 步，不与 materials／extra_materials 混用。
- component_counts 不与 materials／steps 混用。自动推导结束后才应用显式等级和数量，不会被后续判断改回。
- output：标准 1.21 ItemStack 数据，必须为目标 AttachmentId 的配件，整批数量为 1–64；配方产量不受单个物品堆叠上限限制。省略时完整保留所选原方的产量、皮肤、变体和其他组件。修改原方产量时必须同时提供完整 materials 或 steps。
- enabled:false：停止该目标的生成与目标复制，不回退自动路线，也不移除对应原方。

自动路线替换原方普通金属、木材和青金石用料，其他特殊材料按原 Ingredient 与整批数量保留。存在多条原方时，按配方 ID 稳定排序选第一条作为来源；其他成品数据或批量不一致的配方继续保留。单一目标的不同皮肤／变体目前由显式 output 指定，不为所有变体自动复制一套路线。

原方一次产出 N 件时，仍生成一条产出 N 件的批量序列。基础组件、连接件和弹簧成本乘 N，原方特殊材料按整批保留；选择时先消耗一个基座，后续工序再加入 N−1 个基座。因此不会把整批原方材料收取 N 次，也不会只用一个基座制造整批成品。界面摘要显示产量。完整 materials／steps 模式由作者定义全部后续成本，包括批量制造所需的其他基座，不自动追加材料。

完整原生 Create 覆盖的规范 ID 为 `sentrymechanicalarm:gun_manufacturing/attachments/<命名空间>/<配件路径>`。存在该原生资源时，不再补自动或简化 JSON 路线；原生配方的原方移除由数据包作者管理。要在选型界面中出现，入口需为对应自身配方 ID 的 GunAssemblyIngredient，且仅接受配件基座、输出实际配件。

优先级：完整原生 Create 配方覆盖 → 单个配件 JSON → 自动推导。显式规则错误时记录资源位置并停用该目标，不回退自动路线。配方指纹变化后，旧在制品不能串用新路线；未加工的可以退回原基座重新选择。

示例：仅改变瞄具的光学用料，其余沿用默认与原方特殊材料。

```json
{
  "enabled": true,
  "component_tiers": {"optics": "precision"},
  "component_counts": {"optics": 2},
  "extra_materials": [
    {"ingredient": {"item": "create:electron_tube"}, "count": 1}
  ]
}
```

[配件规则模板](../examples/attachment-manufacturing) 不默认修改任何配件。复制到实际配件 ID 的路径后，再启用数据包。

新增“制造枪械配件”中英文思索，关联配件毛坯、组件和通用待加工物品，展示分级组件、基座选型、机械手选型工具及总装流程。当前有效配方存在时优先使用实际配件例子；具体材料始终以当前配方为准。

## 文件入口

统一文件：data/弹药命名空间/sentry_manufacturing/弹药路径.json。例如 tacz:9mm 对应 data/tacz/sentry_manufacturing/9mm.json。文件由服务端数据包加载，修改后 /reload；同一路径按原版数据包优先级整份覆盖，不跨包合并字段。

根字段 enabled 可明确覆盖原来的弹壳自动识别及旧 sentry_casings 允许规则；自动不识别的弹药如需本模组弹壳与制造功能，应显式设为 true。根字段省略则保留原来的弹壳适用范围。

默认识别读取已加载枪械的 ammo 关联和枪械显示配置，不再按名称或 ID 中的 rocket、energy 等关键词排除。枪械必须声明非空的 shell 对象；优先使用其中完整的 model／texture 资源引用，否则回退使用对应弹药显示配置中的完整 shell.model／shell.texture 引用。引用必须为非空、合法的资源 ID；此处读取 JSON 声明，不在服务端加载客户端模型或执行枪包动画脚本，也不以伤害、爆炸效果、射速或枪械类型推断是否有弹壳。

同一种弹药只要被至少一把满足条件的枪械使用，就进入自动弹壳种类及制造范围；射击时仍按当前枪械检查，其他共用该弹药却未声明抛壳的枪械不会自动产出。只有弹药壳体资源、但没有关联抛壳枪械的弹药，不自动生成对应种类。

也可在世界服务器配置中使用 `casings.forceAllAmmo=true`，或填写 `casings.forceAmmoIds=["my_pack:custom_ammo"]`，使全部或指定的已加载弹药绕过默认的枪械抛壳声明与弹壳资源引用判断，实际射击也绕过逐枪自动限制。它们属于自动适用范围的扩展，优先级低于旧 sentry_casings 和统一规则中的显式 enabled；drops 的禁用、类型、数量、概率仍然照常覆盖。显式 enabled:true 或有效且启用的 drops 同样可用于未声明抛壳的脚本枪械／自定义掉落。强制识别不会绕过注册总开关、掉落来源开关或真实弹药消耗条件。修改后 `/reload`。

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

## 枪械零件与总装

枪械生产独立于弹壳规则和弹壳注册开关。毛坯、组件和枪械总装均采用 Create 序列装配，不需要加工图样或蓝图。

世界服务器配置：

```toml
[gunManufacturing]
enabled = true
autoFromTaCZ = true
removeNativeGunRecipes = false
```

`removeNativeGunRecipes=true` 时，生成总装先读取原 TaCZ 枪匠台枪械配方的材料与成品，再从有效配方管理器中移除这些原配方，阻止继续从枪匠台制作枪械；不会移除弹药、配件和 Create 序列配方。第三方枪包同样按输出物类型处理，不按命名空间特判。该开关独立于本模组制造开关，关闭本模组制造功能不会自动恢复原枪械配方；需要将 removeNativeGunRecipes 设回 false 并 `/reload`。恢复以当前有效数据包为准，不会复活数据包已经删除的旧配方。

修改后执行 /reload。关闭 autoFromTaCZ 仍允许显式枪械规则；关闭 enabled 并重载后停止本模组生成的零件和枪械总装。原生数据包自行提供的完整配方由作者管理。

### 通用零件

九种共用毛坯／基础件 ID：barrel_blank、receiver_blank、bolt_blank、gun_spring、gun_fastener、grip_blank、feed_part、trigger_blank、conductive_part。命名空间均为 sentrymechanicalarm。

八类组件：barrel（枪管）、receiver（机匣）、action（枪机）、recoil（复进）、grip（握持）、feed（供弹）、trigger（击发）、control（控制）。每类均有三档，分别为 `<类别>_component`、`precision_<类别>_component`、`reinforced_<类别>_component`。例如 barrel_component、precision_barrel_component、reinforced_barrel_component。旧组件 ID 保留为基础档。

同一种毛坯可直接加工成任意一档，无需先制造低档组件。八类对应起始物依次为 barrel_blank、receiver_blank、bolt_blank、gun_spring、grip_blank、feed_part、trigger_blank、conductive_part。每次产出一个组件；等级只决定制造材料，不修改枪械伤害。

三档的第一步均为机械手安装，但手持材料不同：基础档安装 gun_fastener，精密档安装 create:brass_sheet，强化档安装 create:sturdy_sheet。后续依功能安装铁板、安山合金、弹簧或红石，并进行切削、压制；精密档加入固定件，强化档再加入黄铜与进一步切削，精密／强化控制组件还需要电子管。不同档位的入口不以投入数量区分。

单种零件覆盖入口：`data/sentrymechanicalarm/gun_parts/<零件ID路径>.json`。

```json
{
  "enabled": true,
  "input": {"item": "create:iron_sheet"},
  "materials": [{"ingredient": {"item": "minecraft:iron_nugget"}, "count": 1}],
  "output_count": 8
}
```

上例用于 gun_spring。input 表示每次消耗一件起始物；materials 为额外材料，不包括起始物。省略字段沿用默认值，materials 空数组明确不添加额外材料。output_count 为 1–64，enabled:false 停用该零件生成路线。

`materials` 模式按相邻材料压缩机械手步骤，最后追加一次压制；无材料时只有一次压制。要指定完整加工结构，改用 `steps`，不能同时填写 materials：

```json
{
  "input": {"item": "sentrymechanicalarm:barrel_blank"},
  "steps": [
    {"type": "deploying", "ingredient": {"item": "create:brass_sheet"}},
    {"type": "cutting"},
    {"type": "deploying", "ingredient": {"item": "sentrymechanicalarm:gun_fastener"}},
    {"type": "pressing"}
  ]
}
```

上例覆盖 precision_barrel_component。steps 必须有 1–16 步，严格按顺序执行，不隐式追加工序。type 支持 deploying、cutting、pressing。只有 deploying 接受 ingredient 与 count；count 默认 1、范围 1–64，定量材料必须支持相应堆叠数量。切削每次耗时 60 游戏刻。

**共用起始物时，第一步必须能区分路线。** 切削与压制是不同入口；机械手安装只有手持材料不重叠时才是不同入口。同物品投入 1 个或 2 个、不同的后续步骤、不同的循环次数均不能区分入口。不要依赖机械手过滤器选择序列最终产物，它过滤的是手持物品。

生成零件路线时会检查起始 Ingredient 的候选物品重叠与第一步冲突。显式 JSON 路线优先于默认路线，冲突的默认路线停用；同优先级的冲突路线全部停用并记录日志，不按加载顺序选一条。完整原生配方保持最高优先级，由数据包作者负责其入口与其他加工配方的兼容。

### 手持机匣选择枪型

手持基础、精密或强化机匣组件右键打开选择界面，按名称／ID 搜索并按枪包命名空间筛选，也可开启“仅适配”，只显示配方实际接受当前手持机匣的枪械。左侧列表显示枪包图标并支持滚动条；右侧显示枪械类别、所需机匣等级与后续机械手材料。模型页可左键拖动旋转、滚轮缩放、右键复位，切换枪械时重置视角；简介页显示枪包提供的描述，长文本可滚轮翻阅。枪包没有可用简介时显示提示，不显示缺失的翻译键。后续加工保持材料图标排列，不展开详细步骤。机匣不符合配方时不能确认。

确认后服务端重新检查当前规则、手持物和机匣规格，消耗一个机匣，生成带目标配方身份的待装配件。手中只有一个机匣时直接替换；成组机匣仅减少一个，产物放入背包，背包满时按原版方式掉落。创造模式同样消耗一个。不会提前消耗后续组件。

待装配件尚未执行第一道工序时，潜行右键可取回原机匣，保留其组件数据；开始加工后不能无损退料。配方改变后无法继续的未加工待装配件仍可退回机匣。

### 机械手批量选择目标

机械手以一个已经选择枪型、尚未加工的待装配件为工具，对传送带／置物台上的未选择机匣执行安装配方，将一个合格机匣转为相同目标的待装配件。工具不消耗；每次消耗一个输入机匣。输出记录输入机匣的原始数据，未加工时仍可潜行右键退回。

目标必须存在于当前有效配方中，输入机匣必须符合该目标的规格。已经开始加工的在制品、没有目标的半成品和旧工序指纹的工具均不能用于复制目标。该机械手配方也会出现在 JEI 中。

### 单把枪总装

入口：`data/<枪械命名空间>/sentry_guns/<枪械路径>.json`。

允许字段：enabled、tier、component_tiers、component_counts、input、materials、extra_materials、steps、output。

- tier：basic、precision、reinforced。可选；填写时统一覆盖八类组件的自动等级，不影响自动数量，也不会启用默认数量为 0 的组件。省略时使用下述分组件规则。
- component_tiers：按 barrel、receiver、action、recoil、grip、feed、trigger、control 覆盖等级，优先于 tier 与所有自动判断。
- component_counts：按相同八类名称覆盖数量，省略的类沿用自动数量。后续组件允许整数 0–64，0 表示省去该组件；receiver 只能为 1，因为选择目标时固定消耗一个机匣。不能与完整替换成本的 materials 或 steps 同用，可与 extra_materials 同用。
- input：选择界面实际消耗的机匣 Ingredient，必须只匹配本模组的单个机匣组件；默认使用所选档位的机匣。不能使用空 Ingredient、非机匣物品或要求多件投入的定量 Ingredient；机匣选择固定只消耗一个。原生完整配方中的选择入口也应用同一检查，非法入口不列入选择界面。
- materials：完整替换后续安装材料，每行有 ingredient 与可选 count，默认数量 1、范围 1–1024。不包括已经消耗的机匣。
- extra_materials：追加后续材料。
- steps：完整替换后续工序，不能与 materials／extra_materials 混用。格式与零件的 steps 相同，支持 deploying、pressing、cutting，最多 16 步；完整工序由作者自行包含特殊材料。
- output：标准 1.21 ItemStack JSON；必须为文件目标 GunId 对应的一把枪，省略时保留原枪匠台成品及配件。
- enabled:false：关闭该枪生成路线及其机械手目标复制配方。显式错误不回退自动路线。

默认总装依次安装枪管、枪机、复进组件，压制固定主体，再安装击发、供弹、握持、控制组件及特殊材料，最后压制。只循环一次。机匣已在选择目标时消耗，不重复收费。

自动路线读取已加载的枪匠台枪械配方，替代原方普通金属、木材与青金石成本，保留其他特殊材料的 Ingredient 和数量。不再仅因狙击枪或机枪类别而额外收取精密构件；原方明确需要的精密构件仍作为特殊材料保留。相邻同类材料可压缩为定量机械手投放，工具与容器余料材料保持原方式。超过 16 步时停用并记录日志，不减少成本。

#### 自动组件规则

基础数量为机匣、枪管、枪机、复进、击发、供弹、握持各 1 个，控制为 0。基础等级均为 basic，然后按枪型调整：

- pistol：基础组合。
- smg：枪机、复进为 precision。
- shotgun：枪管为 reinforced。
- rifle：枪管、机匣、枪机、击发为 precision。
- sniper：枪管、枪机、击发为 precision；机匣、复进为 reinforced。
- mg：枪管、机匣、复进、供弹为 reinforced；枪机、击发为 precision；供弹数量为 2。

随后按 TaCZ 枪械数据修正，不读取名称、ID 中的关键词或伤害值：

1. bolt 为 manual_action：复进数量设为 0，枪机设为 precision；不应用后面的连发射速规则。
2. 非手动上膛，且存在 auto 或 burst 模式：击发设为 precision；这些模式中最高声明射速达到 900 RPM 时，枪机设为 precision，复进设为 reinforced、数量为 2。不按半自动理论射速加价。
3. 非无限弹药、reload.type 为 magazine，基础 ammo_amount 达到 60 时，供弹至少 2 个；达到 120 时至少 3 个。不读取扩容配件，不把燃料、背包直读或手动供弹容量当弹匣容量。
4. 任一已声明射击模式具有 charging 数据：控制设为 precision、数量为 1。这是制造玩法规则，不代表自动判定枪械为电子武器；只有 heat 或脚本的枪不会因此增加控制组件。

握持默认保持 basic。精密与强化代表不同加工侧重，不是统一递增的战斗等级。弹药 ID 不直接决定金属、口径或零件档位；特殊枪包可用规则明确覆盖。未知枪械类别仍不自动生成，显式启用时从基础组合开始应用上述数据修正。

全部自动判断结束后，依次应用 tier、component_tiers、component_counts；自定义值不会再被自动规则改回。input 最终决定消耗的机匣。materials／steps 完整替换后续成本时，不暗中追加自动组件或原方特殊材料；extra_materials 仅追加。

不同枪型的输入已经携带各自配方身份，即使第一步都安装同一种枪管，也不会竞争普通机匣。匹配检查目标组件数据，不依赖显示名称。JEI 按目标区分待装配件与工序。

示例混合等级规则：

```json
{"enabled":true,"component_tiers":{"barrel":"reinforced","grip":"basic"},"component_counts":{"action":2,"recoil":1,"control":0}}
```

自动规则或覆盖导致实际成本变化时，配方指纹会变化，旧待装配件不能继续套用新成本；尚未加工的可潜行右键退回原机匣再选择。已经投入的材料不退还。alpha.45 的动力合成 pattern、key、accept_mirrored 字段已移除；请改用等级／材料规则或完整 steps。原生完整 Create 配方覆盖仍拥有最高优先级；仅使用 GunAssemblyIngredient 定义了机匣选择入口的序列会列入选择界面。

### 原生配方覆盖与进度

优先级：完整原生 Create 配方覆盖 → 单个 JSON 规则 → 默认零件或自动枪械路线。

零件的原生覆盖配方 ID 为 `sentrymechanicalarm:gun_manufacturing/parts/<零件路径>`；枪械为 `sentrymechanicalarm:gun_manufacturing/guns/<枪械命名空间>/<枪械路径>`。存在这些资源时，不再生成对应的零件或枪械序列。

零件生成配方的实际 ID 带工序指纹；在制品记录独立配方和进度，避免跨零件串线。修改费用后，旧半成品不能继续套用新费用，已投入材料不退还。枪械序列也使用工序指纹，待装配件必须匹配当前完整配方身份；旧版本的枪械总装半成品不能直接套用新工序。

基础件入口：枪管毛坯、弹簧、连接件、握持件和光学毛坯先由机械手持零件加工夹具处理对应原材料；机匣毛坯由铁板先安装安山合金；供弹件由铁板先安装连接件；击发毛坯由铜板先安装铁粒；导电件由铜板先安装红石。工作件的 JEI 子类型按配方身份区分。

[可用示例数据包](../examples/gun-manufacturing) 需自行安装，不会随模组自动启用。


### 通用原料的序列入口

枪管毛坯、弹簧、连接件、握持件、光学毛坯的默认序列，先由机械手持有 `sentrymechanicalarm:part_forming_jig`（零件加工夹具）处理原材料，再继续原有工序。夹具使用原生机械手的工具不消耗机制，不增加每批零件的材料消耗，也不改变产量。铁板、铁粒、木板、玻璃片等原料不再直接开始切削／冲压序列。夹具在工作台以一个安山合金、两个铁粒和三个铁板合成；图标直接引用 Create 的机械手贴图。

其余同原料路线继续通过首步手持材料区分；不同等级组件使用本模组专用毛坯作为输入。自定义 `steps` 或 `materials` 完整覆盖默认步骤，因此也会替换夹具起始步骤；作者应自行避免入口冲突。旧版这些路线的加工半成品不会匹配更新后的工序指纹，升级前请先完成加工。

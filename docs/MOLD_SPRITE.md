# alpha.32：16×16 平面模具

按用户反馈移除自定义立体几何，改为 minecraft:item/generated 的普通物品贴图。最终 PNG 为 16×16、透明背景、12 色调色板；灰铁框和黄铜凹槽均由像素表现。凹槽底部为与两侧齐平的直边，不再有酒瓶底座式的外凸块。

使用内置 image_gen 生成原创平面像素稿，再用其编辑能力移除槽底凸块；输出按最近邻采样规范化为 16×16、二值透明并收敛调色板。没有复制或嵌入 Create 的贴图。

资源位置：assets/sentrymechanicalarm/textures/item/casing_mold.png；模型仅引用该图，不含自定义 elements。256×256 放大预览仅保存在 outputs 中，不作为游戏资源。

只编译打包，没有运行检查、自动测试或开发客户端。

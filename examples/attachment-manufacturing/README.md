# 配件制造规则模板

本目录没有默认生效的 data/ 规则，不会直接改变任何配件。

1. 查询目标配件的 TaCZ AttachmentId，例如 my_pack:scope。
2. 将 examples/precision-optics.json 复制为 data/my_pack/sentry_attachments/scope.json；命名空间和路径替换为实际 ID。
3. 将包含 pack.mcmeta 和 data/ 的目录放入世界 datapacks 并启用，执行 /reload。

精密光学示例将每个成品的光学组件设为两个精密组件，再为整批追加一个电子管。其他自动组件和原方特殊材料保留。disable-attachment.json 可停用单个目标的自动序列，不删除其原配方。

custom-small-parts.json 示范覆盖供弹组件、弹簧和连接件：每件成品使用两个精密供弹、一个弹簧、不使用连接件。spring_count、fastener_count 均按每件计算，允许 0–64；不得与完整 materials／steps 同用。没有覆盖的字段继续使用倍率、扩容与消音等自动判断结果。

完整字段、批量产量与接管规则见 [自定义制造说明](../../docs/CUSTOM_MANUFACTURING.md#配件制造)。

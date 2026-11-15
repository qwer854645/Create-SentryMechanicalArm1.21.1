"""Author populated Ponder structures and concise bilingual scene captions."""
import gzip
import json
import re
import struct
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/sentrymechanicalarm'
SCENES=ROOT/'src/main/java/euphy/upo/sentrymechanicalarm/ponder/scene'
def string(value):
    data=value.encode('utf-8')
    return struct.pack('>H',len(data))+data
def named(kind,name,payload):return bytes([kind])+string(name)+payload
def integer(value):return struct.pack('>i',value)
def sequence(kind,values):return bytes([kind])+integer(len(values))+b''.join(values)
def ints(values):return sequence(3,[integer(v) for v in values])
def schematic(name,stations):
    # Actual non-air machines establish PonderLevel bounds before any instruction.
    palette=[]
    blocks=[]
    stations=[((x,0,z),'minecraft:andesite',{}) for x in range(9) for z in range(9)]+stations
    for pos,block,properties in stations:
        state=(block,tuple(sorted(properties.items())))
        if state not in palette:palette.append(state)
        payload=named(9,'pos',ints(pos))+named(3,'state',integer(palette.index(state)))
        # Ponder restores its rendered BE list from saved NBT, not lazy instances.
        # Without this tag, animated partials and tank fluids have no renderer.
        if block not in ('minecraft:andesite','minecraft:target','create:andesite_casing'):
            tag=named(8,'id',string({'create:mechanical_saw':'create:saw'}.get(block,block)))
            tag+=b''.join(named(3,axis,integer(value)) for axis,value in zip(('x','y','z'),pos))
            payload+=named(10,'nbt',tag+b'\0')
        blocks.append(payload+b'\0')
    entries=[]
    for block,properties in palette:
        payload=named(8,'Name',string(block))
        if properties:payload+=named(10,'Properties',b''.join(named(8,k,string(v)) for k,v in properties)+b'\0')
        entries.append(payload+b'\0')
    nbt=named(10,'',named(3,'DataVersion',integer(3955))+named(9,'size',ints([9,6,9]))
        +named(9,'palette',sequence(10,entries))+named(9,'blocks',sequence(10,blocks))
        +named(9,'entities',sequence(10,[]))+b'\0')
    (ASSETS/f'ponder/{name}.nbt').write_bytes(gzip.compress(nbt,mtime=0))
def station(x,y,z,name,**properties):return ((x,y,z),name,properties)
def pipe(x,y,z,*connections):
    return station(x,y,z,'create:fluid_pipe',waterlogged='false',
        **{side:str(side in connections).lower() for side in ('down','up','north','south','west','east')})
arm='sentrymechanicalarm:sentry_mechanical_arm'
collector='sentrymechanicalarm:casing_collector'
control='sentrymechanicalarm:blaze_fire_control'
schematic('casing_forming',[station(4,1,4,'create:depot'),station(4,3,4,'create:deployer',facing='down')])
schematic('casing_reloading',[
    station(1,1,2,'create:basin'),station(1,3,2,'create:mechanical_mixer'),station(2,1,5,'create:fluid_tank'),
    station(4,1,3,'create:depot'),station(4,3,3,'create:spout'),
    station(7,1,3,'create:depot'),station(7,3,3,'create:mechanical_press'),
    station(5,1,6,'create:depot'),station(5,3,6,'create:deployer',facing='down'),
    pipe(2,1,2,'west','south'),station(2,1,3,'create:mechanical_pump',facing='south',waterlogged='false'),pipe(2,1,4,'north','south'),
    pipe(2,2,5,'down','up'),pipe(2,3,5,'down','east'),station(3,3,5,'create:mechanical_pump',facing='east',waterlogged='false'),
    pipe(4,3,5,'west','north'),pipe(4,3,4,'south','north')])
schematic('casing_collection',[station(3,1,3,'create:andesite_casing'),station(3,2,3,arm),station(4,2,3,collector),
    station(4,1,3,'minecraft:hopper',facing='south',enabled='true'),station(4,1,4,'minecraft:chest')])
schematic('sentry_depot_supply',[station(3,1,3,arm),station(4,1,3,'tacz:ammo_storage')])
schematic('sentry_targeting',[station(2,1,3,arm),station(5,1,3,control),station(6,1,6,'minecraft:target')])
schematic('gun_components',[
    *(s for x in (2,4,6) for s in (station(x,1,3,'create:depot'),station(x,3,3,'create:deployer',facing='down'))),
    station(4,1,6,'create:mechanical_saw',facing='up'),station(6,1,6,'create:depot'),station(6,3,6,'create:mechanical_press')])
schematic('gun_assembly',[
    *(s for x in (2,4) for s in (station(x,1,3,'create:depot'),station(x,3,3,'create:deployer',facing='down'))),
    station(6,1,3,'create:depot'),station(6,3,3,'create:mechanical_press')])
schematic('gun_target_copying',[station(3,1,4,'create:depot'),station(3,3,4,'create:deployer',facing='down'),station(6,1,4,'create:depot')])
schematic('attachment_assembly',[
    *(station(x,1,z,'create:depot') for x in (2,4,6) for z in (3,6)),
    station(2,3,3,'create:deployer',facing='down'),station(6,3,3,'create:mechanical_press')])

# Preserve the original introduction layout, but preload its final container.
# Replacing an unrelated sentry during playback leaves its old rendered BE behind.
import runpy
codec=runpy.run_path(str(ROOT/'scripts/normalize-ponder-empty-items.py'))
intro=ASSETS/'ponder/introduce.nbt'
root_name,root_tag=codec['read_tag'](gzip.decompress(intro.read_bytes()))
root=root_tag[1]
palette=root['palette'][1][1]
barrel={'Name':(8,'minecraft:barrel'),'Properties':(10,{'facing':(8,'up'),'open':(8,'false')})}
if barrel not in palette:palette.append(barrel)
for block in root['blocks'][1][1]:
    if block['pos'][1][1]==[4,1,2]:
        block['state']=(3,palette.index(barrel))
        block['nbt']=(10,{'id':(8,'minecraft:barrel'),'x':(3,4),'y':(3,1),'z':(3,2)})
intro.write_bytes(gzip.compress(codec['encode'](root_name,root_tag),mtime=0))
obsolete=ASSETS/'ponder/casing_workshop.nbt'
if obsolete.exists():obsolete.unlink()

captions={
 'forming':('casing_forming','使用模具制造弹壳','Making Casings with a Mold',[
 ('右击模具，可以选择要制造的弹壳口径。','Right-click the mold to select a casing caliber.'),
 ('将设好口径的模具交给机械手。','Give the configured mold to a Deployer.'),
 ('机械手使用模具，将金属加工成对应的弹壳。','The Deployer uses the mold to shape metal into matching casings.'),
 ('新制弹壳和回收弹壳都可用于再装填。','Both new and recovered casings can be reloaded.'),
 ('加工不会消耗模具，可以取回并重新选择口径。','The mold is not consumed. Retrieve it to select another caliber.')]),
 'reloading':('casing_reloading','使用弹壳再装填','Reloading Casings',[
 ('使用动力搅拌器，将火药加工成流态火药。','Use a Mechanical Mixer to turn gunpowder into propellant.'),
 ('使用管道，将流态火药送入注液器。','Use pipes to supply the Spout with propellant.'),
 ('注液器会为下方的弹壳装入流态火药。','The Spout fills casings placed beneath it with propellant.'),
 ('部分弹药还需要机械手加入金属或其他材料。','Some ammunition also requires Deployers to add solid ingredients.'),
 ('最后经过冲压，弹壳便被装成一发子弹。','Finally, press the casing to complete a round.'),
 ('装药量较大时，需要经过多次注液。','Larger charges require multiple filling steps.'),
 ('将未完成的弹壳单独放入合成栏，可以取回空壳。','Craft an unfinished casing on its own to recover the empty casing.')]),
 'collecting':('casing_collection','收集弹壳','Collecting Casings',[
 ('将弹壳收集器放在哨戒动力臂旁。','Place a Casing Collector next to a Sentry Mechanical Arm.'),
 ('射击产生的弹壳会直接存入收集器。','Casings produced by firing enter the collector directly.'),
 ('使用漏斗等物流组件，可以取出收集器内的物品。','Use Hoppers or other logistics components to extract the contents.'),
 ('收集器装满后，无法存入的部分会落在地上。','When collectors are full, the remaining items drop onto the ground.'),
 ('在动态结构上，动力臂会使用同一结构内的收集器。','On contraptions, sentries use collectors on their own structure.'),
 ('玩家射击产生的弹壳默认存入背包。','Players collect their casings into their inventory by default.'),
 ('背包放不下的弹壳会在玩家身旁掉落。','Casings that do not fit drop beside the player.'),
 ('默认只回收声明了抛壳的枪械产物，自定义规则可改变掉落物。','By default, only guns declaring shell ejection produce casings. Custom rules can change the drops.')]),
 'depotSupply':('sentry_depot_supply','使用弹药库补给','Supplying Sentries from Ammo Depots',[
 ('为哨戒动力臂装上匹配的弹药盒。','Attach matching Ammo Boxes to the sentry.'),
 ('相邻弹药库中的弹药会补充到弹药盒内。','Matching ammunition from adjacent depots replenishes the boxes.'),
 ('在动态结构上，动力臂也能使用结构内的弹药库。','Sentries can also draw ammunition from depots on their contraption.'),
 ('弹药不足时，可查看动力臂的补给提示。','Check the sentry supply hints if it runs out of ammunition.'),
 ('通过物流持续补充弹药库，就能维持供弹。','Keep the depot stocked through logistics to maintain the supply.')]),
 'targeting':('sentry_targeting','使用瞄准镜指挥','Directing Sentries with a Scope',[
 ('使用扳手连接火控台与哨戒动力臂。','Link the Fire Control and sentry with a Wrench.'),
 ('黑名单指定攻击目标，白名单指定需要保护的目标。','Blacklist selects targets to attack; Whitelist selects targets to protect.'),
 ('手持许可潜行右击，可以将自己加入名单。','Sneak-right-click with the Permit to add yourself to its list.'),
 ('手持瞄准镜潜行右击火控台，即可绑定。','Sneak-right-click a Fire Control with the scope to bind it.'),
 ('使用瞄准镜时，左击实体可指挥动力臂集火。','While using the scope, left-click an entity to focus linked sentries.'),
 ('中键可以标记位置，引导爆炸武器攻击。','Middle-click marks a position for explosive weapons to attack.'),
 ('动力臂仍需有效动力、足够弹药，并处于射程内。','Sentries still require rotational power, ammunition and a target within range.')]),
 'components':('gun_components','加工不同等级的零件','Manufacturing Graded Components',[
 ('先按配方生产毛坯和基础件，再交给机器继续加工。','Produce blanks and basic parts according to their recipes, then process them further.'),
 ('同一种毛坯，安装连接件、黄铜板或坚固板，可以进入不同加工路线。','Apply Fasteners, Brass Sheets or Sturdy Sheets to select different routes from the same blank.'),
 ('基础、精密和强化组件从毛坯分别加工，无需逐级升级。','Basic, Precision and Reinforced Components are made directly from blanks, without upgrading through tiers.'),
 ('沿配方完成后续切削、安装和冲压，便可得到对应组件。','Complete the remaining cutting, deploying and pressing operations to obtain the component.'),
 ('组件等级决定制造用料，不会改变成品枪械的属性。','Component grades determine manufacturing materials, without changing firearm stats.'),
 ('不同组件的后续工序各不相同，可在配方查看器中查看。','Further operations vary by component. Consult the recipe viewer for each route.')]),
 'assembly':('gun_assembly','选择并装配枪械','Selecting and Assembling Firearms',[
 ('手持机匣组件右击，可以选择要装配的枪械。','Right-click with a Receiver Component to select a firearm.'),
 ('机匣规格必须符合配方；开启“仅适配”可筛选当前机匣能制作的枪械。','The receiver must match the recipe. Enable Match to show compatible firearms.'),
 ('确认后消耗一个机匣，得到选定枪型的待装配件。','Confirm to consume one receiver and obtain an unfinished part for the selected firearm.'),
 ('按照目标配方，依次安装所需组件。','Install the required components in the order specified by the selected recipe.'),
 ('零件的等级和数量随枪械而变，不必为所有枪械准备同一套组件。','Component grades and quantities vary by firearm. Different firearms use different combinations.'),
 ('完成配方中的全部工序，再经最后冲压，即可得到枪械。','Complete every operation in the recipe, then press one last time to finish the firearm.'),
 ('尚未加工的待装配件可潜行右击，退回原机匣。','Sneak-right-click an unprocessed assembly to recover its original receiver.')]),
 'copying':('gun_target_copying','批量选择装配目标','Selecting Assembly Targets in Bulk',[
 ('先选择一种枪械，保留一个尚未加工的待装配件。','Select a firearm and keep one unfinished assembly before processing it.'),
 ('将它交给机械手，作为选择同一装配目标的工具。','Give it to a Deployer as a tool for selecting the same assembly target.'),
 ('在下方送入符合目标配方、尚未选择枪型的机匣。','Supply unconfigured receivers matching the target recipe below the Deployer.'),
 ('机械手将机匣设为相同目标，手中的工具不会消耗。','The Deployer selects the same target for the receiver without consuming its held tool.'),
 ('将得到的待装配件送入后续生产线，即可连续制造同一种枪械。','Send the prepared assemblies down the production line to manufacture the same firearm continuously.'),
 ('已开始加工，或配方已经变化的待装配件，不能作为选择工具。','Assemblies already being processed, or belonging to an outdated recipe, cannot be used as selection tools.')])}
captions['attachmentAssembly']=('attachment_assembly','制造枪械配件','Manufacturing Firearm Attachments',[
 ('基座、光学和枪口组件都有基础、精密与强化规格，可从各自毛坯直接加工。','Bases, optical components and muzzle components have Basic, Precision and Reinforced grades, made directly from their blanks.'),
 ('手持配件基座右击，按类别、枪包或名称选择目标配件。','Right-click with an Attachment Base to select a target by category, gunpack or name.'),
 ('确认后得到选定目标的待装配件；尚未加工时，可潜行右击取回原基座。','Confirm to prepare the selected assembly. Sneak-right-click before processing to recover the original base.'),
 ('机械手可重复使用未加工的待装配件，为下方的合格基座选择同一目标。','A Deployer can reuse an unprocessed assembly to select the same target for matching bases below.'),
 ('按目标配方安装组件。瞄具倍率、扩容等级与消音数据会影响默认用料。','Install components according to the target recipe. Magnification, magazine level and silencing data affect default costs.'),
 ('完成配方中的所有工序和特殊材料投入，再进行最后冲压。','Complete all operations and special material additions before the final pressing.'),
 ('批量配方会保留产量，并按整批计入基座和组件；具体用料以当前配方为准。','Batch recipes retain their yield and charge for all bases and components. Consult the current recipe for exact costs.')])
old={
 'sentry_arm_intro.text_2':('手持 TaCZ 枪械，按交互键（默认 O）装入动力臂。','Hold a TaCZ gun and press its Interact key (default O) to equip the sentry.'),
 'sentry_arm_intro.text_3':('右击装入弹药盒，潜行右击取下。','Right-click to attach an Ammo Box. Sneak-right-click to remove it.'),
 'sentry_arm_intro.text_5':('静态动力臂可将击杀战利品和转化的经验存入下方容器。','Stationary sentries can store kill loot and converted experience in the container below.'),
 'sentry_arm_move.text_2':('移动结构上的动力臂会保留装配时的转速。','Sentries retain their rotational speed when assembled into a contraption.'),
 'fire_control.text_6':('使用望远镜时，副手持许可并按中键，可将目标加入名单。','While using a Spyglass, hold the Permit in the offhand and middle-click to record a target.'),
 'fire_control.text_8':('黑名单指定攻击目标，白名单指定需要保护的目标。','Blacklist selects targets to attack; Whitelist selects targets to protect.')}
# Replace only captions authored in the recent update, not the original scene choreography.
for method,(scene_id,zh_title,en_title,texts) in captions.items():
    file=SCENES/('AttachmentManufacturingScenes.java' if method=='attachmentAssembly' else 'FireControlScene.java' if method=='targeting' else 'GunManufacturingScenes.java' if method in ('components','assembly','copying') else 'CasingScenes.java')
    source=file.read_text(encoding='utf-8')
    begin=source.index(f'public static void {method}(')
    end=source.index('scene.markAsFinished();',begin)
    body=source[begin:end]
    body=re.sub(r'"'+scene_id+r'","[^"]+"','"'+scene_id+'","'+zh_title+'"',body)
    index=iter(texts)
    body=re.sub(r'((?:note|targetingNote)\(scene,[^\n]*,")([^"]+)("\);)',
                lambda m:m[1]+next(index)[0]+m[3],body)
    file.write_text(source[:begin]+body+source[end:],encoding='utf-8')
for lang,i in [('zh_cn',0),('en_us',1)]:
    path=ASSETS/f'lang/{lang}.json'
    data=json.loads(path.read_text(encoding='utf-8'))
    for suffix,pair in old.items():data[f'sentrymechanicalarm.ponder.{suffix}']=pair[i]
    for method,(scene_id,zh_title,en_title,texts) in captions.items():
        data[f'sentrymechanicalarm.ponder.{scene_id}.header']=[zh_title,en_title][i]
        for n,pair in enumerate(texts,1):data[f'sentrymechanicalarm.ponder.{scene_id}.text_{n}']=pair[i]
    # Actual Create ItemDescription keys: summary -> condition -> behaviour.
    tips={
      'item.sentrymechanicalarm.empty_casing.description':('可用于再装填同口径弹药。','Used to reload ammunition of the same caliber.'),
      'item.sentrymechanicalarm.reloading_casing.description':('单独合成可取回空壳，已用材料不返还。','Craft alone to recover the casing. Spent ingredients are not returned.'),
      'item.sentrymechanicalarm.casing_mold.description':('用于机械手加工弹壳。','Used by Deployers to form casings.'),
      'item.sentrymechanicalarm.casing_mold.selected':('口径：%s','Caliber: %s'),
      'item.sentrymechanicalarm.casing_mold.unconfigured':('尚未选择口径','No caliber selected'),
      'item.sentrymechanicalarm.casing_mold.tooltip.summary':('让机械手将金属加工成_指定口径的弹壳_。','Allows a Deployer to form metal into _casings of a selected caliber_.'),
      'item.sentrymechanicalarm.casing_mold.tooltip.condition1':('右击时','On R-Click'),
      'item.sentrymechanicalarm.casing_mold.tooltip.behaviour1':('打开_口径选择界面_。','Opens the _caliber selection screen_.'),
      'item.sentrymechanicalarm.casing_mold.tooltip.condition2':('由机械手持有时','When held by a Deployer'),
      'item.sentrymechanicalarm.casing_mold.tooltip.behaviour2':('加工匹配的金属原料。模具_不会消耗_。','Processes matching metal ingredients. The mold _is not consumed_.'),
      'block.sentrymechanicalarm.casing_collector.tooltip.summary':('收集哨戒动力臂产生的_弹壳_，并存入内部库存。','Stores _casings_ produced by Sentry Mechanical Arms.'),
      'block.sentrymechanicalarm.casing_collector.tooltip.condition1':('与动力臂相邻时','When next to a sentry'),
      'block.sentrymechanicalarm.casing_collector.tooltip.behaviour1':('自动接收射后产物。装满后，_剩余物品落地_。','Receives shot byproducts automatically. When full, _remaining items drop_.'),
      'block.sentrymechanicalarm.casing_collector.tooltip.condition2':('在动态结构上时','On a Contraption'),
      'block.sentrymechanicalarm.casing_collector.tooltip.behaviour2':('接收_同一结构_内动力臂的射后产物。','Receives byproducts from sentries on the _same structure_.'),
      'item.sentrymechanicalarm.sentry_scope.tooltip.summary':('为已连接的哨戒动力臂_标记目标_。','_Marks targets_ for linked Sentry Mechanical Arms.'),
      'item.sentrymechanicalarm.sentry_scope.tooltip.condition1':('潜行右击火控台时','On Sneak R-Click on a Fire Control'),
      'item.sentrymechanicalarm.sentry_scope.tooltip.behaviour1':('_绑定_火控台，再次操作可解除绑定。','_Binds_ the controller. Repeat to unbind.'),
      'item.sentrymechanicalarm.sentry_scope.tooltip.condition2':('使用瞄准镜时','While using the scope'),
      'item.sentrymechanicalarm.sentry_scope.tooltip.behaviour2':('左击标记_实体_，中键标记_位置_。位置标记用于引导爆炸武器。','Left-click marks an _entity_; middle-click marks a _position_ for explosive weapons.'),
      'item.sentrymechanicalarm.fire_control_clipboard.tooltip.condition1':('手持右击时','On R-Click'),
      'item.sentrymechanicalarm.fire_control_clipboard.tooltip.behaviour1':('打开_名单界面_。潜行右击将_自己_加入名单。','Opens the _target list_. Sneak-right-click to add _yourself_.'),
      'item.sentrymechanicalarm.fire_control_clipboard.tooltip.behaviour2':('根据名单的_攻击模式_或_保护模式_筛选目标。','Filters targets using the list\'s _attack_ or _protection_ mode.'),
      'screen.sentrymechanicalarm.casing_mold.search':('搜索口径名称或 ID','Search caliber name or ID'),
      'screen.sentrymechanicalarm.casing_mold.empty':('没有可制造的口径','No craftable calibers'),
      'sentry.supply.none':('补给：附近没有弹药库','Supply: no adjacent ammo depot'),
      'sentry.supply.no_box':('补给：请安装匹配的弹药盒','Supply: attach a matching Ammo Box'),
      'sentry.supply.full':('补给：弹药盒已满','Supply: Ammo Boxes full'),
      'sentry.supply.no_stock':('补给：缺少匹配弹药','Supply: matching ammunition unavailable'),
      'sentry.supply.special_only':('补给：仅有特殊弹药','Supply: only special ammunition available'),
      'sentry.supply.supplied':('补给：已补充弹药','Supply: ammunition replenished'),
      'sentry.supply.creative':('补给：无限弹药','Supply: infinite ammunition'),
      'sentry.supply.depot_mounted':('结构运行时自动供弹，拆卸后可打开弹药库。','Supplies ammunition while assembled. Disassemble to open the depot.')}
    for key,pair in tips.items():data[key]=pair[i]
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
# Keep fallback captions in source consistent with localized captions as well.
for filename in ['SentryMechanicalArmScene.java','FireControlScene.java']:
    path=SCENES/filename
    source=path.read_text(encoding='utf-8')
    replacements={
      '手持 TaCZ 枪械，使用交互键（默认 O）装枪；空手右键取下。':old['sentry_arm_intro.text_2'][0],
      '手持匹配弹药盒右键装载，最多两个；潜行右键取下。':old['sentry_arm_intro.text_3'][0],
      '静态炮台可把击杀战利品送入正下方容器，经验转换为附魔之瓶；射后弹壳另用收集器。':old['sentry_arm_intro.text_5'][0],
      '移动炮台沿用装配时的有效转速；没有保存转速时才按结构运动速度推导。':old['sentry_arm_move.text_2'][0],
      '主手使用望远镜、副手拿许可，按中键记录目标；也可右键许可编辑名单。':old['fire_control.text_6'][0],
      '黑名单模式只攻击名单内目标；白名单是安全列表，名单外目标可能被攻击。':old['fire_control.text_8'][0]}
    for before,after in replacements.items():source=source.replace(before,after)
    path.write_text(source,encoding='utf-8')

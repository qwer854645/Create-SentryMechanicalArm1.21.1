"""Author the new empty workshop schematic and bilingual Ponder captions.

No original schematic is modified. Captions follow the order of note calls.
"""
import gzip
import json
import re
import struct
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/sentrymechanicalarm'
def string(value):
    data=value.encode('utf-8')
    return struct.pack('>H',len(data))+data
def named(kind,name,payload):return bytes([kind])+string(name)+payload
def integer(value):return struct.pack('>i',value)
def sequence(kind,values):return bytes([kind])+integer(len(values))+b''.join(values)
def ints(values):return sequence(3,[integer(v) for v in values])
blocks=[named(9,'pos',ints([x,0,z]))+named(3,'state',integer(0))+b'\0'
        for x in range(9) for z in range(9)]
nbt=(named(10,'',named(3,'DataVersion',integer(3955))
     +named(9,'size',ints([9,6,9]))
     +named(9,'palette',sequence(10,[named(8,'Name',string('minecraft:andesite'))+b'\0']))
     +named(9,'blocks',sequence(10,blocks))+named(9,'entities',sequence(10,[]))+b'\0'))
(ASSETS/'ponder/casing_workshop.nbt').write_bytes(gzip.compress(nbt,mtime=0))

english={
 'forming':('casing_forming','Manufacturing Casings',[
  'Right-click with a blank mold to search and select an available caliber. No sample bullet is required.',
  'Give the configured mold to a Deployer. The mold is reusable and takes no damage.',
  'Default 9mm example: one copper ingot makes five casings. Check JEI for each caliber\'s actual metal and yield.',
  'New and recovered casings of the same caliber stack together and use the same reloading process.',
  'Blank or mismatched molds cannot process the input. Retrieve the mold and select another caliber to change production.']),
 'reloading':('casing_reloading','Fluid Propellant and Single-Round Reloading',[
  'Mix one gunpowder into 1000 mB of propellant. No water or heat is required.',
  'Transport propellant through tanks and pipes to a Spout. The processing stations are shown separately here.',
  'One casing makes one round. Default 9mm uses 40 mB; other calibers use rounded proportional native recipe costs.',
  'For calibers requiring solids, add the JEI ingredients with Deployers. Default 9mm can proceed directly to pressing.',
  'Finally, press to obtain the original gunpack ammunition. No primer, small gunpowder or ammo template is required.',
  'Each filling step accepts at most 1000 mB. Larger charges use multiple steps; consult JEI for the complete recipe.',
  'If an old workpiece cannot continue, craft it alone to recover its casing. Spent materials are not refunded.']),
 'collecting':('casing_collection','Collecting Spent Casings',[
  'Place a Casing Collector on any of the six adjacent faces of a stationary sentry. No collection mode is required.',
  'Shot byproducts enter the 27-slot inventory directly. The collector does not vacuum existing dropped items.',
  'Extract the contents with Hoppers or Create logistics. Custom shot byproducts can be collected in the same way.',
  'Without a collector, or when all collectors are full, only the remainder is dropped onto the ground.',
  'Moving sentries use collectors on their own structure, nearest first. Separate Sable structures do not share collectors.',
  'Players collect shot byproducts into their inventory by default. Overflow drops at the player\'s position.',
  'Recovery requires actual finite-ammo consumption. Creative mode and infinite ammunition do not produce free casings.']),
 'depotSupply':('sentry_depot_supply','Automatic Ammo Depot Supply',[
  'Attach compatible Ammo Boxes to the sentry first, up to two. The depot replenishes the boxes.',
  'Stationary sentries draw matching ammunition from adjacent depots. Keep ammunition stocked and supply rotational power.',
  'Create contraptions can use their own mounted inventories and ammo depots. Mounted storage preserves ammunition counts.',
  'Missing boxes, mismatched calibers or empty stock prevent supply. Check the sentry\'s status hints.',
  'Ammo Depots hold live ammunition. Use a Casing Collector for shot byproducts; the containers serve different purposes.']),
 'targeting':('sentry_targeting','Target Lists and Scope Commands',[
  'Link the controller and sentry with a Wrench, then right-click the Permit to edit its target list.',
  'Blacklist is an attack list: attack listed targets only. Whitelist is a safe list: protect listed targets.',
  'Add yourself before using Whitelist: sneak-right-click with the Permit. Use a Spyglass with an offhand Permit and middle-click to record distant targets.',
  'Sneak-right-click a Fire Control with the Sentry Scope to bind it. Repeat on the same controller to unbind.',
  'Right-click to use the scope, then left-click while using it to mark an entity and focus linked sentries.',
  'Middle-click while using the scope to mark a position. Only eligible explosive weapons attack coordinate marks.',
  'Marks cannot bypass range, rotational power or ammunition requirements. Moving fire controls follow the same restrictions.'])}

old={
 'sentry_arm_intro.text_2':('手持 TaCZ 枪械，使用交互键（默认 O）装枪；空手右键取下。','Hold a TaCZ gun and use its Interact key (default O) to equip it. Right-click empty-handed to retrieve it.'),
 'sentry_arm_intro.text_3':('手持匹配弹药盒右键装载，最多两个；潜行右键取下。','Right-click with compatible Ammo Boxes to attach up to two. Sneak-right-click to remove them.'),
 'sentry_arm_intro.text_5':('静态炮台可把击杀战利品送入正下方容器，经验转换为附魔之瓶；射后弹壳另用收集器。','Stationary sentries can send kill loot into the container below and convert XP to bottles. Shot casings use a separate Collector.'),
 'sentry_arm_move.text_2':('移动炮台沿用装配时的有效转速；没有保存转速时才按结构运动速度推导。','Moving sentries retain their captured rotational speed, using movement speed only as a fallback.'),
 'fire_control.text_6':('主手使用望远镜、副手拿许可，按中键记录目标；也可右键许可编辑名单。','Use a Spyglass in the main hand and a Permit in the offhand, then middle-click to record targets. Right-click the Permit to edit.'),
 'fire_control.text_8':('黑名单模式只攻击名单内目标；白名单是安全列表，名单外目标可能被攻击。','Blacklist attacks listed targets only. Whitelist protects listed targets; unlisted targets may be attacked.')}
scenes=ROOT/'src/main/java/euphy/upo/sentrymechanicalarm/ponder/scene'
for lang,language_index in [('zh_cn',0),('en_us',1)]:
    path=ASSETS/f'lang/{lang}.json'
    data=json.loads(path.read_text(encoding='utf-8'))
    for suffix,pair in old.items():data[f'sentrymechanicalarm.ponder.{suffix}']=pair[language_index]
    for method,(scene_id,title,translations) in english.items():
        source=(scenes/('FireControlScene.java' if method=='targeting' else 'CasingScenes.java')).read_text(encoding='utf-8')
        body=source.split(f'public static void {method}(',1)[1].split('scene.markAsFinished();',1)[0]
        texts=re.findall(r'(?:note|targetingNote)\(scene,[^\n]*,"([^"]+)"\);',body)
        chinese_title=re.search(r'(?:start\(builder|scene.title\()[^\n]*?"'+scene_id+r'","([^"]+)"',body).group(1)
        data[f'sentrymechanicalarm.ponder.{scene_id}.header']=chinese_title if lang=='zh_cn' else title
        for index,text in enumerate(texts,1):
            data[f'sentrymechanicalarm.ponder.{scene_id}.text_{index}']=text if lang=='zh_cn' else translations[index-1]
    if lang=='en_us':
        data['item.sentrymechanicalarm.fire_control_clipboard.tooltip.behaviour2']='Connected sentries follow this controller\'s attack-list or safe-list mode.'
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

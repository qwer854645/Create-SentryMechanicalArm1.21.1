"""Inspect the built distributable, independent of the development classpath."""
import hashlib
import json
from pathlib import Path
import struct
import sys
import tomllib
import zipfile

artifact=Path(sys.argv[1])
version=sys.argv[2]
def check(ok,message):
    if not ok:raise AssertionError(message)
    print('ARTIFACT PASS '+message)
def unique_object(pairs):
    result={}
    for key,value in pairs:
        if key in result:raise AssertionError('Duplicate JSON key '+key)
        result[key]=value
    return result
with zipfile.ZipFile(artifact) as jar:
    names=jar.namelist()
    check(jar.testzip() is None,'archive CRC integrity')
    check(len(names)==len(set(names)),'no duplicate archive entries')
    check(not any('/smoke/' in n or n.startswith(('run/','verification/','examples/')) for n in names),'no test classes, worlds or test gunpacks')
    check(not any(n.startswith('assets/create/') for n in names),'Create assets are referenced, not bundled')
    check('LICENSE' in names and b'MIT License' in jar.read('LICENSE'),'MIT license included')
    metadata=tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
    check(metadata['mods'][0]['version']==version,'expanded version '+version)
    requirements={d['modId']:d['versionRange'] for d in metadata['dependencies']['sentrymechanicalarm']}
    check(requirements['minecraft']=='[1.21.1]' and requirements['create']=='[6.0.10]' and requirements['tacz']=='[1.2.0]','target versions fixed correctly')
    jars=[n for n in names if n.endswith('.jar')]
    check(len(jars)==1 and 'sable-companion' in jars[0],'only the declared compatibility library is embedded')
    classes=[n for n in names if n.endswith('.class')]
    check(all(struct.unpack('>H',jar.read(n)[6:8])[0]<=65 for n in classes),'classes target Java 21 or earlier')
    parsed={n:json.loads(jar.read(n),object_pairs_hook=unique_object) for n in names if n.endswith('.json')}
    check(bool(parsed),'all JSON resources parse without duplicate keys')
    zh=parsed['assets/sentrymechanicalarm/lang/zh_cn.json']
    en=parsed['assets/sentrymechanicalarm/lang/en_us.json']
    prefixes=('sentrymechanicalarm.ponder.','item.sentrymechanicalarm.casing_mold.',
              'block.sentrymechanicalarm.casing_collector.','sentry.supply.')
    selected={k for k in set(zh)|set(en) if k.startswith(prefixes)}
    check(all(k in zh and k in en for k in selected),'Ponder and new tooltip translations exist in both languages')
    for scene in ['introduce','supply','movement','fire_control','casing_forming','casing_reloading','casing_collection','sentry_depot_supply','sentry_targeting']:
        check(f'assets/sentrymechanicalarm/ponder/{scene}.nbt' in names,'Ponder structure '+scene)
    check(not any(n.endswith('/'+item+'.json') and '/recipe/' in n for n in names for item in ['primer','primer_cup','small_gunpowder','ammo_template']),'removed production recipes are absent')
    check(not any('jei' in n.lower() for n in jars),'JEI is not embedded')
print('ARTIFACT SHA256 '+hashlib.sha256(artifact.read_bytes()).hexdigest())

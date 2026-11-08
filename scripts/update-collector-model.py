"""Reference just the horizontal funnel's mounting panel, never its intake."""
import json
import sys
import zipfile
from pathlib import Path

root=Path(__file__).resolve().parents[1]
with zipfile.ZipFile(sys.argv[1]) as archive:
    source=json.loads(archive.read('assets/create/models/block/funnel/block_horizontal.json'))
panel=next(e for e in source['elements'] if e.get('name')=='Back')
faces={d:{'texture':'#casing','uv':[0,0,16,16],'cullface':d}
       for d in ['down','north','south','east','west','up']}
model={
 'parent':'minecraft:block/block',
 'textures':{'particle':'create:block/andesite_casing','casing':'create:block/andesite_casing',
             'panel':'create:block/funnel/andesite_funnel',
             'edge':'create:block/funnel/andesite_funnel_unpowered'},
 'elements':[
  {'name':'Andesite casing','from':[0,0,0],'to':[16,16,16],'faces':faces},
  {'name':'Funnel mounting face','from':[2,16,2],'to':[14,17,14],
   'faces':{'up':{'texture':'#panel','uv':panel['faces']['south']['uv']},
            **{d:{'texture':'#edge','uv':[8,2,12,3]} for d in ['north','south','east','west']}}}
 ]}
target=root/'src/main/resources/assets/sentrymechanicalarm/models/block/casing_collector.json'
target.write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')

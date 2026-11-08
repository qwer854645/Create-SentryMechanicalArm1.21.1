"""Derive collector geometry from Create 6.0.10; retain resource references only."""
import copy
import json
import sys
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
with zipfile.ZipFile(sys.argv[1]) as archive:
    source = json.loads(archive.read('assets/create/models/block/funnel/block_horizontal.json'))

# Turn the horizontal funnel's container-facing back upward. Keep its actual
# plates, angled intake, UVs and relative proportions above a half-height casing.
def position(p):
    x, y, z = p
    return [round(2 + .75*x, 5), round(16 + .75*(z-18.1), 5), round(14-.75*y, 5)]

directions = {'north': 'down', 'south': 'up', 'up': 'north',
              'down': 'south', 'east': 'east', 'west': 'west'}
uv_rotations = {'north': 0, 'south': 180, 'up': 0,
                'down': 180, 'east': 90, 'west': 270}
elements = []
body_faces = {d: {'texture': '#casing', 'uv': [0, 0, 16, 8]}
              for d in ('north', 'south', 'east', 'west')}
body_faces['down'] = {'texture': '#casing', 'uv': [0, 0, 16, 16], 'cullface': 'down'}
body_faces['up'] = {'texture': '#casing', 'uv': [0, 0, 16, 16]}
elements.append({'name': 'Andesite casing base', 'from': [0, 0, 0],
                 'to': [16, 8, 16], 'faces': body_faces})
for original in source['elements']:
    element = copy.deepcopy(original)
    start, end = position(element['from']), position(element['to'])
    element['from'] = [min(a, b) for a, b in zip(start, end)]
    element['to'] = [max(a, b) for a, b in zip(start, end)]
    if 'rotation' in element:
        rotation = element['rotation']
        rotation['origin'] = position(rotation['origin'])
        if rotation['axis'] == 'y':
            rotation['axis'], rotation['angle'] = 'z', -rotation['angle']
        elif rotation['axis'] == 'z':
            rotation['axis'] = 'y'
    element['faces'] = {directions[direction]: dict(face,
        rotation=(face.get('rotation', 0)+uv_rotations[direction]) % 360)
        for direction, face in element['faces'].items()}
    elements.append(element)

model = {'parent': 'minecraft:block/block',
         'textures': {'particle': 'create:block/andesite_casing',
                      'casing': 'create:block/andesite_casing',
                      'base': 'create:block/funnel/andesite_funnel',
                      'back': 'create:block/funnel/funnel_back',
                      'block': 'create:block/andesite_block',
                      'direction': 'create:block/funnel/andesite_funnel_pull',
                      'redstone': 'create:block/funnel/andesite_funnel_unpowered'},
         'elements': elements}
target = root/'src/main/resources/assets/sentrymechanicalarm/models/block/casing_collector.json'
target.write_text(json.dumps(model, indent=2)+'\n', encoding='utf-8')

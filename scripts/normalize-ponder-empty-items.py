"""Migrate legacy Ponder empty/TaCZ stacks, retaining layouts and typed NBT."""
import gzip
import io
from pathlib import Path
import struct

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/sentrymechanicalarm/ponder'
FORMATS={1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}
def read_tag(data):
    stream=io.BytesIO(data)
    def number(fmt):return struct.unpack('>'+fmt,stream.read(struct.calcsize('>'+fmt)))[0]
    def string():return stream.read(number('H')).decode('utf-8')
    def payload(kind):
        if kind in FORMATS:return number(FORMATS[kind])
        if kind==8:return string()
        if kind==7:return stream.read(number('i'))
        if kind==9:
            child,count=number('b'),number('i')
            return child,[payload(child) for _ in range(count)]
        if kind==10:
            values={}
            while True:
                child=number('b')
                if not child:return values
                key=string();values[key]=(child,payload(child))
        if kind in (11,12):return [number('i' if kind==11 else 'q') for _ in range(number('i'))]
        raise ValueError(kind)
    kind=number('b');name=string()
    return name,(kind,payload(kind))
def encode(name,tag):
    def string(value):
        data=value.encode('utf-8');return struct.pack('>H',len(data))+data
    def payload(kind,value):
        if kind in FORMATS:return struct.pack('>'+FORMATS[kind],value)
        if kind==8:return string(value)
        if kind==7:return struct.pack('>i',len(value))+value
        if kind==9:
            child,values=value
            return bytes([child])+struct.pack('>i',len(values))+b''.join(payload(child,x) for x in values)
        if kind==10:return b''.join(bytes([t])+string(k)+payload(t,v) for k,(t,v) in value.items())+b'\0'
        if kind in (11,12):return struct.pack('>i',len(value))+b''.join(struct.pack('>i' if kind==11 else '>q',x) for x in value)
        raise ValueError(kind)
    return bytes([tag[0]])+string(name)+payload(*tag)
def normalize(tag):
    kind,value=tag
    if kind==10:
        if value.get('id')==(8,'minecraft:air'):return (10,{}),1
        result={};changes=0
        for key,child in value.items():
            result[key],count=normalize(child);changes+=count
        item_id=value.get('id',(0,''))[1]
        if isinstance(item_id,str) and item_id.startswith('tacz:') and 'Count' in result:
            # 1.21 ItemStack codecs ignore the old Count/tag fields. TaCZ 1.2
            # explicitly reads GunId, AmmoId etc. from minecraft:custom_data.
            count=result.pop('Count')[1]
            if count<=0:return (10,{}),changes+1
            result['count']=(3,count)
            legacy=result.pop('tag',None)
            if legacy is not None:
                components=dict(result.get('components',(10,{}))[1])
                components.setdefault('minecraft:custom_data',legacy)
                result['components']=(10,components)
            changes+=1
        return (10,result),changes
    if kind==9:
        child,values=value;result=[];changes=0
        for item in values:
            normalized,count=normalize((child,item));result.append(normalized[1]);changes+=count
        return (9,(child,result)),changes
    return tag,0
for path in ROOT.glob('*.nbt'):
    name,tag=read_tag(gzip.decompress(path.read_bytes()))
    normalized,changes=normalize(tag)
    if changes:
        path.write_bytes(gzip.compress(encode(name,normalized),mtime=0))
        print(f'Normalized {changes} legacy item stacks in {path.name}')

#!/usr/bin/env python3
"""Reproduce the 30-question demo from a pinned public repository. Requires gh CLI."""
import argparse, concurrent.futures, hashlib, html, json, re, subprocess
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import quote
REPO='ERRRC/kaogongzhentizhengliu'
COMMIT='84ab93d4b64b61d897bece8a1c0a5bab06b4feb2'
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(); parser.add_argument('--cache',type=Path,default=ROOT/'.import-cache');parser.add_argument('--tree',type=Path)
args=parser.parse_args();args.cache.mkdir(parents=True,exist_ok=True)
def gh(endpoint):
 return subprocess.check_output(['gh','api',endpoint,'-H','Accept: application/vnd.github.raw+json'])
def blob(entry):
 p=args.cache/entry['sha']
 if not p.exists(): p.write_bytes(gh(f'repos/{REPO}/git/blobs/{entry["sha"]}'))
 return p.read_bytes()
tree=json.loads(args.tree.read_text() if args.tree else gh(f'repos/{REPO}/git/trees/{COMMIT}?recursive=1'))
assert not tree.get('truncated') and tree['sha']==COMMIT
entries={e['path']:e for e in tree['tree'] if e['type']=='blob'}
image_entries={p.split('/')[-1]:e for p,e in entries.items() if p.startswith('90-图片/')}
assets=set()
class Clean(HTMLParser):
 def __init__(self):super().__init__();self.out=[];self.blocked=0
 def handle_starttag(self,tag,attrs):
  if tag in ('script','style','iframe','object'):self.blocked+=1;return
  if self.blocked:return
  if tag=='img':
   src=dict(attrs).get('src',''); name=src.split('/')[-1]
   if name not in image_entries:raise ValueError('missing image '+src)
   assets.add(name);self.out.append(f'<img src="assets/{html.escape(name,quote=True)}" alt="题目配图，点击放大">')
  elif tag in ('p','br','strong','em','b','i','sub','sup','table','thead','tbody','tr','td','th','ul','ol','li'):self.out.append('<'+tag+'>')
 def handle_endtag(self,tag):
  if tag in ('script','style','iframe','object'):self.blocked=max(0,self.blocked-1);return
  if not self.blocked and tag in ('p','strong','em','b','i','sub','sup','table','thead','tbody','tr','td','th','ul','ol','li'):self.out.append('</'+tag+'>')
 def handle_data(self,data):
  if not self.blocked:self.out.append(html.escape(data))
def clean(s):
 p=Clean();p.feed(s);return ''.join(p.out).strip()
def section(text,name):
 m=re.search(r'^### '+re.escape(name)+r'\s*\n(.*?)(?=^### |\Z)',text,re.M|re.S)
 return m.group(1).strip() if m else ''
def field(text,name):
 m=re.search(r'^'+re.escape(name)+r':\s*"([^"]*)"',text,re.M);return m.group(1) if m else ''
def block(text,name):
 m=re.search(r'^## '+name+r'\s*\n(.*?)(?=^## |^\*\*|^---|\Z)',text,re.M|re.S)
 return m.group(1).strip() if m else ''
def question(entry,category,mid=None):
 text=blob(entry).decode();opts=section(text,'选项')
 pairs=re.findall(r'^- ([A-D])\.\s*(.*?)(?=^- [A-D]\. |\Z)',opts,re.M|re.S)
 correct=[k for k,v in pairs if '✅' in v]
 if len(pairs)!=4 or len(correct)!=1:raise ValueError('ambiguous answer '+entry['path'])
 stem=section(text,'题干');explanation=section(text,'官方解析')
 if not stem or not explanation:raise ValueError('missing question/solution')
 fast=re.search(r'\*\*最快解法\*\*：([^\n]*)',text)
 result={'id':field(text,'qid'),'category':category,'module':'资料分析' if mid else '判断推理','year':field(text,'年份'),'region':field(text,'地区'),'paper':field(text,'试卷'),'source':f'https://github.com/{REPO}/blob/{COMMIT}/'+quote(entry['path']),'sourcePath':entry['path'],'stem':clean(stem),'options':[{'key':k,'html':clean(v.replace('✅','').strip())} for k,v in pairs],'answer':correct[0],'explanation':clean(explanation),'fast':fast.group(1).strip() if fast else '', 'reasoning':block(text,'推理链'),'pitfalls':block(text,'易错点'),'materialId':mid}
 if not result['id']:raise ValueError('missing id')
 return result
questions=[];groups=[];materials=[]
for cat in ['图形推理','定义判断','类比推理','逻辑判断']:
 candidates=[e for p,e in entries.items() if p.startswith('10-真题/判断推理/'+cat+'/') and p.endswith('.md')]
 # Deterministic repository order; prefer recent 2024 examples when available.
 candidates.sort(key=lambda e:(not e['path'].split('/')[-1].startswith('10204'),e['path']))
 selected=[]
 for entry in candidates:
  try:q=question(entry,cat)
  except ValueError as error:print('SKIP',error,flush=True);continue
  selected.append(q)
  if len(selected)==5:break
 assert len(selected)==5
 questions.extend(selected);groups.append({'id':cat,'module':'判断推理','title':cat,'ids':[q['id'] for q in selected]});print(cat,'5 questions',flush=True)
for p,e in entries.items():
 if not p.startswith('15-材料/') or not p.endswith('.md'):continue
 text=blob(e).decode();mid=field(text,'mid')
 links=list(dict.fromkeys(re.findall(r'\[\[(10-真题/[^|\]]+)',text)))
 if len(links)!=5 or not section(text,'材料原文'):continue
 try:
  selected=[question(entries[link+'.md'],'资料分析',mid) for link in links]
  for q in selected:
   raw=blob(entries[q['sourcePath']]).decode()
   if f'资料分析/{mid} ' not in raw:raise ValueError('mismatched material')
  material={'id':mid,'title':field(text,'材料主题'),'html':clean(section(text,'材料原文')),'source':f'https://github.com/{REPO}/blob/{COMMIT}/'+quote(p)}
 except (ValueError,KeyError) as error:print('SKIP material',error,flush=True);continue
 selected.sort(key=lambda q:int(q['id']))
 materials.append(material);questions.extend(selected);groups.append({'id':mid,'module':'资料分析','title':'公共图书馆' if len(materials)==1 else '社会物流','ids':[q['id'] for q in selected]});print('material',mid,'5 questions',flush=True)
 if len(materials)==2:break
assert len(questions)==30 and len({q['id'] for q in questions})==30 and len(materials)==2
# Download only assets referenced by the accepted output.
serialized=json.dumps({'questions':questions,'materials':materials},ensure_ascii=False)
assets=set(re.findall(r'assets/([^"<>\\]+)',serialized))
def download(name):
 data=blob(image_entries[name]);dest=ROOT/'assets'/name;dest.parent.mkdir(exist_ok=True);dest.write_bytes(data)
 assert len(data)>0
 return {'name':name,'sha256':hashlib.sha256(data).hexdigest()}
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:manifest=list(pool.map(download,sorted(assets)))
result={'version':1,'repository':f'https://github.com/{REPO}','commit':COMMIT,'groups':groups,'materials':materials,'questions':questions,'assets':manifest}
(ROOT/'data').mkdir(exist_ok=True);(ROOT/'data/questions.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
(ROOT/'SOURCE-LICENSE.txt').write_bytes(blob(entries['LICENSE']))
print('Imported',len(questions),'questions;',len(materials),'materials;',len(assets),'images.',flush=True)

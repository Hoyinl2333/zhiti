#!/usr/bin/env python3
import hashlib,json,re
from pathlib import Path
root=Path(__file__).resolve().parents[1];d=json.loads((root/'data/questions.json').read_text())
assert d['commit']=='84ab93d4b64b61d897bece8a1c0a5bab06b4feb2'
qs={q['id']:q for q in d['questions']};assert len(qs)==len(d['questions'])==30
assert len(d['groups'])==6 and all(len(g['ids'])==5 for g in d['groups'])
assert set(qs)==set(id for g in d['groups'] for id in g['ids'])
assert len(d['materials'])==2
for q in qs.values():
 assert [o['key'] for o in q['options']]==list('ABCD')
 assert q['answer'] in 'ABCD' and q['stem'] and q['explanation']
 assert '✅' not in q['stem']+str(q['options'])
 assert q['year'] and q['region'] and q['source'].startswith('https://github.com/')
 for text in [q['stem'],q['explanation']]+[o['html'] for o in q['options']]:
  assert not re.search(r'<(?:script|iframe|style)|\bon\w+\s*=|javascript:',text,re.I)
for m in d['materials']:
 assert len([q for q in qs.values() if q['materialId']==m['id']])==5 and m['html']
for a in d['assets']:
 p=root/'assets'/a['name'];assert p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest()==a['sha256']
for name in re.findall(r'assets/([^"<>\\]+)',json.dumps(d,ensure_ascii=False)):
 assert (root/'assets'/name).is_file()
print(f"PASS: 30 questions, 6 groups, 2 complete materials, {len(d['assets'])} verified images; no answer markers in question/options.")

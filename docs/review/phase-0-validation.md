# Phase 0 验证记录

日期：2026-10-20（Asia/Shanghai）。验证范围是文档与仓库治理，不是业务、构建、Docker 或 CI 测试。

## 可复现的文档检查

在仓库根目录将以下代码运行于 `python3 -`，或保存到仓库外临时文件后执行 `python3 <临时文件> <仓库目录>`。检查无第三方依赖，不连接数据库、不启动应用。

```python
from pathlib import Path
import hashlib
import re
import sys
from collections import Counter

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()
required = [
    'AGENTS.md', 'README.md', 'docs/requirements/REQUIREMENTS.md',
    'docs/requirements/TRACEABILITY.md', 'docs/architecture/ARCHITECTURE.md',
    'docs/architecture/DATA_OWNERSHIP.md', 'docs/adr/ADR-001-service-boundary.md',
    'docs/plans/phase-0.md',
]
directories = [
    'docs/requirements', 'docs/architecture', 'docs/adr', 'docs/api',
    'docs/plans', 'docs/review', 'docs/ethics', 'docs/runbooks',
    'contracts/openapi', 'services/platform-service', 'services/reservation-service',
    'services/classroom-service', 'services/maintenance-service', 'apps/web',
    'deploy/compose', 'deploy/nginx', 'deploy/huawei', 'scripts', '.github/workflows',
]
for path in required:
    assert (root / path).is_file() and (root / path).stat().st_size, path
for path in directories:
    assert (root / path).is_dir(), path
print(f'PASS required documents: {len(required)}; directories: {len(directories)}')

files = sorted(p for p in root.rglob('*') if p.is_file() and '.git' not in p.parts)
for p in files:
    content = p.read_bytes()
    text = content.decode('utf-8')
    assert b'\r' not in content, f'CRLF: {p.relative_to(root)}'
    assert not content or content.endswith(b'\n'), f'No final newline: {p}'
    assert all(line == line.rstrip() for line in text.splitlines()), f'Trailing whitespace: {p}'
    if p.suffix == '.md':
        assert len(re.findall(r'^```', text, re.M)) % 2 == 0, f'Unpaired fence: {p}'
        for target in re.findall(r'(?<!!)\[[^\]\n]+\]\(([^)]+)\)', text):
            if re.match(r'^[a-z]+://', target) or target.startswith('#'):
                continue
            assert (p.parent / target.split('#')[0]).exists(), f'Broken link: {p}: {target}'
print(f'PASS UTF-8/LF, whitespace, fences and local links: {len(files)} files')

def rows(path):
    return [line.split('|')[1:-1] for line in (root / path).read_text().splitlines()
            if re.match(r'^\|\s*REQ-[A-Z0-9]+-\d{3}\s*\|', line)]

req = rows('docs/requirements/REQUIREMENTS.md')
trace = rows('docs/requirements/TRACEABILITY.md')
req_ids = [row[0].strip() for row in req]
trace_ids = [row[0].strip() for row in trace]
assert req_ids and len(req_ids) == len(set(req_ids)), 'Missing or duplicate requirement ID'
assert len(trace_ids) == len(set(trace_ids)), 'Duplicate traceability ID'
assert set(req_ids) == set(trace_ids), 'Requirements and traceability differ'
statuses = []
for row in trace:
    cells = [cell.strip() for cell in row]
    states = [cell for cell in cells if cell in {'TODO', 'PARTIAL', 'BLOCKED', 'DONE'}]
    assert len(states) == 1, f'Invalid status: {cells[0]}'
    status = states[0]
    if not cells[0].startswith('REQ-P0-'):
        assert status == 'TODO', f'Future implementation overstated: {cells[0]}'
    assert all(cells), f'Empty traceability cell: {cells[0]}'
    statuses.append(status)
assert len([x for x in req_ids if x.startswith('REQ-P0-')]) == 8
print(f'PASS requirement/traceability bijection: {len(req_ids)}; statuses: {dict(Counter(statuses))}')

for prefix in ['services', 'apps', 'deploy', 'contracts', 'scripts', '.github/workflows']:
    assert all(p.name == '.gitkeep' for p in (root / prefix).rglob('*') if p.is_file()), prefix
for name in ['pom.xml', 'package.json', 'pnpm-workspace.yaml', '.env.example']:
    assert not (root / name).exists(), f'Premature scaffold: {name}'
print('PASS Phase 0 scope: no application, migration, build or workflow implementation')

source = root / 'docs/requirements/CODEX_EXECUTION_PROMPT.md'
digest = hashlib.sha256(source.read_bytes()).hexdigest()
assert digest == 'd48f56b482ba526d80c957128468601592802566dff12aa42c173b832ce5af70'
print(f'PASS original source SHA-256: {digest}')

patterns = [r'gh[pousr]_[A-Za-z0-9]{30,}', r'github_pat_[A-Za-z0-9_]{40,}',
            r'AKIA[0-9A-Z]{16}', r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----']
for p in files:
    for pattern in patterns:
        assert not re.search(pattern, p.read_text()), f'Credential pattern found in {p.relative_to(root)}'
print('PASS known credential pattern scan (limited heuristic, plus manual diff review)')
```

## 执行记录

实际执行的主要命令如下，路径均从仓库根目录理解；临时验证脚本在仓库外 `work/`，内容与上方代码一致，未提交运行时脚本。

| 实际命令/操作 | 结果 |
|---|---|
| `pwd`、`rg --files`、`git status --short --branch` | 确认初始工作目录为空、不是 Git 仓库；无已有业务代码 |
| `cat` / `sed` 阅读原文及适用规则 | 完整读取源文件，Phase 0 范围明确；上级未发现 AGENTS.md |
| `mkdir -p`、`cp`、文件编辑 | 建立文档与占位目录；保留源文件逐字副本 |
| `shasum -a 256`（源文件与归档副本） | 两者均为 `d48f56b482ba526d80c957128468601592802566dff12aa42c173b832ce5af70` |
| `git init -b main`、`git commit --allow-empty`、`git switch -c feature/phase-0-governance` | 本地准备分支；此空提交未作为远端交付历史 |
| `git remote add origin https://github.com/Liinn0529/campus-resource-platform.git`、`git fetch origin` | 首次因网络沙箱失败；获得会话网络权限后重试成功 |
| `git reset --soft origin/main`、`git branch -f main origin/main` | 将本地准备分支对齐 GitHub 自动生成的初始 README 提交，保留全部工作文件 |
| `python3 work/validate_phase0.py outputs/campus-resource-platform`（从上级工作目录执行） | 文档验证退出 0；8 个必需文件、19 个必需目录、30 个文件；186 条 ID 双向一致 |
| `git add .editorconfig .gitattributes .github .gitignore AGENTS.md CONTRIBUTING.md README.md apps contracts deploy docs scripts services` | 仅暂存本项目 Phase 0 文件 |
| `git diff --cached --check`、`git diff --cached --stat` | 退出 0；30 个变更文件，29 新增、1 README 修改 |
| Python 逐项调用 `git check-ignore --quiet --no-index <path>` | 8 个路径符合预期：忽略环境凭据、私钥、依赖、构建目录，保留示例环境文件 |

文档验证实际输出（完成状态更新后复跑）：

```text
PASS required documents: 8; directories: 19
PASS UTF-8/LF, whitespace, fences and local links: 30 files
PASS requirement/traceability bijection: 186; statuses: {'DONE': 8, 'TODO': 178}
PASS Phase 0 scope: no application, migration, build or workflow implementation
PASS original source SHA-256: d48f56b482ba526d80c957128468601592802566dff12aa42c173b832ce5af70
PASS known credential pattern scan (limited heuristic, plus manual diff review)
PASS gitignore behavior: 8 paths
```

gitignore 检查路径为 `.env`、`.env.production`、`services/platform-service/.env`、`private.key`、`node_modules/pkg/index.js`、`services/platform-service/target/build.jar`（应忽略），`.env.example`、`deploy/huawei/env.example`（应保留）。没有为验证生成真实凭据。

## 内容审阅与结论

已按源文档逐节核对需求覆盖，并对照服务边界、数据归属、状态/字段/角色/页面附录。186 条需求在两张表中一一对应；只有 REQ-P0-001..008 为 DONE，其余 178 条为 TODO。关键来源规则含 room_gate 房间锁、占用来源三元唯一键、M2/M3 显式同步状态、只重试网络错误/5xx 且最多 3 次、所有角色和页面、完整测试/部署/伦理要求。

保留 15 项未决问题；不将 DELETE 标识差异、重试口径、NO_SHOW 语义和权限/时限缺省值擅自改成业务规则。ADR 为 Proposed，仍需人工 Review。

原始文件归档与本地源 SHA-256 一致。已查看交付文件内容并进行有限凭据模式扫描；没有真实用户数据、密码、token、私钥或本地个人路径进入版本库。模式扫描仅提供有限辅助证据，不声称完成安全渗透审计。

未执行且不适用本阶段：Maven/pnpm 构建、业务 unit/integration/API/E2E、mutation-style 业务验证、Flyway、Docker、GitHub Actions、部署或回滚。这里的 PASS 仅对应文档和仓库治理检查，不对应这些后续能力。

发布后的仓库、分支、commit、PR 和远端一致性核对记录在最终交付报告；本文件不预填尚未完成的上传结果。

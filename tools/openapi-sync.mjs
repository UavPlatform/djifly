#!/usr/bin/env node
/**
 * 把后端仓导出的 OpenAPI 结构契约（SSOT）vendor 到 djifly 仓，并记录 sha256 供对账。
 *
 * 依据：backend/spec/openapi/drone-backend.openapi.json（结构 SSOT）的 vendored 副本
 *   - 后端仓 spec/openapi/drone-backend.openapi.json 是唯一结构事实源；
 *   - 策略变更（参见 UavPlatform/docs 仓库 ADR）：改为「后端发布 + 机器人自动开 PR」；
 *   - 每端固定一种生成器 + 生成物入库，禁止手改。
 *
 * 用法（在 djifly/ 下执行）：
 *   OPENAPI_SOURCE=/path/to/spec.json node tools/openapi-sync.mjs
 *   # 默认来源 ../backend/spec/openapi/drone-backend.openapi.json（本地调试）
 *   # CI 通过 OPENAPI_SOURCE 环境变量指定下载的契约文件
 *
 * 注：手动同步已废弃，生产环境由 .github/workflows/contract-sync.yml 自动处理。
 * 下一步：node tools/openapi-gen.mjs
 */
import { createHash } from 'node:crypto'
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const vendorDir = resolve(root, 'openapi')
const vendorSpec = resolve(vendorDir, 'drone-backend.openapi.json')
const vendorSha = resolve(vendorDir, 'drone-backend.openapi.sha256')
const defaultSource = resolve(root, '../backend/spec/openapi/drone-backend.openapi.json')

const source = process.env.OPENAPI_SOURCE
  ? resolve(process.env.OPENAPI_SOURCE)
  : defaultSource

if (!existsSync(source)) {
  console.error('[openapi] 找不到来源规格：' + source)
  console.error('[openapi] 可用 OPENAPI_SOURCE=<path> 指定；默认来源：' + defaultSource)
  process.exit(1)
}

mkdirSync(vendorDir, { recursive: true })
copyFileSync(source, vendorSpec)

const bytes = readFileSync(vendorSpec)
const sha256 = createHash('sha256').update(bytes).digest('hex')
writeFileSync(vendorSha, sha256 + '  drone-backend.openapi.json\n')

let operations = 0
try {
  const spec = JSON.parse(bytes.toString('utf8'))
  for (const item of Object.values(spec.paths ?? {})) {
    operations += Object.keys(item).filter((key) => key !== 'parameters').length
  }
} catch (error) {
  console.error('[openapi] vendor 的规格不是合法 JSON：' + error.message)
  process.exit(1)
}

console.log('[openapi] vendor 完成')
console.log('          source  = ' + relative(root, source))
console.log('          spec    = ' + relative(root, vendorSpec))
console.log('          bytes   = ' + bytes.length + '  operations = ' + operations)
console.log('          sha256  = ' + sha256)
console.log('[openapi] 下一步：node tools/openapi-gen.mjs')

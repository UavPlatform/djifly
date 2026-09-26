#!/usr/bin/env node
/**
 * 契约代码生成器（工具，非手写代码）：把 vendored OpenAPI 规格翻译成 Kotlin 契约路径表与模型。
 *
 * 用法（在 djifly/ 下执行）：
 *   node tools/openapi-gen.mjs           # 生成 platform/network/generated/*.kt
 *   node tools/openapi-gen.mjs --check   # 只校验生成物与规格是否同步（漂移则退出码 1）
 *
 * 事实源：openapi/drone-backend.openapi.json（后端仓 spec/openapi/ 导出，sha256 对账）。
 * 生成物必须提交入库且禁止手改；后端契约变更后重跑本脚本并评审 diff。
 */
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const specPath = resolve(root, 'openapi/drone-backend.openapi.json')
const pathsOut = resolve(root, 'app/src/main/java/com/fuwaki/djifly/platform/network/generated/ApiPaths.kt')
const modelsOut = resolve(root, 'app/src/main/java/com/fuwaki/djifly/platform/network/generated/ApiModels.kt')
const generatorName = 'tools/openapi-gen.mjs'
const generateCommand = 'cd djifly && node tools/openapi-gen.mjs'

/**
 * 本端调用的端点清单（消费清单，人工维护，共 16 条）。
 *   method = HTTP 方法；path = Retrofit 注解里的字面量（无前导斜杠，占位符按本端 @Path 命名）。
 * 契约路径通过「占位符结构归一化」匹配：{x} 与 {y} 视为同一模板位置。
 * 新增调用点必须在此登记；门禁测试会核对「登记表 ↔ 契约 ↔ 代码」三者一致。
 * djifly 只消费执飞链路（登录/任务/交付物/直播/设备），聊天、大厅、统计由 app/ 消费。
 */
const calledEndpoints = [
  ['post', 'appUav/add'],
  ['post', 'api/ws/request'],
  ['post', 'user/login'],
  ['post', 'rider/register'],
  ['post', 'user/refresh'],
  ['get', 'rider/my-tasks'],
  ['get', 'rider/my-tasks/history'],
  ['post', 'rider/cancel'],
  ['post', 'rider/complete'],
  ['get', 'rider/task/detail'],
  ['post', 'live/rider/req'],
  ['post', 'tasks/{taskNum}/attachments/upload-url'],
  ['get', 'tasks/{taskNum}/attachments'],
  ['get', 'rider/drone/list'],
  ['post', 'rider/drone/bind'],
  ['delete', 'rider/drone/unbind'],
]

/**
 * 非 Retrofit 调用点引用、但同样属于后端端点的路径（AuthInterceptor 免鉴权名单）。
 * 纳入 ApiPaths 才能彻底消除散落的路径字面量，行为与原先的 "/user/register" 一致。
 */
const referencedEndpoints = [
  ['post', 'user/register'],
]

const endpoints = [...calledEndpoints, ...referencedEndpoints]

const check = process.argv.includes('--check')

if (!existsSync(specPath)) {
  console.error('[openapi] 缺少 vendored 规格 ' + relative(root, specPath))
  console.error('[openapi] 先运行：node tools/openapi-sync.mjs')
  process.exit(1)
}

const specBytes = readFileSync(specPath)
const sha = createHash('sha256').update(specBytes).digest('hex')
let spec
try {
  spec = JSON.parse(specBytes.toString('utf8'))
} catch (error) {
  console.error('[openapi] vendored 规格不是合法 JSON：' + error.message)
  process.exit(1)
}
const specPaths = spec.paths ?? {}
const schemas = (spec.components ?? {}).schemas ?? {}

const normalizeTemplate = (path) => path.replace(/\{[^}]*\}/g, '{}')

function specPathFor(localPath) {
  const wanted = normalizeTemplate('/' + localPath)
  const matches = Object.keys(specPaths).filter((p) => normalizeTemplate(p) === wanted)
  if (matches.length === 0) {
    throw new Error('契约里没有与 ' + localPath + ' 结构一致的路径（本端调用点与契约不一致）')
  }
  if (matches.length > 1) {
    throw new Error('契约里有多条路径与 ' + localPath + ' 结构一致：' + matches.join(', '))
  }
  return matches[0]
}

function operation(specPath, method) {
  const item = specPaths[specPath]
  const op = item?.[method]
  if (!op) {
    throw new Error('契约里 ' + specPath + ' 没有 ' + method.toUpperCase() + ' 方法（本端调用点与契约不一致）')
  }
  return op
}

function constantName(localPath) {
  const stripped = localPath.replace(/\{[^}]*\}/g, '/')
  const words = []
  for (const part of stripped.split(/[^A-Za-z0-9]+/).filter(Boolean)) {
    for (const sub of part.replace(/([a-z0-9])([A-Z])/g, '$1 $2').split(' ')) {
      if (sub) words.push(sub)
    }
  }
  if (words.length === 0) throw new Error('无法为路径生成常量名：' + localPath)
  return words.map((word) => word.toUpperCase()).join('_')
}

function buildPaths() {
  const lines = []
  lines.push('// GENERATED — DO NOT EDIT.')
  lines.push('// 契约路径表：调用点只允许引用 ApiPaths 常量，禁止散落路径字面量。')
  lines.push('// 生成器：' + generatorName)
  lines.push('// 事实源：openapi/drone-backend.openapi.json (sha256: ' + sha + ')')
  lines.push('// 生成命令：' + generateCommand)
  lines.push('')
  lines.push('package com.fuwaki.djifly.platform.network.generated')
  lines.push('')
  lines.push('/**')
  lines.push(' * 后端端点路径常量（与 openapi/drone-backend.openapi.json 同源）。')
  lines.push(' *')
  lines.push(' * 常量值刻意不带前导斜杠：Retrofit 会把它解析到 baseUrl 之下，')
  lines.push(' * 与既有调用点的字面量保持逐字节一致。')
  lines.push(' */')
  lines.push('object ApiPaths {')
  const used = new Set()
  for (const [method, localPath] of endpoints) {
    const specPath = specPathFor(localPath)
    operation(specPath, method)
    const name = constantName(localPath)
    if (used.has(name)) throw new Error('常量名冲突：' + name + '（' + localPath + '）')
    used.add(name)
    const note = specPath === '/' + localPath ? '' : '（契约路径 ' + specPath + '）'
    lines.push('')
    lines.push('    /** ' + method.toUpperCase() + ' ' + localPath + note + ' */')
    lines.push('    const val ' + name + ' = "' + localPath + '"')
  }
  lines.push('')
  lines.push('    /** 事实源规格 openapi/drone-backend.openapi.json 的 sha256（门禁测试对账）。 */')
  lines.push('    const val GENERATED_FROM_SHA256 = "' + sha + '"')
  lines.push('}')
  lines.push('')
  return lines.join('\n')
}

function refName(schema) {
  if (schema && typeof schema === 'object' && typeof schema.$ref === 'string') {
    return schema.$ref.split('/').pop()
  }
  return null
}

function resolveSchema(schema) {
  const ref = refName(schema)
  if (!ref) return schema
  const target = schemas[ref]
  if (!target) throw new Error('契约里没有 schema：' + ref)
  return target
}

const needed = new Set()

function visit(schema) {
  if (!schema || typeof schema !== 'object') return
  const ref = refName(schema)
  if (ref) {
    if (needed.has(ref)) return
    if (!schemas[ref]) throw new Error('契约里没有 schema：' + ref)
    needed.add(ref)
    visit(schemas[ref])
    return
  }
  if (schema.properties && typeof schema.properties === 'object') {
    for (const value of Object.values(schema.properties)) visit(value)
  }
  visit(schema.items)
  visit(schema.additionalProperties)
  for (const key of ['allOf', 'oneOf', 'anyOf']) {
    if (Array.isArray(schema[key])) for (const part of schema[key]) visit(part)
  }
}

function jsonSchemaOf(content) {
  if (!content || typeof content !== 'object') return null
  if (content['application/json']) return content['application/json'].schema
  for (const value of Object.values(content)) {
    if (value && typeof value === 'object' && value.schema) return value.schema
  }
  return null
}

function collect() {
  // 闭包只取「本端真正调用」的 16 条端点；referencedEndpoints 仅生成 ApiPaths 常量。
  for (const [method, localPath] of calledEndpoints) {
    const op = operation(specPathFor(localPath), method)
    if (Array.isArray(op.parameters)) {
      for (const param of op.parameters) visit(param?.schema)
    }
    if (op.requestBody) visit(jsonSchemaOf(op.requestBody.content))
    if (op.responses) {
      for (const response of Object.values(op.responses)) {
        if (response && typeof response === 'object') visit(jsonSchemaOf(response.content))
      }
    }
  }
}

/** allOf 拍平：Java 继承关系在契约里展开为 allOf，Kotlin 侧合并成一个类。 */
function mergedShape(name) {
  const rootSchema = resolveSchema({ $ref: '#/components/schemas/' + name })
  const properties = {}
  const required = new Set()
  const absorb = (part) => {
    const resolved = part ? resolveSchema(part) : null
    if (!resolved || typeof resolved !== 'object') return
    if (resolved.properties && typeof resolved.properties === 'object') {
      for (const [key, value] of Object.entries(resolved.properties)) properties[key] = value
    }
    if (Array.isArray(resolved.required)) for (const item of resolved.required) required.add(item)
  }
  if (Array.isArray(rootSchema.allOf)) for (const part of rootSchema.allOf) absorb(part)
  absorb(rootSchema)
  return { properties, required: [...required] }
}

// Kotlin 硬关键字（不能直接作属性名）；data/inline/open 等软关键字可以，无需反引号。
const KOTLIN_HARD_KEYWORDS = new Set([
  'as', 'break', 'class', 'continue', 'do', 'else', 'false', 'for', 'fun', 'if', 'in',
  'interface', 'is', 'null', 'object', 'package', 'return', 'super', 'this', 'throw',
  'true', 'try', 'typealias', 'typeof', 'val', 'var', 'when', 'while',
])

function propertyName(jsonKey) {
  if (/^[A-Za-z_][A-Za-z0-9_]*$/.test(jsonKey) && !KOTLIN_HARD_KEYWORDS.has(jsonKey)) return jsonKey
  return '`' + jsonKey + '`'
}

function kotlinType(schema, nullable) {
  const suffix = nullable ? '?' : ''
  if (!schema || typeof schema !== 'object') return 'Any?'
  const ref = refName(schema)
  if (ref) return 'Api' + ref + suffix
  switch (schema.type) {
    case 'array':
      return 'List<' + kotlinType(schema.items, false) + '>' + suffix
    case 'integer':
      return (schema.format === 'int64' ? 'Long' : 'Int') + suffix
    case 'number':
      return 'Double' + suffix
    case 'boolean':
      return 'Boolean' + suffix
    case 'string':
      return 'String' + suffix
    case 'object':
      return 'Map<String, Any?>' + suffix
    default:
      return 'Any?'
  }
}

function buildModels() {
  collect()
  const names = [...needed].sort()
  const body = []
  let usesSerializedName = false

  for (const name of names) {
    const shape = mergedShape(name)
    const requiredSet = new Set(shape.required)
    const entries = Object.entries(shape.properties)
    body.push('')
    body.push('/** 契约 components.schemas.' + name + '。 */')
    if (entries.length === 0) {
      body.push('data class Api' + name + '(val placeholder: Any? = null)')
      continue
    }
    const params = []
    for (const [key, schema] of entries) {
      const prop = propertyName(key)
      const isRequired = requiredSet.has(key)
      const type = kotlinType(schema, !isRequired)
      const annotation = prop === key ? '' : '@SerializedName("' + key + '") '
      if (annotation) usesSerializedName = true
      const suffix = isRequired ? '' : ' = null'
      params.push('    ' + annotation + 'val ' + prop + ': ' + type + suffix)
    }
    body.push('data class Api' + name + '(')
    for (let i = 0; i < params.length; i += 1) {
      body.push(params[i] + (i === params.length - 1 ? '' : ','))
    }
    body.push(')')
  }

  const lines = []
  lines.push('// GENERATED — DO NOT EDIT.')
  lines.push('// 契约模型：后端 components.schemas 中「被调用端点可达」的传递闭包。')
  lines.push('// 生成器：' + generatorName)
  lines.push('// 事实源：openapi/drone-backend.openapi.json (sha256: ' + sha + ')')
  lines.push('// 生成命令：' + generateCommand)
  lines.push('')
  lines.push('package com.fuwaki.djifly.platform.network.generated')
  if (usesSerializedName) {
    lines.push('')
    lines.push('import com.google.gson.annotations.SerializedName')
  }
  lines.push('')
  lines.push('/**')
  lines.push(' * 契约数据类（Json 注解与仓库既有的 Gson 风格一致：字段名与契约键名相同，')
  lines.push(' * 因此正常无需 @SerializedName；仅当键名不是合法 Kotlin 标识符时才生成注解）。')
  lines.push(' *')
  lines.push(' * 可空性取自契约 required 列表：required 字段非空，其余可空（默认 null）。')
  lines.push(' * 本文件由生成器维护，禁止手改。')
  lines.push(' */')
  for (const line of body) lines.push(line)
  lines.push('')
  return lines.join('\n')
}

let pathsSource
let modelsSource
try {
  pathsSource = buildPaths()
  modelsSource = buildModels()
} catch (error) {
  console.error('[openapi] FAIL：' + error.message)
  process.exit(1)
}

const outputs = [
  [pathsOut, pathsSource],
  [modelsOut, modelsSource],
]

let drift = 0
for (const [file, content] of outputs) {
  const existing = existsSync(file) ? readFileSync(file, 'utf8') : null
  if (existing === content) continue
  drift += 1
  if (check) {
    console.error('[openapi] 生成物与规格不同步：' + relative(root, file))
  } else {
    mkdirSync(dirname(file), { recursive: true })
    writeFileSync(file, content)
    console.log('[openapi] 已写入 ' + relative(root, file) + '（' + Buffer.byteLength(content) + ' 字节）')
  }
}

if (check) {
  if (drift > 0) {
    console.error('[openapi] FAIL：' + drift + ' 个生成物过期，请运行 ' + generateCommand)
    process.exit(1)
  }
  console.log('[openapi] OK：生成物与规格同步（sha256=' + sha + '，端点=' + endpoints.length + '）')
  process.exit(0)
}

console.log('[openapi] sha256=' + sha + ' 端点=' + endpoints.length + ' 变更文件=' + drift)
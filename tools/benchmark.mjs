// 轻量压测脚本：用于快速 A/B 对比（JMeter 之外的补充手段）
//
// 用法：
//   node tools/benchmark.mjs                          # 默认打 18080，ID 900001-900200
//   node tools/benchmark.mjs http://localhost         # 经 Nginx
//   node tools/benchmark.mjs http://localhost:18080 23 24  # 指定文章 ID 列表
//
// 输出各并发档位下的 QPS / 平均 / P50 / P95 / P99 / 错误率。
// 用固定并发维持「在途请求数恒定」，比固定线程数循环更能反映真实吞吐上限。
import http from 'node:http'

const [target = 'http://localhost:18080', ...idArgs] = process.argv.slice(2)
const IDS = idArgs.length
  ? idArgs.map(Number)
  : Array.from({ length: 200 }, (_, i) => 900001 + i)
const LEVELS = [5, 10, 20, 40, 80, 120, 200]
const HOLD_MS = 8000

const agent = new http.Agent({ keepAlive: true, maxSockets: 8192 })

/**
 * 发一次请求。
 *
 * 不能只看 HTTP 状态码：本项目的全局异常处理器把业务错误也包成 HTTP 200，
 * 错误码放在 body 的 code 字段里（文章不存在时返回 HTTP 200 + {"code":404}）。
 * 只判断 HTTP 200 会把失败请求算成成功，测出的 QPS 虚高——
 * 这一点在 JMeter 压测计划里强调过，这里同样要遵守。
 * 因此解析 body，要求 code 为 200 且返回的 id 与请求的一致。
 */
function once(id) {
  return new Promise((resolve) => {
    const t0 = process.hrtime.bigint()
    const req = http.request(
      `${target}/api/articles/${id}`,
      { agent, headers: { Connection: 'keep-alive' } },
      (res) => {
        let buf = ''
        res.setEncoding('utf8')
        res.on('data', (c) => (buf += c))
        res.on('end', () => {
          const ms = Number(process.hrtime.bigint() - t0) / 1e6
          let bizCode = null
          let returnedId = null
          try {
            const j = JSON.parse(buf)
            bizCode = j.code
            returnedId = j.data ? j.data.id : null
          } catch {
            bizCode = 'PARSE_FAIL'
          }
          resolve({
            ms,
            ok: res.statusCode === 200 && bizCode === 200 && returnedId === id,
            code: res.statusCode === 200 ? 'biz:' + bizCode : 'http:' + res.statusCode,
          })
        })
      }
    )
    req.on('error', (e) => resolve({ ms: 0, ok: false, code: 'ERR:' + e.code }))
    req.end()
  })
}

async function runLevel(concurrency) {
  const lat = []
  let ok = 0
  const failures = {}
  const t0 = Date.now()
  let i = 0

  const worker = async () => {
    while (Date.now() - t0 < HOLD_MS) {
      const r = await once(IDS[i++ % IDS.length])
      if (r.ok) {
        ok++
        lat.push(r.ms)
      } else {
        failures[r.code] = (failures[r.code] || 0) + 1
      }
    }
  }
  await Promise.all(Array.from({ length: concurrency }, worker))

  const elapsed = (Date.now() - t0) / 1000
  lat.sort((a, b) => a - b)
  const pct = (p) => (lat.length ? lat[Math.min(lat.length - 1, Math.floor(lat.length * p))] : 0)
  return {
    concurrency,
    qps: ok / elapsed,
    avg: lat.length ? lat.reduce((a, b) => a + b, 0) / lat.length : 0,
    p50: pct(0.5),
    p95: pct(0.95),
    p99: pct(0.99),
    failed: Object.values(failures).reduce((a, b) => a + b, 0),
    failures,
  }
}

console.log(`目标: ${target}`)
console.log(`文章 ID: ${IDS.length} 个${IDS.length > 5 ? ` (${IDS[0]}..${IDS[IDS.length - 1]})` : ` (${IDS.join(',')})`}`)
console.log(`每档持续 ${HOLD_MS / 1000}s\n`)
console.log('  并发 |    QPS |   平均 |   P50 |   P95 |   P99 | 失败')
console.log('  -----+--------+--------+-------+-------+-------+------')

for (const c of LEVELS) {
  const r = await runLevel(c)
  const f = r.failed ? String(r.failed) : ''
  console.log(
    `  ${String(r.concurrency).padStart(4)} | ${r.qps.toFixed(1).padStart(6)} | ` +
      `${r.avg.toFixed(0).padStart(5)}ms | ${r.p50.toFixed(0).padStart(4)}ms | ` +
      `${r.p95.toFixed(0).padStart(4)}ms | ${r.p99.toFixed(0).padStart(4)}ms | ${f}`
  )
  if (r.failed) console.log(`        失败明细: ${JSON.stringify(r.failures)}`)
}

agent.destroy()

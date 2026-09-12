/**
 * 并发下单验证脚本：验证同一时段不会超卖，并检查测试结束后的库存守恒。
 *
 * 用法：
 *   node verify-concurrency.mjs
 *   node verify-concurrency.mjs --concurrency 1000
 *
 * 可选环境变量：BOOKING_API_BASE（默认 http://127.0.0.1:8080/api）。
 */
const BASE = process.env.BOOKING_API_BASE || 'http://127.0.0.1:8080/api'
const concurrency = readConcurrency()

let pass = 0
let fail = 0

function readConcurrency() {
  const index = process.argv.indexOf('--concurrency')
  const value = index >= 0 ? Number(process.argv[index + 1]) : 100
  if (!Number.isInteger(value) || value < 2 || value > 2000) {
    throw new Error('--concurrency 必须是 2 到 2000 之间的整数')
  }
  return value
}

async function request(path, { method = 'GET', token, body } = {}) {
  const response = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  })

  let raw
  try {
    raw = await response.json()
  } catch {
    throw new Error(`${method} ${path} 返回了非 JSON 响应，HTTP ${response.status}`)
  }

  if (!raw || typeof raw !== 'object' || !('code' in raw)) {
    if (!response.ok) throw new Error(`${method} ${path} HTTP ${response.status}`)
    return raw
  }
  if (raw.code !== 0) {
    const error = new Error(raw.message || `业务失败 code=${raw.code}`)
    error.code = raw.code
    throw error
  }
  return raw.data
}

function check(label, condition, detail = '') {
  const ok = Boolean(condition)
  console.log(`${ok ? '  PASS' : '  FAIL'}  ${label}${detail ? ` -> ${detail}` : ''}`)
  ok ? pass++ : fail++
  return ok
}

async function login() {
  return request('/auth/login', {
    method: 'POST',
    body: { username: 'customer', password: '123456' }
  })
}

async function findAvailableSlot(token) {
  const reservations = await request('/reservations', { token })
  const activeSlotIds = new Set(
    reservations.filter((item) => item.status === 0 || item.status === 1).map((item) => item.slotId)
  )
  const courts = await request('/courts?venueId=1')

  for (let offset = 0; offset < 14; offset++) {
    const date = new Date(Date.now() + offset * 24 * 60 * 60 * 1000)
      .toISOString()
      .slice(0, 10)
    for (const court of courts) {
      const slots = await request(`/courts/${court.id}/slots?date=${date}`)
      const slot = slots.find(
        (item) => item.available > 0 && !activeSlotIds.has(item.id)
      )
      if (slot) return slot
    }
  }
  return null
}

async function main() {
  console.log(`\n并发下单验证：${concurrency} 个请求，接口 ${BASE}`)
  const account = await login()
  const token = account.token
  const slot = await findAvailableSlot(token)

  check('存在未被当前账号占用的可用时段', slot && slot.available > 0,
    slot ? `slotId=${slot.id}, available=${slot.available}, total=${slot.total}` : '未找到')
  if (!slot) throw new Error('未来 14 天没有可用测试时段，请先释放或更换测试账号')

  const tokenResults = await Promise.allSettled(
    Array.from({ length: concurrency }, () =>
      request('/reservations/token', {
        method: 'POST',
        token,
        body: { slotId: slot.id }
      })
    )
  )
  const requestTokens = tokenResults
    .filter((result) => result.status === 'fulfilled' && typeof result.value === 'string')
    .map((result) => result.value)
  check('幂等令牌全部获取成功', requestTokens.length === concurrency,
    `${requestTokens.length}/${concurrency}`)

  const results = await Promise.allSettled(
    requestTokens.map((requestToken) =>
      request('/reservations', {
        method: 'POST',
        token,
        body: { token: requestToken, slotId: slot.id }
      })
    )
  )
  const successfulOrders = results
    .filter((result) => result.status === 'fulfilled' && result.value?.orderNo)
    .map((result) => result.value)
  const businessFailures = results.filter((result) => result.status === 'rejected')
  console.log(`  结果    成功 ${successfulOrders.length}，业务/网络失败 ${businessFailures.length}`)

  check('并发成功数不超过原始库存', successfulOrders.length <= slot.total,
    `${successfulOrders.length} <= ${slot.total}`)
  check('单库存时段恰好只有一个成功', slot.total !== 1 || successfulOrders.length === 1,
    `total=${slot.total}, success=${successfulOrders.length}`)

  for (const order of successfulOrders) {
    try {
      await request(`/reservations/${order.orderNo}/cancel`, { method: 'POST', token })
    } catch (error) {
      console.log(`  WARN  测试订单取消失败 orderNo=${order.orderNo} -> ${error.message}`)
    }
  }

  const slots = await request(`/courts/${slot.courtId}/slots?date=${slot.bizDate}`)
  const finalSlot = slots.find((item) => item.id === slot.id)
  const invariant = finalSlot &&
    finalSlot.available + finalSlot.locked + finalSlot.sold === finalSlot.total
  check('测试订单清理后库存守恒', invariant,
    finalSlot ? `${finalSlot.available}+${finalSlot.locked}+${finalSlot.sold}=${finalSlot.total}` : '时段不存在')

  console.log(`\n结果：${pass} 通过 / ${fail} 失败\n`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch((error) => {
  console.error(`\n执行失败：${error.message}`)
  process.exit(1)
})

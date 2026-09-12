/**
 * 契约验证脚本：确认「前端拦截器解包后拿到的到底是不是数组」。
 *
 * 背景：前端 el-table 的 :data 必须收到数组，收到对象会渲染报错、
 * loading 遮罩摘不掉。这里复刻 request.ts 的解包逻辑，对真实后端逐个接口断言类型。
 *
 * 用法：node verify-contract.mjs
 */
const BASE = 'http://127.0.0.1:8080/api'

let pass = 0
let fail = 0

async function call(path, { method = 'GET', token, body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  })
  const raw = await res.json()

  // ↓↓↓ 与 src/api/request.ts 的响应拦截器逻辑保持一致
  if (!raw || typeof raw !== 'object' || !('code' in raw)) return raw
  if (raw.code !== 0) {
    const err = new Error(raw.message)
    err.code = raw.code
    throw err
  }
  return raw.data
}

function check(label, actual, expect) {
  const ok = expect(actual)
  console.log(`${ok ? '  PASS' : '  FAIL'}  ${label}  ->  ${describe(actual)}`)
  ok ? pass++ : fail++
}

function describe(v) {
  if (Array.isArray(v)) return `Array(${v.length})`
  if (v === null) return 'null'
  if (typeof v === 'object') return `Object{${Object.keys(v).slice(0, 4).join(',')}...}`
  return `${typeof v}(${v})`
}

const isArray = (v) => Array.isArray(v)
const isObject = (v) => !!v && typeof v === 'object' && !Array.isArray(v)

console.log('\n【未登录也能访问的浏览类接口】')
check('/venues         应返回数组', await call('/venues'), isArray)
check('/courts         应返回数组', await call('/courts?venueId=1'), isArray)
check('/courts/101/slots 应返回数组', await call('/courts/101/slots?date=2026-09-10'), isArray)

console.log('\n【登录】')
const login = await call('/auth/login', {
  method: 'POST',
  body: { username: 'customer', password: '123456' }
})
check('/auth/login    应返回对象且带 token', login, (v) => isObject(v) && !!v.token)
const token = login.token

console.log('\n【需要登录的接口（之前就是这里拿不到数据）】')
check('/reservations  应返回数组', await call('/reservations', { token }), isArray)
check('/auth/me       应返回对象', await call('/auth/me', { token }), isObject)

console.log('\n【下单主链路】')
const availableSlot = await findAvailableSlot()
check('存在可用测试时段', availableSlot, (v) => isObject(v) && v.available > 0)
if (!availableSlot) {
  throw new Error('未来 14 天没有可用时段，无法执行下单契约检查')
}
const tok = await call('/reservations/token', { method: 'POST', token, body: { slotId: availableSlot.id } })
check('取幂等令牌      应返回字符串', tok, (v) => typeof v === 'string' && v.length > 0)
const order = await call('/reservations', {
  method: 'POST',
  token,
  body: { token: tok, slotId: availableSlot.id }
})
check('下单            应返回对象且带 orderNo', order, (v) => isObject(v) && !!v.orderNo)
check('订单详情        应返回对象', await call('/reservations/' + order.orderNo, { token }), isObject)
check('取消订单        应返回布尔', await call('/reservations/' + order.orderNo + '/cancel', { method: 'POST', token }), (v) => v === true)

console.log('\n【权限隔离】')
try {
  await call('/merchant/courts', { token })
  console.log('  FAIL  顾客访问商家端应当被拒，但通过了')
  fail++
} catch (e) {
  console.log(`  PASS  顾客访问商家端被拒  ->  code=${e.code} ${e.message}`)
  pass++
}

console.log('\n【商家端】')
const mLogin = await call('/auth/login', {
  method: 'POST',
  body: { username: 'merchant', password: '123456' }
})
check('/merchant/venues 应返回数组', await call('/merchant/venues', { token: mLogin.token }), isArray)
check('/merchant/courts 应返回数组', await call('/merchant/courts', { token: mLogin.token }), isArray)

console.log(`\n结果：${pass} 通过 / ${fail} 失败\n`)
process.exit(fail === 0 ? 0 : 1)

/** 从未来 14 天的公开时段中选择一个真实可约时段，避免写死库存已耗尽的 ID。 */
async function findAvailableSlot() {
  const courts = await call('/courts?venueId=1')
  for (let offset = 0; offset < 14; offset++) {
    const date = new Date(Date.now() + offset * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
    for (const court of courts) {
      const slots = await call(`/courts/${court.id}/slots?date=${date}`)
      const slot = slots.find((item) => item.available > 0)
      if (slot) return slot
    }
  }
  return null
}

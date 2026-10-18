import { useState, useEffect, useCallback } from 'react'
import './styles.css'

const tok = () => decodeURIComponent((document.cookie.match(/XSRF-TOKEN=([^;]+)/) || [])[1] || '')
async function api(p, method = 'GET', body) {
  const r = await fetch('/api' + p, {
    method, headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': tok() },
    body: body ? JSON.stringify(body) : undefined })
  const t = await r.text(); let d = null
  try { d = t ? JSON.parse(t) : null } catch { d = t }
  if (!r.ok) throw new Error((d && d.message) || 'Ошибка ' + r.status)
  return d
}
const ST = { SENT: 'Отправлен', REVIEW: 'На рассмотрении', INVITE: 'Приглашение на собеседование', REJECT: 'Отказ',
  PENDING: 'На модерации', APPROVED: 'Опубликована', REJECTED: 'Отклонена' }
const useGet = p => {
  const [d, setD] = useState([])
  const l = useCallback(() => { api(p).then(setD).catch(() => {}) }, [p])
  useEffect(() => { l() }, [l])
  return [d, l]
}

function Auth({ onUser }) {
  const [reg, setReg] = useState(false), [f, setF] = useState({ role: 'STUDENT' }), [err, setErr] = useState('')
  const set = k => e => setF({ ...f, [k]: e.target.value })
  const go = async () => {
    try { setErr(''); if (reg) await api('/auth/register', 'POST', f); onUser(await api('/auth/login', 'POST', f)) }
    catch (e) { setErr(e.message) }
  }
  return <div className="card narrow"><h2>{reg ? 'Регистрация' : 'Вход'}</h2>
    {reg && <>
      <input placeholder="ФИО" onChange={set('fullName')} />
      <select onChange={set('role')}><option value="STUDENT">Студент</option><option value="EMPLOYER">Работодатель</option></select>
      {f.role === 'EMPLOYER' && <input placeholder="Компания" onChange={set('company')} />}</>}
    <input placeholder="E-mail" onChange={set('email')} />
    <input type="password" placeholder="Пароль (от 8 символов)" onChange={set('password')} />
    {err && <p className="err">{err}</p>}
    <button onClick={go}>{reg ? 'Зарегистрироваться' : 'Войти'}</button>{' '}
    <a href="#" onClick={e => { e.preventDefault(); setReg(!reg) }}>{reg ? 'Уже есть аккаунт' : 'Создать аккаунт'}</a>
  </div>
}

function Catalog() {
  const [q, setQ] = useState({ q: '', city: '', type: '', industry: '', direction: '' }), [list, setList] = useState([]), [msg, setMsg] = useState('')
  const load = () => api('/vacancies?' + new URLSearchParams(q)).then(setList)
  useEffect(() => { load() }, [])
  const apply = async v => {
    const c = prompt('Сопроводительное письмо'); if (c === null) return
    try { await api(`/vacancies/${v.id}/apply`, 'POST', { coverLetter: c }); setMsg('Отклик отправлен') } catch (e) { setMsg(e.message) }
  }
  return <div><div className="card row">
    {[['q', 'Поиск'], ['direction', 'Направление'], ['industry', 'Отрасль'], ['city', 'Город']].map(([k, l]) =>
      <input key={k} placeholder={l} value={q[k]} onChange={e => setQ({ ...q, [k]: e.target.value })} />)}
    <select value={q.type} onChange={e => setQ({ ...q, type: e.target.value })}>
      <option value="">Любой тип</option><option>Полная занятость</option><option>Частичная занятость</option><option>Стажировка</option></select>
    <button onClick={load}>Найти</button></div>
    {msg && <p><b>{msg}</b></p>}
    {list.map(v => <div className="card" key={v.id}><b>{v.title}</b> — {v.employer.company}
      <p>{v.city} · {v.industry} · {v.employmentType} · {v.direction}</p><p>{v.description}</p>
      <button onClick={() => apply(v)}>Откликнуться</button></div>)}</div>
}

function MyReplies() {
  const [list] = useGet('/my/replies')
  return <div>{list.map(r => <div className="card" key={r.id}><b>{r.vacancy.title}</b> — {ST[r.status]}</div>)}</div>
}

function Profile({ user, onUser }) {
  const [f, setF] = useState(user), [m, setM] = useState('')
  const set = k => e => setF({ ...f, [k]: e.target.value })
  return <div className="card narrow"><h3>Профиль и резюме</h3>
    <input placeholder="ФИО" value={f.fullName || ''} onChange={set('fullName')} />
    <input placeholder="Направление подготовки" value={f.faculty || ''} onChange={set('faculty')} />
    <textarea rows="8" placeholder="Резюме: образование, навыки, опыт" value={f.resume || ''} onChange={set('resume')} />
    <button onClick={async () => { onUser(await api('/profile', 'PUT', f)); setM('Сохранено') }}>Сохранить</button> {m}</div>
}

function MyVacancies() {
  const [list, load] = useGet('/my/vacancies'), [f, setF] = useState({}), [err, setErr] = useState('')
  const set = k => e => setF({ ...f, [k]: e.target.value })
  const save = async () => {
    try { setErr(''); await api(f.id ? '/my/vacancies/' + f.id : '/my/vacancies', f.id ? 'PUT' : 'POST', f); setF({}); load() }
    catch (e) { setErr(e.message) }
  }
  const del = async id => { if (confirm('Удалить вакансию?')) { await api('/my/vacancies/' + id, 'DELETE'); load() } }
  return <div><div className="card"><h3>{f.id ? 'Редактирование' : 'Новая вакансия'}</h3>
    {[['title', 'Название'], ['direction', 'Направление подготовки'], ['industry', 'Отрасль'], ['city', 'Город'], ['employmentType', 'Тип занятости']].map(([k, l]) =>
      <input key={k} placeholder={l} value={f[k] || ''} onChange={set(k)} />)}
    <textarea placeholder="Описание" value={f.description || ''} onChange={set('description')} />
    {err && <p className="err">{err}</p>}<button onClick={save}>Отправить на модерацию</button></div>
    {list.map(v => <div className="card" key={v.id}><b>{v.title}</b> — {ST[v.status]}
      {v.rejectReason && <p className="err">Причина отказа: {v.rejectReason}</p>}
      <div><button onClick={() => setF(v)}>Изменить</button><button onClick={() => del(v.id)}>Удалить</button></div></div>)}</div>
}

function Responses() {
  const [list, load] = useGet('/my/responses')
  const st = async (id, status) => { await api(`/replies/${id}/status`, 'PUT', { status }); load() }
  return <div>{list.map(r => <div className="card" key={r.id}>
    <b>{r.student.fullName}</b> → {r.vacancy.title} <i>[{ST[r.status]}]</i>
    <p>{r.student.email}</p><p>Письмо: {r.coverLetter}</p><pre>{r.student.resume}</pre>
    {['REVIEW', 'INVITE', 'REJECT'].map(s => <button key={s} onClick={() => st(r.id, s)}>{ST[s]}</button>)}</div>)}</div>
}

function Moderation() {
  const [list, load] = useGet('/mod/vacancies')
  const dec = async (id, approve) => {
    let reason = ''
    if (!approve) { reason = prompt('Причина отклонения'); if (!reason) return }
    await api(`/mod/vacancies/${id}/decision`, 'POST', { approve, reason }); load()
  }
  return <div><p><a href="/api/mod/report.csv" download="report.csv">Скачать отчёт (CSV, открывается в Excel)</a></p>
    {list.length === 0 && <p>Нет заявок на модерации</p>}
    {list.map(v => <div className="card" key={v.id}><b>{v.title}</b> ({v.employer.company})<p>{v.description}</p>
      <button onClick={() => dec(v.id, true)}>Одобрить</button><button onClick={() => dec(v.id, false)}>Отклонить</button></div>)}</div>
}

function Users() {
  const [list, load] = useGet('/mod/users')
  const act = async (id, a) => { await api(`/mod/users/${id}/${a}`, 'POST'); load() }
  return <table><thead><tr><th>E-mail</th><th>Роль</th><th>Компания</th><th>Статус</th><th /></tr></thead><tbody>
    {list.map(u => <tr key={u.id}><td>{u.email}</td><td>{u.role}</td><td>{u.company}</td>
      <td>{u.blocked ? 'заблокирован' : u.role === 'EMPLOYER' && !u.verified ? 'не верифицирован' : 'активен'}</td>
      <td>{u.role === 'EMPLOYER' && !u.verified && <button onClick={() => act(u.id, 'verify')}>Верифицировать</button>}
        {u.role !== 'MODERATOR' && <button onClick={() => act(u.id, 'block')}>{u.blocked ? 'Разблокировать' : 'Заблокировать'}</button>}</td></tr>)}
  </tbody></table>
}

function Audit() {
  const [list] = useGet('/mod/audit')
  return <table><tbody>{list.map(a => <tr key={a.id}><td>{new Date(a.created).toLocaleString('ru')}</td><td>{a.actor}</td><td>{a.action}</td></tr>)}</tbody></table>
}

function Notes({ user }) {
  const [list, load] = useGet('/notes'), [t, setT] = useState('')
  return <div>{user.role === 'MODERATOR' && <div className="card"><h3>Глобальное уведомление</h3>
    <textarea value={t} onChange={e => setT(e.target.value)} />
    <button onClick={async () => { await api('/mod/notify', 'POST', { text: t }); setT(''); load() }}>Опубликовать</button></div>}
    {list.map(n => <div className="card" key={n.id}>{n.message}<br /><small>{new Date(n.created).toLocaleString('ru')}</small></div>)}</div>
}

function ResumeHelper({ user, onUser }) {
  const [f, setF] = useState({ position: '', education: user.faculty ? 'СибАДИ, ' + user.faculty : '', skills: '', experience: '', about: '', draft: user.resume || '' })
  const [out, setOut] = useState(''), [ai, setAi] = useState(null), [busy, setBusy] = useState(false), [msg, setMsg] = useState('')
  const set = k => e => setF({ ...f, [k]: e.target.value })
  const run = async mode => {
    setBusy(true); setMsg('')
    try { const d = await api('/resume/assist', 'POST', { ...f, mode }); setOut(d.text); setAi(d.ai) } catch (e) { setMsg(e.message) }
    setBusy(false)
  }
  const save = async () => { onUser(await api('/profile', 'PUT', { ...user, resume: out })); setMsg('Резюме сохранено в профиль') }
  return <div><div className="card"><h3>Помощь с составлением резюме</h3>
    <p><small>Заполненные поля отправляются во внешний ИИ-сервис. Не указывайте паспортные данные, адрес и телефон — контакты добавьте в готовое резюме сами.</small></p>
    <input placeholder="Желаемая должность (например, Junior AQA-инженер)" value={f.position} onChange={set('position')} />
    <textarea rows="2" placeholder="Образование" value={f.education} onChange={set('education')} />
    <textarea rows="3" placeholder="Навыки (языки программирования, инструменты, языки)" value={f.skills} onChange={set('skills')} />
    <textarea rows="4" placeholder="Опыт, практика, учебные и личные проекты — что делали и каков результат" value={f.experience} onChange={set('experience')} />
    <textarea rows="2" placeholder="О себе (необязательно)" value={f.about} onChange={set('about')} />
    <button disabled={busy} onClick={() => run('generate')}>{busy ? 'Подождите…' : 'Составить резюме'}</button>
    <hr /><textarea rows="5" placeholder="Или вставьте готовый текст резюме, чтобы его улучшить" value={f.draft} onChange={set('draft')} />
    <button disabled={busy} onClick={() => run('improve')}>Улучшить мой текст</button>
    {msg && <p className={msg.startsWith('Резюме') ? '' : 'err'}>{msg}</p>}</div>
    {out && <div className="card"><h3>Результат</h3>
      {ai === false && <p><small>ИИ на сервере не подключён, поэтому показан простой шаблон.</small></p>}
      <textarea rows="16" value={out} onChange={e => setOut(e.target.value)} />
      <button onClick={save}>Сохранить в профиль</button><button onClick={() => navigator.clipboard.writeText(out)}>Копировать</button></div>}</div>
}

const TABS = {
  STUDENT: [['Вакансии', Catalog], ['Мои отклики', MyReplies], ['Профиль', Profile], ['Помощь с резюме', ResumeHelper], ['Уведомления', Notes]],
  EMPLOYER: [['Мои вакансии', MyVacancies], ['Отклики', Responses], ['Уведомления', Notes]],
  MODERATOR: [['Модерация', Moderation], ['Пользователи', Users], ['Журнал', Audit], ['Уведомления', Notes]],
}

export default function App() {
  const [u, setU] = useState(null), [ready, setReady] = useState(false), [t, setT] = useState(0)
  useEffect(() => { api('/me').then(d => setU(d && d.id ? d : null)).finally(() => setReady(true)) }, [])
  if (!ready) return null
  if (!u) return <Auth onUser={d => { setU(d); setT(0) }} />
  const tabs = TABS[u.role], C = tabs[t][1]
  const out = async () => { await api('/auth/logout', 'POST'); await api('/me'); setU(null) }
  return <>
    <header><b>Центр карьеры СибАДИ</b>
      <nav>{tabs.map(([n], i) => <button key={n} className={i === t ? 'on' : ''} onClick={() => setT(i)}>{n}</button>)}</nav>
      <span>{u.fullName} <button onClick={out}>Выйти</button></span></header>
    {u.role === 'EMPLOYER' && !u.verified && <p className="warn">Аккаунт ожидает верификации модератором — размещать вакансии пока нельзя.</p>}
    <main><C user={u} onUser={setU} /></main></>
}

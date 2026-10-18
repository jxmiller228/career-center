package ru.sibadi.career;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class Api {
    @Autowired Users users; @Autowired Vacancies vac; @Autowired Replies replies;
    @Autowired Notes noteRepo; @Autowired Audits audits;
    @Autowired AuthenticationManager am; @Autowired PasswordEncoder enc;

    static final Map<String, String> LABEL = Map.of("REVIEW", "На рассмотрении", "INVITE", "Приглашение на собеседование", "REJECT", "Отказ");

    private AppUser me(Authentication a) { return users.findByEmail(a.getName()).orElseThrow(); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException status(HttpStatus s) { return new ResponseStatusException(s); }
    private void log(Authentication a, String s) { var x = new Audit(); x.actor = a == null ? "-" : a.getName(); x.action = s; audits.save(x); }
    private void notify(Long uid, String t) { var n = new Note(); n.userId = uid; n.message = t; noteRepo.save(n); }

    // ---------- Аутентификация ----------
    @GetMapping("/me")
    public Object meApi(Authentication a) { return a == null || a instanceof AnonymousAuthenticationToken ? Map.of() : me(a); }

    @PostMapping("/auth/register")
    public Object register(@RequestBody Map<String, String> b) {
        String role = b.getOrDefault("role", "STUDENT");
        if (!role.equals("STUDENT") && !role.equals("EMPLOYER")) throw bad("Недопустимая роль");
        String email = b.getOrDefault("email", "").trim().toLowerCase(), pw = b.getOrDefault("password", "");
        if (!email.contains("@") || pw.length() < 8) throw bad("Нужен e-mail и пароль от 8 символов");
        if (users.findByEmail(email).isPresent()) throw bad("Этот e-mail уже зарегистрирован");
        users.save(App.mk(enc, email, b.getOrDefault("fullName", ""), role, pw, b.get("company")));
        log(null, "register " + email);
        return Map.of("ok", true);
    }

    @PostMapping("/auth/login")
    public Object login(@RequestBody Map<String, String> b, HttpServletRequest q, HttpServletResponse s) {
        try {
            var au = am.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                b.getOrDefault("email", "").trim().toLowerCase(), b.getOrDefault("password", "")));
            var c = SecurityContextHolder.createEmptyContext();
            c.setAuthentication(au);
            SecurityContextHolder.setContext(c);
            new HttpSessionSecurityContextRepository().saveContext(c, q, s);
            log(au, "login");
            return me(au);
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный e-mail/пароль или аккаунт заблокирован");
        }
    }

    @PutMapping("/profile")
    public Object profile(@RequestBody Map<String, String> b, Authentication a) {
        var u = me(a);
        u.fullName = b.getOrDefault("fullName", u.fullName);
        u.faculty = b.getOrDefault("faculty", u.faculty);
        u.resume = b.getOrDefault("resume", u.resume);
        return users.save(u);
    }

    // ---------- Каталог и отклики (студент) ----------
    @GetMapping("/vacancies")
    public List<Vacancy> list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String city,
                              @RequestParam(defaultValue = "") String type, @RequestParam(defaultValue = "") String industry,
                              @RequestParam(defaultValue = "") String direction) {
        return vac.search(q, city, type, industry, direction);
    }

    @PreAuthorize("hasRole('STUDENT')") @PostMapping("/vacancies/{id}/apply")
    public Reply apply(@PathVariable Long id, @RequestBody Map<String, String> b, Authentication a) {
        var s = me(a);
        var v = vac.findById(id).filter(x -> x.status.equals("APPROVED")).orElseThrow(() -> status(HttpStatus.NOT_FOUND));
        if (replies.existsByVacancyAndStudent(v, s)) throw bad("Вы уже откликались на эту вакансию");
        if (s.resume == null || s.resume.isBlank()) throw bad("Сначала заполните резюме в профиле");
        var r = new Reply();
        r.vacancy = v; r.student = s; r.coverLetter = b.getOrDefault("coverLetter", "");
        notify(v.employer.id, "Новый отклик на «" + v.title + "»");
        log(a, "apply " + id);
        return replies.save(r);
    }

    @PreAuthorize("hasRole('STUDENT')") @GetMapping("/my/replies")
    public List<Reply> myReplies(Authentication a) { return replies.findByStudentOrderByIdDesc(me(a)); }

    // ---------- Работодатель ----------
    @PreAuthorize("hasRole('EMPLOYER')") @GetMapping("/my/vacancies")
    public List<Vacancy> mine(Authentication a) { return vac.findByEmployer(me(a)); }

    @PreAuthorize("hasRole('EMPLOYER')") @PostMapping("/my/vacancies")
    public Vacancy create(@RequestBody Vacancy b, Authentication a) {
        var u = me(a);
        if (!u.verified) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Аккаунт ещё не верифицирован модератором");
        var v = new Vacancy();
        v.employer = u;
        return save(v, b, a, "create vacancy");
    }

    @PreAuthorize("hasRole('EMPLOYER')") @PutMapping("/my/vacancies/{id}")
    public Vacancy edit(@PathVariable Long id, @RequestBody Vacancy b, Authentication a) { return save(own(id, a), b, a, "edit vacancy " + id); }

    @PreAuthorize("hasRole('EMPLOYER')") @DeleteMapping("/my/vacancies/{id}")
    public void delete(@PathVariable Long id, Authentication a) {
        var v = own(id, a);
        replies.deleteAll(replies.findByVacancy(v));
        vac.delete(v);
        log(a, "delete vacancy " + id);
    }

    private Vacancy own(Long id, Authentication a) {
        var v = vac.findById(id).orElseThrow(() -> status(HttpStatus.NOT_FOUND));
        if (!v.employer.id.equals(me(a).id)) throw status(HttpStatus.FORBIDDEN);
        return v;
    }

    private Vacancy save(Vacancy v, Vacancy b, Authentication a, String act) {
        if (b.title == null || b.title.isBlank()) throw bad("Укажите название вакансии");
        v.title = b.title; v.description = b.description; v.direction = b.direction;
        v.industry = b.industry; v.city = b.city; v.employmentType = b.employmentType;
        v.status = "PENDING"; v.rejectReason = null; // любое изменение — повторная модерация
        log(a, act);
        return vac.save(v);
    }

    @PreAuthorize("hasRole('EMPLOYER')") @GetMapping("/my/responses")
    public List<Reply> responses(Authentication a) { return replies.findByVacancyEmployerOrderByIdDesc(me(a)); }

    @PreAuthorize("hasRole('EMPLOYER')") @PutMapping("/replies/{id}/status")
    public Reply setStatus(@PathVariable Long id, @RequestBody Map<String, String> b, Authentication a) {
        var r = replies.findById(id).orElseThrow(() -> status(HttpStatus.NOT_FOUND));
        if (!r.vacancy.employer.id.equals(me(a).id)) throw status(HttpStatus.FORBIDDEN);
        String st = b.get("status");
        if (!LABEL.containsKey(st)) throw bad("Недопустимый статус");
        r.status = st;
        notify(r.student.id, "Статус отклика на «" + r.vacancy.title + "»: " + LABEL.get(st));
        log(a, "reply " + id + " -> " + st);
        return replies.save(r);
    }

    // ---------- Уведомления ----------
    @GetMapping("/notes")
    public List<Note> notes(Authentication a) { return noteRepo.forUser(me(a).id); }

    // ---------- Модератор ----------
    @GetMapping("/mod/vacancies")
    public List<Vacancy> pending() { return vac.findByStatus("PENDING"); }

    @PostMapping("/mod/vacancies/{id}/decision")
    public Vacancy decide(@PathVariable Long id, @RequestBody Map<String, Object> b, Authentication a) {
        var v = vac.findById(id).orElseThrow(() -> status(HttpStatus.NOT_FOUND));
        boolean ok = Boolean.TRUE.equals(b.get("approve"));
        String reason = b.get("reason") == null ? "" : b.get("reason").toString();
        if (ok) {
            v.status = "APPROVED"; v.rejectReason = null;
            users.findAll().stream()
                .filter(u -> u.role.equals("STUDENT") && v.direction != null && v.direction.equalsIgnoreCase(u.faculty))
                .forEach(u -> notify(u.id, "Новая подходящая вакансия: " + v.title));
        } else {
            if (reason.isBlank()) throw bad("Укажите причину отклонения");
            v.status = "REJECTED"; v.rejectReason = reason;
        }
        notify(v.employer.id, ok ? "Вакансия «" + v.title + "» опубликована" : "Вакансия «" + v.title + "» отклонена: " + reason);
        log(a, (ok ? "approve " : "reject ") + id);
        return vac.save(v);
    }

    @GetMapping("/mod/users")
    public List<AppUser> allUsers() { return users.findAll(); }

    @PostMapping("/mod/users/{id}/block")
    public AppUser block(@PathVariable Long id, Authentication a) {
        var u = users.findById(id).orElseThrow(() -> status(HttpStatus.NOT_FOUND));
        if (u.role.equals("MODERATOR")) throw bad("Нельзя блокировать модератора");
        u.blocked = !u.blocked;
        log(a, (u.blocked ? "block " : "unblock ") + u.email);
        return users.save(u);
    }

    @PostMapping("/mod/users/{id}/verify")
    public AppUser verify(@PathVariable Long id, Authentication a) {
        var u = users.findById(id).orElseThrow(() -> status(HttpStatus.NOT_FOUND));
        u.verified = true;
        notify(u.id, "Ваш аккаунт верифицирован, можно размещать вакансии");
        log(a, "verify " + u.email);
        return users.save(u);
    }

    @PostMapping("/mod/notify")
    public void broadcast(@RequestBody Map<String, String> b, Authentication a) {
        if (b.getOrDefault("text", "").isBlank()) throw bad("Пустое уведомление");
        notify(null, b.get("text"));
        log(a, "broadcast");
    }

    @GetMapping("/mod/audit")
    public List<Audit> audit() { return audits.findTop100ByOrderByIdDesc(); }

    @GetMapping(value = "/mod/report.csv", produces = "text/csv;charset=UTF-8")
    public String report() {
        var sb = new StringBuilder("\uFEFFвакансия;работодатель;статус;откликов\n");
        for (var v : vac.findAll())
            sb.append(v.title.replace(';', ',')).append(';').append(Objects.toString(v.employer.company, "")).append(';')
              .append(v.status).append(';').append(replies.findByVacancy(v).size()).append('\n');
        return sb.toString();
    }
}

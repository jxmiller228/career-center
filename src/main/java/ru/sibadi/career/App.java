package ru.sibadi.career;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@SpringBootApplication
@EnableMethodSecurity
public class App {
    public static void main(String[] args) { SpringApplication.run(App.class, args); }

    // Пароли хешируются bcrypt
    @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService uds(Users users) {
        return email -> users.findByEmail(email)
            .map(u -> org.springframework.security.core.userdetails.User.withUsername(u.email)
                .password(u.password).roles(u.role).disabled(u.blocked).build())
            .orElseThrow(() -> new UsernameNotFoundException(email));
    }

    @Bean AuthenticationManager authManager(AuthenticationConfiguration c) throws Exception { return c.getAuthenticationManager(); }

    @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null); // токен CSRF выдаётся сразу (cookie XSRF-TOKEN)
        return http
            .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(csrfHandler))
            .authorizeHttpRequests(r -> r
                .requestMatchers("/api/auth/**", "/api/me").permitAll()
                .requestMatchers("/api/mod/**").hasRole("MODERATOR")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .logout(l -> l.logoutUrl("/api/auth/logout").logoutSuccessHandler((q, s, a) -> s.setStatus(200)))
            // защита от XSS: запрет внешних скриптов
            .headers(h -> h.contentSecurityPolicy(p -> p.policyDirectives("default-src 'self'; style-src 'self' 'unsafe-inline'")))
            .build();
    }

    static AppUser mk(PasswordEncoder e, String email, String name, String role, String pw, String company) {
        var u = new AppUser();
        u.email = email; u.fullName = name; u.role = role; u.password = e.encode(pw); u.company = company;
        u.verified = !role.equals("EMPLOYER");
        return u;
    }

    // Демо-данные при первом запуске
    @Bean CommandLineRunner seed(Users u, Vacancies v, PasswordEncoder e) {
        return x -> {
            if (u.count() > 0) return;
            u.save(mk(e, "moderator@sibadi.ru", "Сотрудник Центра карьеры", "MODERATOR", "Moder123!", null));
            var em = mk(e, "employer@demo.ru", "Иван Петров", "EMPLOYER", "Employer123!", "ООО «Демо»");
            em.verified = true; u.save(em);
            var st = mk(e, "student@sibadi.ru", "Студент Демо", "STUDENT", "Student123!", null);
            st.faculty = "Прикладная информатика"; st.resume = "Образование: СибАДИ, прикладная информатика. Навыки: Java, SQL.";
            u.save(st);
            for (String[] t : new String[][]{{"Junior AQA-инженер", "ИТ"}, {"Java-разработчик (стажёр)", "ИТ"}}) {
                var w = new Vacancy();
                w.employer = em; w.title = t[0]; w.industry = t[1]; w.city = "Омск"; w.employmentType = "Стажировка";
                w.direction = "Прикладная информатика"; w.description = "Описание вакансии"; w.status = "APPROVED";
                v.save(w);
            }
        };
    }
}

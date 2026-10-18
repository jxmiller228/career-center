package ru.sibadi.career;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity @Table(name = "users")
class AppUser {
    @Id @GeneratedValue public Long id;
    @Column(unique = true) public String email;
    @JsonIgnore public String password;
    public String fullName, role, company, faculty;
    @Column(length = 4000) public String resume;
    public boolean blocked, verified;
}

@Entity
class Vacancy {
    @Id @GeneratedValue public Long id;
    @ManyToOne public AppUser employer;
    public String title, direction, industry, city, employmentType, rejectReason, status = "PENDING";
    @Column(length = 4000) public String description;
}

@Entity
class Reply {
    @Id @GeneratedValue public Long id;
    @ManyToOne public Vacancy vacancy;
    @ManyToOne public AppUser student;
    @Column(length = 4000) public String coverLetter;
    public String status = "SENT";
    public Instant created = Instant.now();
}

@Entity
class Note {
    @Id @GeneratedValue public Long id;
    public Long userId; // null = глобальное уведомление
    public String message;
    public Instant created = Instant.now();
}

@Entity
class Audit {
    @Id @GeneratedValue public Long id;
    public String actor, action;
    public Instant created = Instant.now();
}

interface Users extends JpaRepository<AppUser, Long> { Optional<AppUser> findByEmail(String e); }

interface Vacancies extends JpaRepository<Vacancy, Long> {
    @Query("select v from Vacancy v where v.status='APPROVED' and lower(v.title) like lower(concat('%',:q,'%')) "
         + "and (:city='' or v.city=:city) and (:type='' or v.employmentType=:type) "
         + "and (:industry='' or v.industry=:industry) and (:direction='' or v.direction=:direction) order by v.id desc")
    List<Vacancy> search(@Param("q") String q, @Param("city") String city, @Param("type") String type,
                         @Param("industry") String industry, @Param("direction") String direction);
    List<Vacancy> findByEmployer(AppUser e);
    List<Vacancy> findByStatus(String s);
}

interface Replies extends JpaRepository<Reply, Long> {
    List<Reply> findByStudentOrderByIdDesc(AppUser s);
    List<Reply> findByVacancyEmployerOrderByIdDesc(AppUser e);
    List<Reply> findByVacancy(Vacancy v);
    boolean existsByVacancyAndStudent(Vacancy v, AppUser s);
}

interface Notes extends JpaRepository<Note, Long> {
    @Query("select n from Note n where n.userId is null or n.userId=:u order by n.id desc")
    List<Note> forUser(@Param("u") Long u);
}

interface Audits extends JpaRepository<Audit, Long> { List<Audit> findTop100ByOrderByIdDesc(); }

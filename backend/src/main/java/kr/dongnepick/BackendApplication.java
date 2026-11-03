package kr.dongnepick;

import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.*;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class BackendApplication {
 public static void main(String[] args){ SpringApplication.run(BackendApplication.class,args); }
 @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder();}
 // 학습용 로컬 데모. 외부 배포 시 CSRF 토큰과 보안 설정이 필요하다.
 @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().permitAll()).build();}
 @Bean CommandLineRunner seed(UserRepo u,StoreRepo s,PostRepo p,PasswordEncoder enc){return args->{if(u.count()!=0)return;
  User a=new User();a.email="demo@dongne.kr";a.passwordHash=enc.encode("demo1234");a.nickname="동네빵집";u.save(a);
  Store st=new Store();st.ownerId=a.id;st.name="온기 베이커리";st.region="서울 마포구";st.introduction="매일 아침 굽는 작은 동네 빵집입니다.";s.save(st);
  Post p1=new Post();p1.storeId=st.id;p1.content="오늘도 갓 구운 소금빵이 나왔어요. 따뜻할 때 들러 주세요!";p1.imageUrl="";p.save(p1);
  Post p2=new Post();p2.storeId=st.id;p2.content="이번 주말에는 딸기 케이크를 준비합니다. 소중한 사람과 달콤한 시간을 보내세요.";p2.imageUrl="";p.save(p2);
 };}
}
@Entity @Table(name="users") class User { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(nullable=false,unique=true,length=255) public String email; @Column(nullable=false) public String passwordHash; @Column(nullable=false,length=50) public String nickname; }
@Entity @Table(name="stores") class Store { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(name="owner_id",nullable=false) public Long ownerId; @JsonIgnore @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="owner_id",insertable=false,updatable=false) public User owner; @Column(nullable=false,length=100) public String name; @Column(nullable=false,length=100) public String region; @Column(length=500) public String introduction; }
@Entity @Table(name="posts") class Post { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(name="store_id",nullable=false) public Long storeId; @JsonIgnore @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="store_id",insertable=false,updatable=false) public Store store; @Column(nullable=false,length=4000) public String content; @Column(length=1000) public String imageUrl; @Column(nullable=false) public LocalDateTime createdAt=LocalDateTime.now(); }
@Entity @Table(name="comments") class Comment { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(name="post_id",nullable=false) public Long postId; @Column(name="user_id",nullable=false) public Long userId; @JsonIgnore @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="post_id",insertable=false,updatable=false) public Post post; @JsonIgnore @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="user_id",insertable=false,updatable=false) public User user; @Column(nullable=false,length=500) public String content; @Column(nullable=false) public LocalDateTime createdAt=LocalDateTime.now(); }
@Entity @Table(name="post_likes",uniqueConstraints=@UniqueConstraint(columnNames={"post_id","user_id"})) class PostLike { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(name="post_id",nullable=false) public Long postId; @Column(name="user_id",nullable=false) public Long userId; @JsonIgnore @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="post_id",insertable=false,updatable=false) public Post post; @JsonIgnore @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="user_id",insertable=false,updatable=false) public User user; }
interface UserRepo extends JpaRepository<User,Long>{Optional<User> findByEmail(String email);}
interface StoreRepo extends JpaRepository<Store,Long>{List<Store> findByOwnerId(Long id);}
interface PostRepo extends JpaRepository<Post,Long>{List<Post> findAllByOrderByCreatedAtDesc();}
interface CommentRepo extends JpaRepository<Comment,Long>{List<Comment> findByPostIdOrderByCreatedAtAsc(Long id);void deleteByPostId(Long id);}
interface LikeRepo extends JpaRepository<PostLike,Long>{long countByPostId(Long id);Optional<PostLike> findByPostIdAndUserId(Long postId,Long userId);void deleteByPostId(Long id);}
record Signup(String email,String password,String nickname){} record Login(String email,String password){} record StoreInput(String name,String region,String introduction){} record PostInput(Long storeId,String content,String imageUrl){} record CommentInput(String content){}
@RestController @RequestMapping("/api")
class ApiController {
 private final UserRepo users; private final StoreRepo stores; private final PostRepo posts; private final CommentRepo comments; private final LikeRepo likes; private final PasswordEncoder encoder;
 ApiController(UserRepo u,StoreRepo s,PostRepo p,CommentRepo c,LikeRepo l,PasswordEncoder e){users=u;stores=s;posts=p;comments=c;likes=l;encoder=e;}
 private ResponseStatusException err(HttpStatus status,String message){return new ResponseStatusException(status,message);}
 private String required(String s,int max,String field){if(s==null||s.isBlank()||s.trim().length()>max)throw err(HttpStatus.BAD_REQUEST,field+"을 확인해 주세요.");return s.trim();}
 private String optional(String s,int max){if(s==null)return "";if(s.length()>max)throw err(HttpStatus.BAD_REQUEST,"입력 길이가 너무 깁니다.");return s.trim();}
 private User current(HttpSession session){Object id=session.getAttribute("userId");if(!(id instanceof Long))throw err(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");return users.findById((Long)id).orElseThrow(()->err(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다."));}
 private Store store(Long id){return stores.findById(id).orElseThrow(()->err(HttpStatus.NOT_FOUND,"가게를 찾을 수 없습니다."));}
 private Post post(Long id){return posts.findById(id).orElseThrow(()->err(HttpStatus.NOT_FOUND,"게시물을 찾을 수 없습니다."));}
 private void owner(Store s,User u){if(!s.ownerId.equals(u.id))throw err(HttpStatus.FORBIDDEN,"본인 가게만 변경할 수 있습니다.");}
 private Map<String,Object> userView(User u){return Map.of("id",u.id,"email",u.email,"nickname",u.nickname);}
 private Map<String,Object> postView(Post p,HttpSession session){Store s=store(p.storeId);Object uid=session.getAttribute("userId");Map<String,Object> v=new LinkedHashMap<>();v.put("id",p.id);v.put("storeId",s.id);v.put("storeName",s.name);v.put("region",s.region);v.put("content",p.content);v.put("imageUrl",p.imageUrl==null?"":p.imageUrl);v.put("createdAt",p.createdAt);v.put("likeCount",likes.countByPostId(p.id));v.put("liked",uid instanceof Long && likes.findByPostIdAndUserId(p.id,(Long)uid).isPresent());v.put("owner",uid instanceof Long && s.ownerId.equals(uid));v.put("commentCount",comments.findByPostIdOrderByCreatedAtAsc(p.id).size());return v;}
 @PostMapping("/signup") Map<String,Object> signup(@RequestBody Signup in,HttpSession session){String email=required(in.email(),255,"이메일").toLowerCase(Locale.ROOT);if(!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw err(HttpStatus.BAD_REQUEST,"이메일 형식을 확인해 주세요.");String password=required(in.password(),72,"비밀번호");if(password.length()<8)throw err(HttpStatus.BAD_REQUEST,"비밀번호는 8자 이상이어야 합니다.");if(users.findByEmail(email).isPresent())throw err(HttpStatus.CONFLICT,"이미 가입된 이메일입니다.");User u=new User();u.email=email;u.nickname=required(in.nickname(),50,"닉네임");u.passwordHash=encoder.encode(password);users.save(u);session.setAttribute("userId",u.id);return userView(u);}
 @PostMapping("/login") Map<String,Object> login(@RequestBody Login in,HttpSession session,HttpServletRequest request){User u=users.findByEmail(required(in.email(),255,"이메일").toLowerCase(Locale.ROOT)).orElseThrow(()->err(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호가 올바르지 않습니다."));if(in.password()==null||!encoder.matches(in.password(),u.passwordHash))throw err(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호가 올바르지 않습니다.");request.changeSessionId();session.setAttribute("userId",u.id);return userView(u);}
 @GetMapping("/me") Map<String,Object> me(HttpSession session){return userView(current(session));}
 @PostMapping("/logout") void logout(HttpSession session){session.invalidate();}
 @GetMapping("/stores") List<Store> stores(){return stores.findAll();}
 @GetMapping("/stores/{id}") Store getStore(@PathVariable Long id){return store(id);}
 @GetMapping("/stores/mine") List<Store> mine(HttpSession session){return stores.findByOwnerId(current(session).id);}
 @PostMapping("/stores") Store addStore(@RequestBody StoreInput in,HttpSession session){User u=current(session);Store s=new Store();s.ownerId=u.id;return saveStore(s,in);}
 @PutMapping("/stores/{id}") Store editStore(@PathVariable Long id,@RequestBody StoreInput in,HttpSession session){Store s=store(id);owner(s,current(session));return saveStore(s,in);}
 private Store saveStore(Store s,StoreInput in){s.name=required(in.name(),100,"가게명");s.region=required(in.region(),100,"지역");s.introduction=optional(in.introduction(),500);return stores.save(s);}
 @GetMapping("/posts") List<Map<String,Object>> list(@RequestParam(defaultValue="") String q,HttpSession session){String query=q.trim().toLowerCase(Locale.ROOT);return posts.findAllByOrderByCreatedAtDesc().stream().filter(p->{Store s=store(p.storeId);return (p.content+" "+s.name+" "+s.region).toLowerCase(Locale.ROOT).contains(query);}).map(p->postView(p,session)).toList();}
 @GetMapping("/posts/{id}") Map<String,Object> detail(@PathVariable Long id,HttpSession session){return postView(post(id),session);}
 @PostMapping("/posts") Map<String,Object> addPost(@RequestBody PostInput in,HttpSession session){User u=current(session);if(in.storeId()==null)throw err(HttpStatus.BAD_REQUEST,"가게를 선택해 주세요.");Store s=store(in.storeId());owner(s,u);Post p=new Post();p.storeId=s.id;return savePost(p,in,session);}
 @PutMapping("/posts/{id}") Map<String,Object> editPost(@PathVariable Long id,@RequestBody PostInput in,HttpSession session){Post p=post(id);owner(store(p.storeId),current(session));return savePost(p,in,session);}
 private Map<String,Object> savePost(Post p,PostInput in,HttpSession session){p.content=required(in.content(),4000,"게시물 내용");p.imageUrl=optional(in.imageUrl(),1000);if(!p.imageUrl.isEmpty()&&!p.imageUrl.matches("^https?://.+"))throw err(HttpStatus.BAD_REQUEST,"이미지는 http 또는 https URL이어야 합니다.");return postView(posts.save(p),session);}
 @Transactional @DeleteMapping("/posts/{id}") void deletePost(@PathVariable Long id,HttpSession session){Post p=post(id);owner(store(p.storeId),current(session));comments.deleteByPostId(id);likes.deleteByPostId(id);posts.delete(p);}
 @Transactional @PostMapping("/posts/{id}/like") Map<String,Object> toggle(@PathVariable Long id,HttpSession session){post(id);User u=current(session);Optional<PostLike> found=likes.findByPostIdAndUserId(id,u.id);if(found.isPresent())likes.delete(found.get());else{PostLike l=new PostLike();l.postId=id;l.userId=u.id;likes.save(l);}likes.flush();return postView(post(id),session);}
 @GetMapping("/posts/{id}/comments") List<Map<String,Object>> comments(@PathVariable Long id,HttpSession session){post(id);Object uid=session.getAttribute("userId");return comments.findByPostIdOrderByCreatedAtAsc(id).stream().map(c->{User u=users.findById(c.userId).orElseThrow();return Map.<String,Object>of("id",c.id,"nickname",u.nickname,"content",c.content,"createdAt",c.createdAt,"owner",c.userId.equals(uid));}).toList();}
 @PostMapping("/posts/{id}/comments") void addComment(@PathVariable Long id,@RequestBody CommentInput in,HttpSession session){post(id);User u=current(session);Comment c=new Comment();c.postId=id;c.userId=u.id;c.content=required(in.content(),500,"댓글");comments.save(c);}
 @DeleteMapping("/comments/{id}") void deleteComment(@PathVariable Long id,HttpSession session){Comment c=comments.findById(id).orElseThrow(()->err(HttpStatus.NOT_FOUND,"댓글을 찾을 수 없습니다."));if(!c.userId.equals(current(session).id))throw err(HttpStatus.FORBIDDEN,"본인 댓글만 삭제할 수 있습니다.");comments.delete(c);}
}



